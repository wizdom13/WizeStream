/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.download.hls

import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Base64
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.hls.offline.HlsDownloader
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipInputStream
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real platform muxing/readback with generated color/tone media; no external service is used. */
class HlsMp4ExporterTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test(timeout = 60_000)
    fun muxedTransportStreamVodExportsCompleteAvcAndAacTracksOffline() {
        Fixture().use { fixture ->
            val plan = fixture.download("ts/index.m3u8")
            assertTrue(plan.durationUs in 2_900_000L..3_100_000L)
            assertEquals(fixture.headers, plan.requestHeaders)

            HlsMp4Exporter.export(context, plan, fixture.output)

            assertValidMp4(fixture.output)
            assertEquals(0, fixture.network.attempts.get())
            assertEquals(
                setOf("segment-00.ts", "segment-01.ts", "segment-02.ts"),
                fixture.loaded.filter { it.endsWith(".ts") }.map { it.substringAfterLast('/') }.toSet()
            )
            fixture.assertCacheReleased()
        }
    }

    @Test(timeout = 60_000)
    fun fragmentedMp4WithSeparateAudioExportsBothTracksOffline() {
        Fixture().use { fixture ->
            val plan = fixture.download("fmp4/index.m3u8")
            assertEquals(64, plan.height)
            assertTrue(plan.audioPlaylistUrl?.contains("audio.m3u8") == true)

            HlsMp4Exporter.export(context, plan, fixture.output)

            assertValidMp4(fixture.output)
            assertTrue(fixture.loaded.any { it.endsWith("video-init.mp4") })
            assertTrue(fixture.loaded.any { it.endsWith("audio-init.mp4") })
            assertTrue(fixture.loaded.any { it.endsWith("video-02.m4s") })
            assertTrue(fixture.loaded.any { it.endsWith("audio-02.m4s") })
            assertEquals(0, fixture.network.attempts.get())
            fixture.assertCacheReleased()
        }
    }

    @Test(timeout = 60_000)
    fun silentAvcVodPreservesOneVideoTrackWithoutCreatingAudio() {
        Fixture().use { fixture ->
            val plan = fixture.download("fmp4/video.m3u8")

            HlsMp4Exporter.export(context, plan, fixture.output)

            assertValidMp4(fixture.output, hasAudio = false)
            assertTrue(fixture.loaded.any { it.endsWith("video-init.mp4") })
            assertTrue(fixture.loaded.any { it.endsWith("video-02.m4s") })
            assertFalse(fixture.loaded.any { it.contains("audio") })
            assertEquals(0, fixture.network.attempts.get())
            fixture.assertCacheReleased()
        }
    }

    @Test(timeout = 60_000)
    fun missingCachedSegmentFailsWithoutNetworkOrAnIncompletePublishedFile() {
        Fixture().use { fixture ->
            val plan = fixture.download("ts/index.m3u8")
            val cache = HlsDownloadCache.open(context, fixture.cacheId)
            try {
                val key = cache.keys.single { it.contains("/segment-01.ts?") }
                val spans = cache.getCachedSpans(key).toList()
                assertTrue(spans.isNotEmpty())
                spans.forEach(cache::removeSpan)
            } finally {
                cache.release()
            }

            assertThrows(IOException::class.java) {
                HlsMp4Exporter.export(context, plan, fixture.output)
            }

            assertFalse(fixture.output.exists())
            assertEquals(0, fixture.network.attempts.get())
            fixture.assertCacheReleased()
        }
    }

    @Test(timeout = 60_000)
    fun cancelledExportLeavesTheCachedInputReusableAndPublishesNoFile() {
        Fixture().use { fixture ->
            val plan = fixture.download("fmp4/index.m3u8")
            var bytesWrittenBeforeCancellation = 0L
            assertThrows(InterruptedIOException::class.java) {
                HlsMp4Exporter.export(context, plan, fixture.output, isCancelled = {
                    // The initial cancellation check must pass. Cancel only once the real
                    // export has produced bytes, including on very fast test devices.
                    bytesWrittenBeforeCancellation = fixture.output.length()
                    bytesWrittenBeforeCancellation > 0L
                })
            }
            assertTrue("Cancellation happened before any MP4 was written", bytesWrittenBeforeCancellation > 0L)
            assertFalse(fixture.output.exists())
            fixture.assertCacheReleased()

            HlsMp4Exporter.export(context, plan, fixture.output)

            assertValidMp4(fixture.output)
            assertEquals(0, fixture.network.attempts.get())
            fixture.assertCacheReleased()
        }
    }

    private fun assertValidMp4(file: File, hasAudio: Boolean = true) {
        assertTrue("Exported MP4 is empty", file.length() > 1024)
        val extractor = MediaExtractor()
        val formats = try {
            extractor.setDataSource(file.path)
            assertEquals(if (hasAudio) 2 else 1, extractor.trackCount)
            (0 until extractor.trackCount).map(extractor::getTrackFormat)
        } finally {
            extractor.release()
        }
        val expectedMimeTypes = if (hasAudio) setOf("video/avc", "audio/mp4a-latm") else setOf("video/avc")
        assertEquals(expectedMimeTypes, formats.map { it.getString(MediaFormat.KEY_MIME) }.toSet())
        val video = formats.single { it.getString(MediaFormat.KEY_MIME) == "video/avc" }
        assertEquals(64, video.getInteger(MediaFormat.KEY_WIDTH))
        assertEquals(64, video.getInteger(MediaFormat.KEY_HEIGHT))
        if (hasAudio) {
            val audio = formats.single { it.getString(MediaFormat.KEY_MIME) == "audio/mp4a-latm" }
            assertEquals(48_000, audio.getInteger(MediaFormat.KEY_SAMPLE_RATE))
            assertEquals(1, audio.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
        }
        formats.forEachIndexed { index, format ->
            assertTrue("Unexpected track duration: $format", format.getLong(MediaFormat.KEY_DURATION) in 2_800_000L..3_300_000L)
            val reader = MediaExtractor()
            try {
                reader.setDataSource(file.path)
                reader.selectTrack(index)
                val buffer = ByteBuffer.allocate(256 * 1024)
                var count = 0
                var previousTime = -1L
                while (true) {
                    buffer.clear()
                    val length = reader.readSampleData(buffer, 0)
                    if (length < 0) break
                    assertTrue("Empty media sample", length > 0)
                    assertTrue("Track timestamps went backwards", reader.sampleTime >= previousTime)
                    if (count == 0) assertTrue("Track did not start near zero", reader.sampleTime in 0L..100_000L)
                    previousTime = reader.sampleTime
                    count++
                    if (!reader.advance()) break
                }
                val minimumSamples = if (format.getString(MediaFormat.KEY_MIME) == "video/avc") 25 else 100
                assertTrue("Incomplete media track: $count samples", count >= minimumSamples)
                assertTrue("Final samples are missing", previousTime in 2_700_000L..3_300_000L)
            } finally {
                reader.release()
            }
        }
    }

    private inner class Fixture : Closeable {
        val network = NetworkGuard()
        val cacheId = UUID.randomUUID().toString()
        val output = File(context.cacheDir, "hls-export-$cacheId.mp4")
        val headers = mapOf("Referer" to "https://example.com/watch", "Origin" to "https://example.com")
        val loaded = CopyOnWriteArrayList<String>()
        private val files = buildMap {
            val encoded = instrumentation.context.assets.open("hls-downloads/avc-aac.zip.base64").use { it.readBytes() }
            ZipInputStream(ByteArrayInputStream(Base64.decode(encoded, Base64.DEFAULT))).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    put(entry.name, zip.readBytes())
                }
            }
        }
        private val upstream = DataSource.Factory {
            ByteArrayDataSource { uri ->
                assertEquals(SIGNED_QUERY, uri.encodedQuery)
                val path = requireNotNull(uri.path).removePrefix("/")
                loaded += path
                val bytes = files[path] ?: throw IOException("Unknown generated HLS fixture: $path")
                if (path.endsWith(".m3u8")) signedPlaylist(bytes) else bytes
            }
        }

        fun download(path: String): HlsDownloadPlan {
            val url = "http://127.0.0.1:${network.port}/$path?$SIGNED_QUERY"
            val cache = HlsDownloadCache.open(context, cacheId)
            try {
                val plan = HlsDownloadPreparation.prepare(cache, cacheId, url, headers, upstreamFactory = upstream)
                HlsDownloader.Factory(HlsDownloadCache.networkFactory(cache, upstream))
                    .create(plan.toMediaItem()).download { _, _, _ -> }
                assertEquals(0, network.attempts.get())
                return plan
            } finally {
                cache.release()
            }
        }

        fun assertCacheReleased() {
            // A fresh instance cannot open a cache still locked by the exporter.
            HlsDownloadCache.open(context, cacheId).release()
        }

        override fun close() {
            try {
                HlsDownloadCache.remove(context, cacheId)
                output.delete()
            } finally {
                network.close()
            }
        }

        private fun signedPlaylist(bytes: ByteArray): ByteArray = bytes.toString(Charsets.UTF_8)
            .lineSequence().joinToString("\n") { line ->
                when {
                    line.startsWith('#') -> URI_ATTRIBUTE.replace(line) { match ->
                        "URI=\"${match.groupValues[1]}?$SIGNED_QUERY\""
                    }

                    line.isNotBlank() -> "$line?$SIGNED_QUERY"

                    else -> line
                }
            }.toByteArray()
    }

    /** Makes an unintended fallback observable without permitting any external connection. */
    private class NetworkGuard : Closeable {
        private val server = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
        val port get() = server.localPort
        val attempts = AtomicInteger()
        private val worker = thread(isDaemon = true, name = "hls-offline-network-guard") {
            while (!server.isClosed) {
                try {
                    server.accept().use { socket ->
                        attempts.incrementAndGet()
                        socket.getOutputStream().write(
                            "HTTP/1.1 503 Service Unavailable\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()
                        )
                    }
                } catch (error: SocketException) {
                    if (!server.isClosed) throw error
                }
            }
        }

        override fun close() {
            server.close()
            worker.join(1000)
        }
    }

    companion object {
        private const val SIGNED_QUERY = "token=a%2Fb&expires=123"
        private val URI_ATTRIBUTE = Regex("URI=\"([^\"]+)\"")
    }
}
