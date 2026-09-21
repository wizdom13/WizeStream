/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util

import android.content.Context
import android.util.AtomicFile
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Locale
import okhttp3.Request
import org.schabi.newpipe.DownloaderImpl
import org.schabi.newpipe.R

object AiSListRepository {
    const val SOURCE_URL =
        "https://raw.githubusercontent.com/Override92/AiSList/main/AiSList/aislist_blocklist.txt"
    const val PROJECT_URL = "https://github.com/Override92/AiSList"
    const val LICENSE_NAME = "CC BY-NC 4.0"

    private const val CACHE_FILE_NAME = "aislist_blocklist.txt"
    private const val LAST_UPDATED_PREFERENCE = "aislist_last_updated"
    private const val MAX_DOWNLOAD_BYTES = 2 * 1024 * 1024
    private const val MINIMUM_VALID_ENTRIES = 1_000

    @Volatile
    private var cachedEntries: Set<String>? = null

    data class SyncStatus(
        val count: Int,
        val updatedAtMillis: Long
    )

    @JvmStatic
    fun isEnabled(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(context.getString(R.string.aislist_enabled_key), false)
    }

    @JvmStatic
    fun setEnabled(context: Context, enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putBoolean(context.getString(R.string.aislist_enabled_key), enabled)
        }
    }

    @JvmStatic
    fun entries(context: Context): Set<String> {
        cachedEntries?.let { return it }
        return synchronized(this) {
            cachedEntries ?: loadCachedEntries(context.applicationContext).also {
                cachedEntries = it
            }
        }
    }

    @JvmStatic
    fun status(context: Context): SyncStatus {
        if (!cacheFile(context.applicationContext).baseFile.exists()) {
            return SyncStatus(0, 0L)
        }
        val updatedAt = PreferenceManager.getDefaultSharedPreferences(context)
            .getLong(LAST_UPDATED_PREFERENCE, 0L)
        return SyncStatus(entries(context).size, updatedAt)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun sync(context: Context): SyncStatus {
        val request = Request.Builder()
            .url(SOURCE_URL)
            .header("Accept", "text/plain")
            .build()
        val bytes = DownloaderImpl.getInstance().client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("AiSList returned HTTP " + response.code)
            }
            val body = response.body ?: throw IOException("AiSList returned an empty response")
            readLimited(body.byteStream())
        }
        val text = String(bytes, StandardCharsets.UTF_8)
        val parsed = parse(text)
        if (parsed.size < MINIMUM_VALID_ENTRIES) {
            throw IOException(
                "AiSList response contained only " + parsed.size + " valid channel entries"
            )
        }

        writeCache(context.applicationContext, bytes)
        cachedEntries = parsed
        val updatedAt = System.currentTimeMillis()
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putLong(LAST_UPDATED_PREFERENCE, updatedAt)
        }
        return SyncStatus(parsed.size, updatedAt)
    }

    @JvmStatic
    fun isListed(
        entries: Set<String>,
        channelUrl: String?,
        channelName: String?
    ): Boolean {
        if (entries.isEmpty()) {
            return false
        }
        return channelCandidates(channelUrl, channelName).any(entries::contains)
    }

    internal fun parse(text: String): Set<String> {
        return text.lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("!") }
            .filter { it.startsWith("@") || it.startsWith("UC", ignoreCase = true) }
            .map { it.lowercase(Locale.ROOT) }
            .toCollection(linkedSetOf())
    }

    internal fun channelCandidates(channelUrl: String?, channelName: String?): Set<String> {
        val candidates = linkedSetOf<String>()

        fun addCandidate(raw: String?) {
            val candidate = raw
                ?.trim()
                ?.trimEnd('/')
                ?.takeIf { it.isNotEmpty() }
                ?: return
            if (candidate.startsWith("@") ||
                candidate.startsWith("UC", ignoreCase = true)
            ) {
                candidates += candidate.lowercase(Locale.ROOT)
            }
        }

        addCandidate(channelName)

        val cleanUrl = channelUrl
            ?.substringBefore('?')
            ?.substringBefore('#')
            ?.trimEnd('/')
            ?: return candidates
        addCandidate(cleanUrl)

        val segments = cleanUrl.split('/').filter(String::isNotEmpty)
        segments.forEachIndexed { index, segment ->
            if (segment.startsWith("@")) {
                addCandidate(segment)
            }
            if (index > 0 && segments[index - 1].equals("channel", ignoreCase = true)) {
                addCandidate(segment)
            }
        }
        return candidates
    }

    private fun loadCachedEntries(context: Context): Set<String> {
        val atomicFile = cacheFile(context)
        if (!atomicFile.baseFile.exists()) {
            return emptySet()
        }
        return try {
            atomicFile.openRead().bufferedReader(StandardCharsets.UTF_8).use { reader ->
                parse(reader.readText())
            }
        } catch (_: IOException) {
            emptySet()
        }
    }

    @Throws(IOException::class)
    private fun writeCache(context: Context, bytes: ByteArray) {
        val atomicFile = cacheFile(context)
        val stream = atomicFile.startWrite()
        try {
            stream.write(bytes)
            atomicFile.finishWrite(stream)
        } catch (error: IOException) {
            atomicFile.failWrite(stream)
            throw error
        }
    }

    private fun cacheFile(context: Context): AtomicFile {
        return AtomicFile(context.filesDir.resolve(CACHE_FILE_NAME))
    }

    @Throws(IOException::class)
    private fun readLimited(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) {
                break
            }
            total += read
            if (total > MAX_DOWNLOAD_BYTES) {
                throw IOException("AiSList response exceeded the maximum allowed size")
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}
