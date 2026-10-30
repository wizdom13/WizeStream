/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.recovery

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import androidx.preference.PreferenceManager
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.R
import org.schabi.newpipe.local.history.IncognitoMode
import org.schabi.newpipe.profiles.ProfileManager

/** Two bounded snapshots per profile, outside backups and synchronization. Access on the IO worker. */
internal class QueueRecoveryStore(private val file: File) {
    fun load(now: Long = System.currentTimeMillis()): List<QueueSnapshot> = try {
        val atomic = AtomicFile(file)
        val data = atomic.openRead().use { input ->
            val bytes = input.readBytesLimited(MAX_BYTES)
            JSONObject(String(bytes, Charsets.UTF_8))
        }
        require(data.getInt("version") == 1)
        val snapshots = data.getJSONArray("queues")
        require(snapshots.length() <= 2)
        val valid = (0 until snapshots.length()).map { QueueSnapshot.parse(snapshots.getJSONObject(it)) }
            .filter { it.savedAt <= now && now - it.savedAt <= MAX_AGE }
        if (valid.size != snapshots.length()) {
            if (valid.isEmpty()) clear() else write(valid)
        }
        valid
    } catch (_: Exception) {
        clear()
        emptyList()
    }

    fun save(snapshot: QueueSnapshot) {
        val old = load(snapshot.savedAt)
        val previous = old.firstOrNull { it.session != snapshot.session }
        write(listOfNotNull(snapshot, previous))
    }

    private fun write(queues: List<QueueSnapshot>) {
        val data = JSONObject().put("version", 1).put("queues", JSONArray().also { list -> queues.forEach { list.put(it.json()) } })
            .toString().toByteArray(Charsets.UTF_8)
        if (data.size > MAX_BYTES) return
        file.parentFile?.mkdirs()
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try {
            output.write(data)
            atomic.finishWrite(output)
        } catch (error: Exception) {
            atomic.failWrite(output)
            throw error
        }
    }

    fun clear() = AtomicFile(file).delete()

    private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            require(output.size() + count <= limit)
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    companion object {
        const val MAX_BYTES = 2 * 1024 * 1024
        const val MAX_AGE = 7L * 24 * 60 * 60 * 1000
    }
}

object QueueRecovery {
    const val RESTORE_EXTRA = "restore_saved_queue"
    internal val generation = java.util.concurrent.atomic.AtomicLong()
    const val ENABLED_KEY = "queue_recovery_enabled"
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    @JvmStatic
    fun initialize(context: Context) {
        if (listener != null) return
        val app = context.applicationContext
        val prefs = PreferenceManager.getDefaultSharedPreferences(app)
        listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if ((key == ENABLED_KEY || key == app.getString(R.string.enable_watch_history_key)) && !enabled(app)) {
                generation.incrementAndGet()
                io.execute { directory(app).deleteRecursively() }
            }
        }.also(prefs::registerOnSharedPreferenceChangeListener)
        io.execute {
            if (!enabled(app)) {
                directory(app).deleteRecursively()
            } else {
                // Access removes expired/corrupt snapshots even for profiles not opened today.
                directory(app).listFiles()?.filter { it.extension == "json" }?.forEach {
                    QueueRecoveryStore(it).load()
                }
            }
        }
    }

    fun enabled(context: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getBoolean(ENABLED_KEY, true) && prefs.getBoolean(context.getString(R.string.enable_watch_history_key), true)
    }

    internal fun save(context: Context, profile: String, snapshot: QueueSnapshot, eligible: java.util.concurrent.atomic.AtomicBoolean, epoch: Long) {
        io.execute {
            if (generation.get() == epoch && eligible.get() && enabled(context) && !IncognitoMode.isEnabled(context) && ProfileManager.getProfile(context, profile) != null) {
                runCatching { store(context, profile).save(snapshot) }
            }
        }
    }

    internal fun load(context: Context, profile: String, callback: (List<QueueSnapshot>) -> Unit) {
        io.execute {
            val snapshots = if (enabled(context) && !IncognitoMode.isEnabled(context)) store(context, profile).load() else emptyList()
            main.post { callback(snapshots) }
        }
    }

    @JvmStatic
    fun clear(context: Context, profile: String) {
        generation.incrementAndGet()
        io.execute { runCatching { store(context, profile).clear() } }
    }

    private fun directory(context: Context) = File(context.noBackupFilesDir, "queue-recovery")
    private fun store(context: Context, profile: String) = QueueRecoveryStore(File(directory(context), "${UUID.fromString(profile)}.json"))
}
