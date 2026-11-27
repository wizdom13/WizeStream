/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.download.hls

import android.app.Application
import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], application = Application::class)
class HlsDownloadDataSourceTest {
    @Test
    fun sameOriginSegmentKeepsHeadersSignedQueryAndExactByteRange() {
        val expected = "https://fixture.example/segment.ts?token=a%2Fb&part=1"
        val connection = Reply(URL(expected), 206, "2345", mapOf("Content-Range" to "bytes 2-5/8"))
        val factory = HlsDownloadDataSource.Factory(ORIGIN, HEADERS) { url ->
            assertEquals(expected, url.toString())
            connection
        }
        val source = factory.createDataSource()
        try {
            assertEquals(4L, source.open(DataSpec(Uri.parse(expected), 2, 4)))
            assertEquals("2345", readAll(source))
            assertEquals("bytes=2-5", connection.getRequestProperty("Range"))
            assertEquals("identity", connection.getRequestProperty("Accept-Encoding"))
            assertEquals("Bearer fixture", connection.getRequestProperty("Authorization"))
            assertEquals("session=fixture", connection.getRequestProperty("Cookie"))
            assertEquals(Uri.parse(expected), source.uri)
        } finally {
            source.close()
        }
        assertTrue(connection.disconnected)
    }

    @Test
    fun crossOriginChildDoesNotReceiveCredentialsButRetainsReferer() {
        val connection = Reply(URL("https://cdn.example/segment.ts"), 200, "data")
        val source = HlsDownloadDataSource.Factory(ORIGIN, HEADERS) { connection }.createDataSource()
        try {
            source.open(DataSpec(Uri.parse(connection.url.toString())))
            assertNull(connection.getRequestProperty("Authorization"))
            assertNull(connection.getRequestProperty("Cookie"))
            assertEquals("https://fixture.example/watch", connection.getRequestProperty("Referer"))
        } finally {
            source.close()
        }
    }

    @Test
    fun credentialsStayRemovedAfterRedirectReturnsToOriginalOrigin() {
        val first = Reply(URL(ORIGIN), 302, "", mapOf("Location" to "https://cdn.example/next"))
        val second = Reply(URL("https://cdn.example/next"), 302, "", mapOf("Location" to "https://fixture.example/final?sig=kept"))
        val third = Reply(URL("https://fixture.example/final?sig=kept"), 200, "data")
        val replies = ArrayDeque(listOf(first, second, third))
        val source = HlsDownloadDataSource.Factory(ORIGIN, HEADERS) { url -> replies.removeFirst().also { assertEquals(it.url, url) } }.createDataSource()
        try {
            source.open(DataSpec(Uri.parse(ORIGIN)))
            assertEquals("data", readAll(source))
            assertEquals("Bearer fixture", first.getRequestProperty("Authorization"))
            for (connection in listOf(second, third)) {
                assertNull(connection.getRequestProperty("Authorization"))
                assertNull(connection.getRequestProperty("Cookie"))
                assertEquals("https://fixture.example/watch", connection.getRequestProperty("Referer"))
            }
            assertEquals(Uri.parse(third.url.toString()), source.uri)
        } finally {
            source.close()
        }
    }

    @Test
    fun ignoredRangeStillReturnsOnlyRequestedBytes() {
        val connection = Reply(URL(ORIGIN), 200, "01234567")
        val source = HlsDownloadDataSource.Factory(ORIGIN, emptyMap()) { connection }.createDataSource()
        try {
            assertEquals(3L, source.open(DataSpec(Uri.parse(ORIGIN), 2, 3)))
            assertEquals("234", readAll(source))
        } finally {
            source.close()
        }
    }

