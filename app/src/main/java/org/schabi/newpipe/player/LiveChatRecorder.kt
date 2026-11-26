/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.player

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import androidx.preference.PreferenceManager
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfoItem
import org.schabi.newpipe.local.history.IncognitoMode
import org.schabi.newpipe.profiles.ProfileManager

/** User-initiated capture of only messages the live-chat extractor actually delivered. */
object LiveChatRecorder {
    private const val FOLDER = "recorded-live-chats"
    internal const val MAX_MESSAGES = 5_000
    internal const val MAX_DURATION_MS = 20L * 60L * 1_000L
    private const val MAX_FILES = 12
    private const val MAX_FILE_BYTES = 8L * 1024L * 1024L
    private const val MAX_JSON_CHARS = 12_000_000
    private val archiveLock = Any()

    data class Entry(val atMillis: Long, val author: String, val text: String)

    internal data class Key(val profileId: String, val serviceId: Int, val url: String)
    internal enum class Phase { STARTING, RECORDING, SAVING, PAUSED, LIMIT, READ_FAILED, SAVE_FAILED }
    internal enum class Failure { READ, SAVE, SOURCE }
    internal data class State(val key: Key, val phase: Phase, val failure: Failure? = null)
    internal data class Archive(
        val startedAt: Long,
        var capturedMillis: Long = 0,
        val rows: MutableList<Entry> = mutableListOf()
    )

    /** IO is separate from the short ownership lock used by recording controls. */
    internal open class ArchiveStore {
        open suspend fun load(context: Context, key: Key): Archive? = readArchive(context, key)

        open suspend fun save(context: Context, key: Key, archive: Archive, allowed: () -> Boolean): Boolean = writeArchive(context, key, archive, allowed)
    }

    internal class Controller(
        private val scope: CoroutineScope,
        private val messages: (Int, String) -> Flow<Result<List<BulletCommentsInfoItem>>> = LiveChatRepository::messages,
        private val wallTime: () -> Long = System::currentTimeMillis,
        private val elapsedTime: () -> Long = SystemClock::elapsedRealtime,
        private val store: ArchiveStore = ArchiveStore()
    ) {
        private class Attempt(val context: Context, val preferences: SharedPreferences, val key: Key) {
            lateinit var job: Job
            lateinit var listener: SharedPreferences.OnSharedPreferenceChangeListener

            @Volatile var discarded = false

            @Volatile var stopped = false

            @Volatile var stoppedAt: Long? = null
            var startedAt: Long? = null
            var archive: Archive? = null
            var limitReached = false
            var failure: Failure? = null
            var finished = false
            var saving = false
        }

        private val lock = Any()
        private val forgottenProfiles = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
        private var active: Attempt? = null
        private val mutableState = MutableStateFlow<State?>(null)
        val state = mutableState.asStateFlow()

        fun stateFor(context: Context, serviceId: Int, url: String): State? {
            if (IncognitoMode.isEnabled(context)) return null
            val key = Key(ProfileManager.getActiveProfileId(context), serviceId, url)
            return state.value?.takeIf { it.key == key }
        }

        fun isRecording(serviceId: Int, url: String): Boolean = state.value?.let {
            it.key.serviceId == serviceId && it.key.url == url &&
                (it.phase == Phase.STARTING || it.phase == Phase.RECORDING)
        } == true

        fun start(context: Context, serviceId: Int, url: String): Boolean {
            if (url.isBlank() || IncognitoMode.isEnabled(context)) return false
            val app = context.applicationContext
            val attempt = Attempt(
                app,
                PreferenceManager.getDefaultSharedPreferences(app),
                Key(ProfileManager.getActiveProfileId(app), serviceId, url)
            )
            attempt.listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if ((key == null || key == IncognitoMode.KEY || key == ProfileManager.ACTIVE_PROFILE_ID_PREFERENCE_KEY) &&
                    !allowed(attempt)
                ) {
                    discard(attempt)
                }
            }
            attempt.job = scope.launch(start = CoroutineStart.LAZY) { capture(attempt) }
            attempt.preferences.registerOnSharedPreferenceChangeListener(attempt.listener)
            synchronized(lock) {
                if (active != null || !allowed(attempt)) {
                    attempt.preferences.unregisterOnSharedPreferenceChangeListener(attempt.listener)
                    attempt.job.cancel()
                    return false
                }
                active = attempt
                mutableState.value = State(attempt.key, Phase.STARTING)
            }
            // Completion handlers also run when Stop cancels a lazy job before its first dispatch.
            attempt.job.invokeOnCompletion { captureFinished(attempt) }
            attempt.job.start()
            return true
        }

