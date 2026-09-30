package org.schabi.newpipe.player.resolver

import android.net.Uri
import android.util.Base64
import androidx.media3.common.C
import androidx.media3.common.Player as Media3Player
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.IOException
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.helper.PlayerDataSource
import org.schabi.newpipe.player.helper.YoutubeDashLiveManifestParser

class LiveDvrPlaybackTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun finiteDynamicDashSnapshotRefreshesAndKeepsRewoundPosition() {
        val fixture = Fixture()
        withPlayer(fixture) { player ->
            awaitPlayer(player) { it.playbackState == Media3Player.STATE_READY }
            assertEquals(0, fixture.hlsLoads.get())
            instrumentation.runOnMainSync {
                assertTrue(player.isCurrentMediaItemDynamic)
                assertTrue(player.isCurrentMediaItemSeekable)
                assertEquals(16000L, player.duration)
                player.seekTo(0)
            }
            awaitPlayer(player) { it.playbackState == Media3Player.STATE_READY && it.currentPosition == 0L }

            // The live broadcast grows beyond the initial snapshot's declared end.
            fixture.segmentCount.set(16)
            awaitPlayer(player) { it.duration == 32000L }
            instrumentation.runOnMainSync {
                assertEquals(0L, player.currentPosition)
                player.seekTo(20000)
            }
            // Newly published segments remain playable beyond the initial snapshot's end.
            awaitPlayer(player) { it.playbackState == Media3Player.STATE_READY && it.currentPosition == 20000L }
            instrumentation.runOnMainSync {
                player.seekTo(6000)
            }
            awaitPlayer(player) { it.playbackState == Media3Player.STATE_READY && it.currentPosition == 6000L }
            instrumentation.runOnMainSync { player.seekToDefaultPosition() }
            awaitPlayer(player) { it.playbackState == Media3Player.STATE_READY && it.currentPosition > 6000 }
            instrumentation.runOnMainSync { assertTrue(player.isCurrentMediaItemDynamic) }
            assertTrue(fixture.dashLoads.get() >= 2)
            assertEquals(0, fixture.hlsLoads.get())
        }
    }

    @Test
    fun staticDashSnapshotFallsBackToHlsInsteadOfEndingTheLiveBroadcast() {
        val fixture = Fixture(dynamic = false)
        withPlayer(fixture) { player ->
            awaitPlayer(player) { it.playbackState == Media3Player.STATE_READY }
            assertTrue(fixture.hlsLoads.get() > 0)
            instrumentation.runOnMainSync {
                assertTrue(player.isCurrentMediaItemDynamic)
                player.seekTo(0)
            }
            awaitPlayer(player) { it.playbackState == Media3Player.STATE_READY && it.currentPosition == 0L }
        }
    }

    @Test
    fun malformedDashFallsBackBeforePlaybackStarts() {
        val fixture = Fixture(broken = true)
        withPlayer(fixture) { player ->
            awaitPlayer(player) { it.playbackState == Media3Player.STATE_READY }
            assertTrue(fixture.hlsLoads.get() > 0)
        }
    }

    @Test
    fun dynamicSnapshotUsesTheCurrentMedia3ParserHook() {
        val manifest = YoutubeDashLiveManifestParser().parse(
            Uri.parse(DASH_URL),
            ByteArrayInputStream(Fixture().manifest())
        )
        assertTrue(manifest.dynamic)
        assertEquals(C.TIME_UNSET, manifest.durationMs)
        assertEquals(0L, manifest.availabilityStartTimeMs)
        assertEquals(1000L, manifest.minUpdatePeriodMs)
    }

    @Test
    fun staticArchiveKeepsItsDuration() {
        val fixture = Fixture(dynamic = false)
        val manifest = YoutubeDashLiveManifestParser().parse(
            Uri.parse(DASH_URL),
            ByteArrayInputStream(fixture.manifest())
        )
        assertFalse(manifest.dynamic)
        assertEquals(16000L, manifest.durationMs)
    }

    private fun withPlayer(fixture: Fixture, test: (ExoPlayer) -> Unit) {
        lateinit var player: ExoPlayer
        instrumentation.runOnMainSync {
            player = ExoPlayer.Builder(context).build().apply {
                setMediaSource(fixture.source())
                prepare()
            }
        }
        try {
            test(player)
        } finally {
            instrumentation.runOnMainSync { player.release() }
        }
    }

    private fun awaitPlayer(player: ExoPlayer, condition: (ExoPlayer) -> Boolean) {
        val reached = CountDownLatch(1)
        val listener = object : Media3Player.Listener {
            override fun onEvents(ignored: Media3Player, events: Media3Player.Events) {
                if (condition(player) || player.playerError != null) reached.countDown()
            }
        }
        try {
            instrumentation.runOnMainSync {
                player.addListener(listener)
                if (condition(player) || player.playerError != null) reached.countDown()
            }
            assertTrue("Playback condition timed out", reached.await(20, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                assertEquals(null, player.playerError)
                assertTrue(condition(player))
            }
        } finally {
            instrumentation.runOnMainSync { player.removeListener(listener) }
        }
    }

    private inner class Fixture(private val dynamic: Boolean = true, private val broken: Boolean = false) {
        val segmentCount = AtomicInteger(8)
        val dashLoads = AtomicInteger()
        val hlsLoads = AtomicInteger()

        // Generated 32-second, 64x64 blue video: ffmpeg color source, libx264, 5 fps,
        // keyframes every 10 frames, DASH segments of 2 seconds. No external media or network.
        private val files = buildMap {
            val encoded = instrumentation.context.assets.open("live-playback/dvr-segments.zip.base64").use { it.readBytes() }
            ZipInputStream(ByteArrayInputStream(Base64.decode(encoded, Base64.DEFAULT))).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    put(entry.name, zip.readBytes())
                }
            }
        }

        fun manifest(): ByteArray {
            val count = segmentCount.get()
            val liveAttributes = if (dynamic) {
                "availabilityStartTime=\"1970-01-01T00:00:00Z\" minimumUpdatePeriod=\"PT1S\""
            } else {
                ""
            }
            return """
                <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="${if (dynamic) "dynamic" else "static"}"
                  $liveAttributes mediaPresentationDuration="PT${count * 2}S" minBufferTime="PT2S">
                  <UTCTiming schemeIdUri="urn:mpeg:dash:utc:direct:2014" value="${Instant.now()}"/>
                  <Period id="0" start="PT0S"><AdaptationSet contentType="video" segmentAlignment="true">
                    <Representation id="0" mimeType="video/mp4" codecs="avc1.42c00a" bandwidth="2000" width="64" height="64">
                      <SegmentTemplate timescale="10240" initialization="init-stream${'$'}RepresentationID${'$'}.m4s"
                        media="chunk-stream${'$'}RepresentationID${'$'}-${'$'}Number%05d${'$'}.m4s" startNumber="1">
                        <SegmentTimeline><S t="0" d="20480" r="${count - 1}"/></SegmentTimeline>
                      </SegmentTemplate>
                    </Representation>
                  </AdaptationSet></Period>
                </MPD>
            """.trimIndent().toByteArray()
        }

        fun source(): MediaSource {
            val factory = DataSource.Factory {
                object : DataSource {
                    private var delegate: ByteArrayDataSource? = null
                    override fun addTransferListener(transferListener: TransferListener) = Unit
                    override fun getUri(): Uri? = delegate?.uri
                    override fun close() {
                        delegate?.close()
                    }
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = requireNotNull(delegate).read(buffer, offset, length)
                    override fun open(dataSpec: DataSpec): Long {
                        val name = dataSpec.uri.lastPathSegment
                        val bytes = when (name) {
                            "live.mpd" -> {
                                dashLoads.incrementAndGet()
                                if (broken) "not an MPD".toByteArray() else manifest()
                            }

                            "live.m3u8" -> {
                                hlsLoads.incrementAndGet()
                                buildString {
                                    append("#EXTM3U\n#EXT-X-VERSION:7\n#EXT-X-TARGETDURATION:2\n#EXT-X-MEDIA-SEQUENCE:1\n")
                                    append("#EXT-X-MAP:URI=\"init-stream0.m4s\"\n")
                                    repeat(16) { append("#EXTINF:2,\nchunk-stream0-${(it + 1).toString().padStart(5, '0')}.m4s\n") }
                                }.toByteArray()
                            }

                            else -> files[name] ?: throw IOException("Unknown fixture: $name")
                        }
                        delegate = ByteArrayDataSource(bytes)
                        return requireNotNull(delegate).open(dataSpec)
                    }
                }
            }
            val dataSource = object : PlayerDataSource(context, DefaultBandwidthMeter.Builder(context).build()) {
                override fun getLiveYoutubeDashMediaSourceFactory(): DashMediaSource.Factory = DashMediaSource.Factory(factory).setManifestParser(YoutubeDashLiveManifestParser())
                override fun getLiveHlsMediaSourceFactory(): HlsMediaSource.Factory = HlsMediaSource.Factory(factory)
            }
            val info = StreamInfo(ServiceList.YouTube.serviceId, "live", "https://example.test/watch", "Live fixture")
            info.streamType = StreamType.LIVE_STREAM
            info.dashMpdUrl = DASH_URL
            info.hlsUrl = "https://example.test/live.m3u8"
            val source = PlaybackResolver.maybeBuildLiveMediaSource(dataSource, info)
            assertNotNull(source)
            return requireNotNull(source)
        }
    }

    companion object {
        private const val DASH_URL = "https://example.test/live.mpd"
    }
}