    @Test
    fun truncatedResponsesAndIncorrectRangesDoNotBecomeCompleteCacheSpans() {
        val short = Reply(URL(ORIGIN), 206, "12", mapOf("Content-Range" to "bytes 0-4/5", "Content-Length" to "5"))
        val source = HlsDownloadDataSource.Factory(ORIGIN, emptyMap()) { short }.createDataSource()
        try {
            source.open(DataSpec(Uri.parse(ORIGIN), 0, 5))
            assertThrows(EOFException::class.java) { readAll(source) }
        } finally {
            source.close()
        }
        val wrong = HlsDownloadDataSource.Factory(ORIGIN, emptyMap()) { Reply(URL(ORIGIN), 206, "34", mapOf("Content-Range" to "bytes 3-4/8")) }.createDataSource()
        assertThrows(java.io.IOException::class.java) { wrong.open(DataSpec(Uri.parse(ORIGIN), 2, 2)) }
    }

    @Test
    fun unboundedRequestRejectsAPrefixResponseButAcceptsACompletePartialResponse() {
        for (range in listOf("bytes 0-1/8", "bytes 0-1/*")) {
            val source = HlsDownloadDataSource.Factory(ORIGIN, emptyMap()) { Reply(URL(ORIGIN), 206, "01", mapOf("Content-Range" to range)) }.createDataSource()
            assertThrows(java.io.IOException::class.java) { source.open(DataSpec(Uri.parse(ORIGIN))) }
        }
        val complete = HlsDownloadDataSource.Factory(ORIGIN, emptyMap()) { Reply(URL(ORIGIN), 206, "0123", mapOf("Content-Range" to "bytes 0-3/4")) }.createDataSource()
        try {
            assertEquals(4L, complete.open(DataSpec(Uri.parse(ORIGIN))))
            assertEquals("0123", readAll(complete))
        } finally {
            complete.close()
        }
    }

    @Test
    fun partialResponseLengthMustAgreeWithItsDeclaredRange() {
        val source = HlsDownloadDataSource.Factory(ORIGIN, emptyMap()) {
            Reply(URL(ORIGIN), 206, "01234", mapOf("Content-Range" to "bytes 0-3/8"))
        }.createDataSource()
        assertThrows(java.io.IOException::class.java) { source.open(DataSpec(Uri.parse(ORIGIN), 0, 4)) }
    }

    @Test
    fun cancelDisconnectsActiveRequestsAndPreventsFurtherReadsOrOpens() {
        val connection = Reply(URL(ORIGIN), 200, "fixture")
        val factory = HlsDownloadDataSource.Factory(ORIGIN, emptyMap()) { connection }
        val source = factory.createDataSource()
        try {
            source.open(DataSpec(Uri.parse(ORIGIN)))
            assertFalse(connection.disconnected)
            factory.cancel()
            assertTrue(connection.disconnected)
            assertThrows(InterruptedIOException::class.java) { source.read(ByteArray(1), 0, 1) }
            assertThrows(InterruptedIOException::class.java) { factory.createDataSource().open(DataSpec(Uri.parse(ORIGIN))) }
        } finally {
            source.close()
        }
    }

    private fun readAll(source: DataSource): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16)
        while (true) {
            val count = source.read(buffer, 0, buffer.size)
            if (count == -1) return output.toString("UTF-8")
            output.write(buffer, 0, count)
        }
    }

    private class Reply(url: URL, private val code: Int, body: String, headers: Map<String, String> = emptyMap()) : HttpURLConnection(url) {
        private val bytes = body.toByteArray()
        private val fields = mapOf("Content-Length" to bytes.size.toString()) + headers
        var disconnected = false

        override fun connect() = Unit
        override fun disconnect() {
            disconnected = true
        }
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = code
        override fun getInputStream() = ByteArrayInputStream(bytes)
        override fun getHeaderField(name: String): String? = fields[name]
        override fun getHeaderFields(): Map<String, List<String>> = fields.mapValues { listOf(it.value) }
    }

    companion object {
        private const val ORIGIN = "https://fixture.example/master.m3u8?token=master"
        private val HEADERS = mapOf("Authorization" to "Bearer fixture", "Cookie" to "session=fixture", "Referer" to "https://fixture.example/watch")
    }
}