        fun stop(serviceId: Int, url: String) {
            val attempt = synchronized(lock) {
                active?.takeIf { it.key.serviceId == serviceId && it.key.url == url && !it.finished && !it.stopped }?.also {
                    it.stopped = true
                    it.stoppedAt = elapsedTime()
                    mutableState.value = State(it.key, Phase.SAVING)
                }
            }
            attempt?.job?.cancel()
        }

        fun retrySaving(context: Context, serviceId: Int, url: String): Boolean {
            val key = Key(ProfileManager.getActiveProfileId(context), serviceId, url)
            val attempt = synchronized(lock) {
                active?.takeIf { it.key == key && state.value?.phase == Phase.SAVE_FAILED && allowed(it) }
            } ?: return false
            return save(attempt)
        }

        private fun allowed(attempt: Attempt): Boolean = !attempt.discarded && attempt.key.profileId !in forgottenProfiles &&
            !IncognitoMode.isEnabled(attempt.preferences) &&
            attempt.preferences.getString(
                ProfileManager.ACTIVE_PROFILE_ID_PREFERENCE_KEY,
                ProfileManager.DEFAULT_PROFILE_ID
            ) == attempt.key.profileId

        private suspend fun capture(attempt: Attempt) {
            try {
                if (!allowed(attempt)) return
                val archive = store.load(attempt.context, attempt.key) ?: Archive(wallTime())
                attempt.archive = archive
                if (!allowed(attempt) || attempt.stopped) return
                val remaining = MAX_DURATION_MS - archive.capturedMillis
                if (remaining <= 0 || archive.rows.size >= MAX_MESSAGES) {
                    attempt.limitReached = true
                    return
                }
                val seen = archive.rows.toMutableSet()
                synchronized(lock) {
                    if (attempt.stopped || !allowed(attempt)) return
                    attempt.startedAt = elapsedTime()
                    mutableState.value = State(attempt.key, Phase.RECORDING)
                }
                val completed = withTimeoutOrNull(remaining) {
                    messages(attempt.key.serviceId, attempt.key.url).collect { result ->
                        if (!allowed(attempt) || attempt.stopped) throw CancellationException()
                        // A failure value is recoverable: the shared repository retries upstream.
                        result.getOrNull()?.forEach { item ->
                            if (attempt.stopped || !allowed(attempt)) throw CancellationException()
                            val text = item.commentText.orEmpty().trim().take(2_000)
                            if (text.isEmpty()) return@forEach
                            val offset = item.duration?.toMillis()?.takeIf { it > 0 }
                                ?: (wallTime() - archive.startedAt).coerceAtLeast(0)
                            val row = Entry(offset, item.name.orEmpty().take(160), text)
                            if (seen.add(row)) archive.rows += row
                            if (archive.rows.size >= MAX_MESSAGES) {
                                attempt.limitReached = true
                                throw CancellationException()
                            }
                        }
                    }
                    true
                }
                if (completed == null) attempt.limitReached = true
            } catch (_: CancellationException) {
                // The completion handler owns saving, including cancellation before this body runs.
            } catch (_: Exception) {
                attempt.failure = if (attempt.archive == null) Failure.READ else Failure.SOURCE
            }
        }

