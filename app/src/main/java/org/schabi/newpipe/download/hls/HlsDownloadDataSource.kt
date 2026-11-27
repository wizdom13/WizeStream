/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.download.hls

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import org.schabi.newpipe.DownloaderImpl
import org.schabi.newpipe.network.AppProxySelector
import us.shandian.giga.get.DownloadRequestHeaders

/** Request-local HTTP transport with the same proxy and credential boundaries as file downloads. */
class HlsDownloadDataSource private constructor(private val owner: Factory) : BaseDataSource(true) {
    @Volatile private var connection: HttpURLConnection? = null

    @Volatile private var input: InputStream? = null
    private var opened = false
    private var remaining = C.LENGTH_UNSET.toLong()
    private var currentUri: Uri? = null
    private var responseHeaders: Map<String, List<String>> = emptyMap()

    override fun open(dataSpec: DataSpec): Long {
        checkCancelled()
        if (dataSpec.httpMethod != DataSpec.HTTP_METHOD_GET) throw IOException("HLS downloads only use GET requests.")
        transferInitializing(dataSpec)
        owner.active.add(this)
        var url = HlsDownloadPreparation.validateUrl(dataSpec.uri.toString())
        var headers = DownloadRequestHeaders.afterRedirect(owner.headers + dataSpec.httpRequestHeaders, owner.origin, url)
        try {
            for (redirect in 0..MAX_REDIRECTS) {
                checkCancelled()
                val request = owner.openConnection(url)
                connection = request
                request.instanceFollowRedirects = false
                request.connectTimeout = TIMEOUT_MILLIS
                request.readTimeout = TIMEOUT_MILLIS
                request.requestMethod = "GET"
                request.setRequestProperty("User-Agent", DownloaderImpl.USER_AGENT)
                headers.forEach { (name, value) ->
                    if (!name.equals("Range", true) && !name.equals("Accept-Encoding", true)) request.setRequestProperty(name, value)
                }
                request.setRequestProperty("Accept-Encoding", "identity")
                if (dataSpec.position > 0 || dataSpec.length != C.LENGTH_UNSET.toLong()) {
                    val end = if (dataSpec.length == C.LENGTH_UNSET.toLong()) "" else Math.addExact(dataSpec.position, dataSpec.length - 1).toString()
                    request.setRequestProperty("Range", "bytes=${dataSpec.position}-$end")
                }
                val status = request.responseCode
                if (status in setOf(301, 302, 303, 307, 308)) {
                    if (redirect == MAX_REDIRECTS) throw IOException("Too many HLS redirects.")
                    val location = request.getHeaderField("Location") ?: throw IOException("HLS redirect has no destination.")
                    val next = HlsDownloadPreparation.validateUrl(URL(url, location).toString())
                    headers = DownloadRequestHeaders.afterRedirect(headers, url, next)
                    request.disconnect()
                    connection = null
                    url = next
                    continue
                }
                if (status !in 200..299) throw HlsHttpException(status)
                val rangeLength = if (status == HttpURLConnection.HTTP_PARTIAL) validateRange(request, dataSpec) else null
                currentUri = Uri.parse(url.toString())
                responseHeaders = request.headerFields.filterKeys { it != null }
                input = request.inputStream
                val skipped = if (status == HttpURLConnection.HTTP_OK) dataSpec.position else 0L
                skip(skipped)
                val contentLength = request.getHeaderField("Content-Length")?.toLongOrNull()?.takeIf { it >= skipped }
                remaining = when {
                    dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
                    rangeLength != null -> rangeLength
                    contentLength != null -> contentLength - skipped
                    else -> C.LENGTH_UNSET.toLong()
                }
                checkCancelled()
                opened = true
                transferStarted(dataSpec)
                return remaining
            }
            throw IOException("Too many HLS redirects.")
        } catch (error: Exception) {
            close()
            throw error
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        checkCancelled()
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val count = input?.read(buffer, offset, if (remaining < 0) length else minOf(length.toLong(), remaining).toInt())
            ?: throw IOException("The HLS connection is closed.")
        if (count == -1) {
            if (remaining > 0) throw EOFException("The HLS response ended before all bytes arrived.")
            return C.RESULT_END_OF_INPUT
        }
        if (remaining > 0) remaining -= count
        bytesTransferred(count)
        return count
    }

    override fun getUri(): Uri? = currentUri

    override fun getResponseHeaders(): Map<String, List<String>> = responseHeaders

    override fun close() {
        try {
            input?.close()
        } finally {
            input = null
            connection?.disconnect()
            connection = null
            currentUri = null
            responseHeaders = emptyMap()
            owner.active.remove(this)
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }

    private fun abort() {
        connection?.disconnect()
    }

    private fun skip(bytes: Long) {
        var left = bytes
        val buffer = ByteArray(8192)
        while (left > 0) {
            checkCancelled()
            val count = input?.read(buffer, 0, minOf(left, buffer.size.toLong()).toInt()) ?: -1
            if (count == -1) throw EOFException("The HLS byte range starts after the end of the resource.")
            left -= count
        }
    }

    private fun validateRange(request: HttpURLConnection, dataSpec: DataSpec): Long {
        val range = Regex("bytes (\\d+)-(\\d+)/(\\d+|\\*)").matchEntire(request.getHeaderField("Content-Range").orEmpty().trim())
        val start = range?.groupValues?.get(1)?.toLongOrNull()
        val end = range?.groupValues?.get(2)?.toLongOrNull()
        val total = range?.groupValues?.get(3)?.toLongOrNull()
        if (start == null || end == null || start != dataSpec.position || end < start || end == Long.MAX_VALUE || total != null && end >= total) {
            throw IOException("The HLS server returned an incorrect byte range.")
        }
        val span = end - start + 1
        val contentLength = request.getHeaderField("Content-Length")?.toLongOrNull()
        if (contentLength != null && contentLength != span) throw IOException("The HLS byte range does not match the response length.")
        if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
            if (total == null || end + 1 != total) throw IOException("The HLS server returned only part of the requested resource.")
        } else if (span < dataSpec.length) {
            throw IOException("The HLS server returned an incomplete byte range.")
        }
        return span
    }

    private fun checkCancelled() {
        if (owner.cancelled.get() || Thread.currentThread().isInterrupted) throw InterruptedIOException("The HLS download was cancelled.")
    }

    class Factory @JvmOverloads constructor(
        manifestUrl: String,
        headers: Map<String, String>,
        internal val openConnection: (URL) -> HttpURLConnection = AppProxySelector::openConnection
    ) : DataSource.Factory {
        internal val origin = HlsDownloadPreparation.validateUrl(manifestUrl)
        internal val headers = Collections.unmodifiableMap(
            HashMap(headers).also { values ->
                if (values.any { (name, value) -> !name.matches(Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+")) || value.any { it == '\r' || it == '\n' } }) {
                    throw IOException("The HLS request contains an invalid HTTP header.")
                }
            }
        )
        internal val cancelled = AtomicBoolean()
        internal val active = ConcurrentHashMap.newKeySet<HlsDownloadDataSource>()

        override fun createDataSource(): DataSource = HlsDownloadDataSource(this)

        fun cancel() {
            cancelled.set(true)
            active.forEach { it.abort() }
        }
    }

    companion object {
        private const val MAX_REDIRECTS = 10
        private const val TIMEOUT_MILLIS = 30_000
    }
}

class HlsHttpException(val responseCode: Int) : IOException(
    when (responseCode) {
        401, 403, 410 -> "The HLS download link is unavailable or expired. Open the video again and start a new download."
        else -> "The HLS server returned HTTP $responseCode."
    }
)
