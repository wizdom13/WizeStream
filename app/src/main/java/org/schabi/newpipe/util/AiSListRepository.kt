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
    const val BLOCKLIST_SOURCE_URL =
        "https://raw.githubusercontent.com/Override92/AiSList/main/AiSList/aislist_blocklist.txt"
    const val WARNLIST_SOURCE_URL =
        "https://raw.githubusercontent.com/Override92/AiSList/main/AiSList/aislist_warnlist.txt"
    const val SOURCE_URL = BLOCKLIST_SOURCE_URL
    const val PROJECT_URL = "https://github.com/Override92/AiSList"
    const val LICENSE_NAME = "CC BY-NC 4.0"

    private const val BLOCKLIST_CACHE_FILE_NAME = "aislist_blocklist.txt"
    private const val WARNLIST_CACHE_FILE_NAME = "aislist_warnlist.txt"
    private const val LAST_UPDATED_PREFERENCE = "aislist_last_updated"
    private const val MAX_DOWNLOAD_BYTES = 2 * 1024 * 1024
    private const val MINIMUM_VALID_BLOCK_ENTRIES = 1_000
    private const val MINIMUM_VALID_WARN_ENTRIES = 100

    @Volatile
    private var cachedBlockEntries: Set<String>? = null

    @Volatile
    private var cachedWarnEntries: Set<String>? = null

    data class SyncStatus(
        val blockCount: Int,
        val warnCount: Int,
        val updatedAtMillis: Long
    ) {
        val count: Int
            get() = blockCount + warnCount
    }

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
    fun entries(context: Context): Set<String> = blockEntries(context)

    @JvmStatic
    fun blockEntries(context: Context): Set<String> {
        cachedBlockEntries?.let { return it }
        return synchronized(this) {
            cachedBlockEntries ?: loadCachedEntries(
                context.applicationContext,
                BLOCKLIST_CACHE_FILE_NAME
            ).also {
                cachedBlockEntries = it
            }
        }
    }

    @JvmStatic
    fun warnEntries(context: Context): Set<String> {
        cachedWarnEntries?.let { return it }
        return synchronized(this) {
            cachedWarnEntries ?: loadCachedEntries(
                context.applicationContext,
                WARNLIST_CACHE_FILE_NAME
            ).also {
                cachedWarnEntries = it
            }
        }
    }

    @JvmStatic
    fun status(context: Context): SyncStatus {
        val appContext = context.applicationContext
        val hasBlocklist = cacheFile(appContext, BLOCKLIST_CACHE_FILE_NAME).baseFile.exists()
        val hasWarnlist = cacheFile(appContext, WARNLIST_CACHE_FILE_NAME).baseFile.exists()
        if (!hasBlocklist && !hasWarnlist) {
            return SyncStatus(0, 0, 0L)
        }
        val updatedAt = PreferenceManager.getDefaultSharedPreferences(context)
            .getLong(LAST_UPDATED_PREFERENCE, 0L)
        return SyncStatus(
            blockCount = if (hasBlocklist) blockEntries(context).size else 0,
            warnCount = if (hasWarnlist) warnEntries(context).size else 0,
            updatedAtMillis = updatedAt
        )
    }

    @JvmStatic
    @Throws(IOException::class)
    fun sync(context: Context): SyncStatus {
        val blockBytes = fetch(BLOCKLIST_SOURCE_URL)
        val warnBytes = fetch(WARNLIST_SOURCE_URL)
        val blockEntries = parse(String(blockBytes, StandardCharsets.UTF_8))
        val warnEntries = parse(String(warnBytes, StandardCharsets.UTF_8))

        if (blockEntries.size < MINIMUM_VALID_BLOCK_ENTRIES) {
            throw IOException(
                "AiSList blocklist contained only " +
                    blockEntries.size +
                    " valid channel entries"
            )
        }
        if (warnEntries.size < MINIMUM_VALID_WARN_ENTRIES) {
            throw IOException(
                "AiSList warnlist contained only " +
                    warnEntries.size +
                    " valid channel entries"
            )
        }

        val appContext = context.applicationContext
        writeCache(appContext, BLOCKLIST_CACHE_FILE_NAME, blockBytes)
        writeCache(appContext, WARNLIST_CACHE_FILE_NAME, warnBytes)
        cachedBlockEntries = blockEntries
        cachedWarnEntries = warnEntries

        val updatedAt = System.currentTimeMillis()
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putLong(LAST_UPDATED_PREFERENCE, updatedAt)
        }
        return SyncStatus(blockEntries.size, warnEntries.size, updatedAt)
    }

    @JvmStatic
    fun isBlockListed(
        context: Context,
        channelUrl: String?,
        channelName: String?
    ): Boolean = isListed(blockEntries(context), channelUrl, channelName)

    @JvmStatic
    fun isWarnListed(
        context: Context,
        channelUrl: String?,
        channelName: String?
    ): Boolean = isListed(warnEntries(context), channelUrl, channelName)

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
            .map(::normalizeListEntry)
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
                candidates += normalizeListEntry(candidate)
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

    private fun normalizeListEntry(value: String): String {
        return if (value.startsWith("@")) {
            value.lowercase(Locale.ROOT)
        } else {
            value
        }
    }

    @Throws(IOException::class)
    private fun fetch(url: String): ByteArray {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/plain")
            .build()
        return DownloaderImpl.getInstance().client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("AiSList returned HTTP " + response.code + " for " + url)
            }
            val body = response.body ?: throw IOException("AiSList returned an empty response")
            readLimited(body.byteStream())
        }
    }

    private fun loadCachedEntries(context: Context, fileName: String): Set<String> {
        val atomicFile = cacheFile(context, fileName)
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
    private fun writeCache(context: Context, fileName: String, bytes: ByteArray) {
        val atomicFile = cacheFile(context, fileName)
        val stream = atomicFile.startWrite()
        try {
            stream.write(bytes)
            atomicFile.finishWrite(stream)
        } catch (error: IOException) {
            atomicFile.failWrite(stream)
            throw error
        }
    }

    private fun cacheFile(context: Context, fileName: String): AtomicFile {
        return AtomicFile(context.filesDir.resolve(fileName))
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