        private fun captureFinished(attempt: Attempt) {
            synchronized(lock) {
                if (active !== attempt || attempt.finished) return
                attempt.finished = true
                attempt.startedAt?.let { started ->
                    attempt.archive?.let { archive ->
                        val captured = ((attempt.stoppedAt ?: elapsedTime()) - started).coerceAtLeast(0)
                        archive.capturedMillis = (archive.capturedMillis + captured).coerceAtMost(MAX_DURATION_MS)
                        attempt.limitReached = attempt.limitReached || archive.capturedMillis >= MAX_DURATION_MS
                    }
                }
            }
            save(attempt)
        }

        private fun save(attempt: Attempt): Boolean {
            synchronized(lock) {
                if (active !== attempt || attempt.saving) return false
                attempt.saving = true
                mutableState.value = State(attempt.key, Phase.SAVING)
            }
            scope.launch {
                try {
                    val archive = attempt.archive
                    val saved = allowed(attempt) && (archive == null || store.save(attempt.context, attempt.key, archive) { allowed(attempt) })
                    val phase = when {
                        attempt.failure == Failure.READ -> Phase.READ_FAILED
                        attempt.limitReached -> Phase.LIMIT
                        else -> Phase.PAUSED
                    }
                    release(attempt, if (saved && allowed(attempt)) State(attempt.key, phase, attempt.failure) else null)
                } catch (_: Exception) {
                    synchronized(lock) {
                        if (active === attempt) {
                            attempt.saving = false
                            if (allowed(attempt)) {
                                // Keep the bounded rows and the owner for an explicit save retry.
                                mutableState.value = State(attempt.key, Phase.SAVE_FAILED, Failure.SAVE)
                            } else {
                                release(attempt, null)
                            }
                        }
                    }
                }
            }
            return true
        }

        private fun release(attempt: Attempt, state: State?) {
            synchronized(lock) {
                if (active !== attempt) return
                active = null
                mutableState.value = state
                attempt.preferences.unregisterOnSharedPreferenceChangeListener(attempt.listener)
            }
        }

        private fun discard(attempt: Attempt) {
            val completed = synchronized(lock) {
                if (active !== attempt) return
                attempt.discarded = true
                attempt.finished
            }
            attempt.job.cancel()
            if (completed) save(attempt)
        }

        fun forgetProfile(context: Context, profileId: String) {
            // Metadata removal follows archive deletion. Reject starts in that intervening window.
            forgottenProfiles += profileId
            val attempt = synchronized(lock) { active?.takeIf { it.key.profileId == profileId } }
            if (attempt != null) discard(attempt)
            synchronized(archiveLock) { directory(context, profileId).deleteRecursively() }
            synchronized(lock) {
                if (active == null && state.value?.key?.profileId == profileId) mutableState.value = null
            }
        }
    }

    internal val controller = Controller(CoroutineScope(SupervisorJob() + Dispatchers.IO))

    @JvmStatic
    fun isRecording(serviceId: Int, url: String): Boolean = controller.isRecording(serviceId, url)

    @JvmStatic
    fun start(context: Context, serviceId: Int, url: String): Boolean = controller.start(context, serviceId, url)

    @JvmStatic
    fun stop(serviceId: Int, url: String) = controller.stop(serviceId, url)

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun directory(context: Context, profileId: String): File = File(File(context.filesDir, FOLDER), digest(profileId))

    private fun file(context: Context, key: Key): File = File(directory(context, key.profileId), digest("${key.serviceId}:${key.url}") + ".json.gz")

    private fun readArchive(context: Context, key: Key): Archive? = synchronized(archiveLock) {
        val file = file(context, key)
        if (!file.exists()) return null
        if (!file.isFile || file.length() > MAX_FILE_BYTES) throw IOException("Invalid live chat archive")
        val json = GZIPInputStream(file.inputStream()).bufferedReader(Charsets.UTF_8).use { reader ->
            val text = StringBuilder()
            val buffer = CharArray(8_192)
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                if (text.length + count > MAX_JSON_CHARS) throw IOException("Live chat archive is too large")
                text.append(buffer, 0, count)
            }
            JSONObject(text.toString())
        }
        val startedAt = json.getLong("startedAt")
        // Older files saved only once. Their final write time approximates that capture's budget.
        val captured = if (json.has("capturedMillis")) json.getLong("capturedMillis") else file.lastModified() - startedAt
        val rows = json.getJSONArray("rows")
        if (rows.length() > MAX_MESSAGES) throw IOException("Too many live chat messages")
        Archive(
            startedAt,
            captured.coerceIn(0, MAX_DURATION_MS),
            (0 until rows.length()).map { index ->
                val item = rows.getJSONObject(index)
                Entry(item.getLong("at"), item.getString("author"), item.getString("text"))
            }.toMutableList()
        )
    }

    private fun writeArchive(context: Context, key: Key, archive: Archive, allowed: () -> Boolean): Boolean {
        if (archive.rows.isEmpty() && archive.capturedMillis == 0L) return allowed()
        val dest = file(context, key)
        val temp = synchronized(archiveLock) {
            if (!allowed()) return false
            val parent = requireNotNull(dest.parentFile)
            if (!parent.isDirectory && !parent.mkdirs()) throw IOException("Cannot create live chat directory")
            File.createTempFile(dest.name, ".tmp", parent)
        }
        try {
            val json = JSONObject()
                .put("startedAt", archive.startedAt)
                .put("capturedMillis", archive.capturedMillis)
                .put(
                    "rows",
                    JSONArray().apply {
                        archive.rows.forEach { row ->
                            put(JSONObject().put("at", row.atMillis).put("author", row.author).put("text", row.text))
                        }
                    }
                ).toString()
            if (json.length > MAX_JSON_CHARS) throw IOException("Live chat archive is too large")
            FileOutputStream(temp).use { output ->
                GZIPOutputStream(output).use { gzip ->
                    gzip.write(json.toByteArray(Charsets.UTF_8))
                    gzip.finish()
                    output.fd.sync()
                }
            }
            if (temp.length() > MAX_FILE_BYTES) throw IOException("Live chat archive is too large")
            return synchronized(archiveLock) {
                // Profile deletion uses this same lock, and marks its attempt discarded first.
                if (!allowed()) return false
                // Android's same-directory rename atomically replaces the old file. Never copy over it.
                if (!temp.renameTo(dest)) throw IOException("Cannot save live chat archive")
                dest.parentFile?.listFiles { f -> f.name.endsWith(".json.gz") }.orEmpty()
                    .sortedByDescending(File::lastModified).drop(MAX_FILES).forEach(File::delete)
                true
            }
        } finally {
            temp.delete()
        }
    }

    @JvmStatic
    fun hasSaved(context: Context, serviceId: Int, url: String): Boolean {
        if (IncognitoMode.isEnabled(context)) return false
        return file(context, Key(ProfileManager.getActiveProfileId(context), serviceId, url)).isFile
    }

    @JvmStatic
    fun read(context: Context, serviceId: Int, url: String): List<Entry> {
        if (IncognitoMode.isEnabled(context)) return emptyList()
        val key = Key(ProfileManager.getActiveProfileId(context), serviceId, url)
        val rows = runCatching { readArchive(context, key)?.rows.orEmpty() }.getOrDefault(emptyList())
        return if (!IncognitoMode.isEnabled(context) && ProfileManager.getActiveProfileId(context) == key.profileId) rows else emptyList()
    }

    internal fun replayRows(rows: List<Entry>, atMillis: Long): List<Entry> = rows.filter { it.atMillis <= atMillis }.takeLast(250)

    @JvmStatic
    fun forgetProfile(context: Context, profileId: String) = controller.forgetProfile(context, profileId)
}
