package org.schabi.newpipe.player

import android.net.Uri
import android.os.Looper
import androidx.media3.common.Player as Media3Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.SingleSubject
import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.helper.YoutubeDashLiveManifestParser
import org.schabi.newpipe.player.mediaitem.StreamInfoTag
import org.schabi.newpipe.player.mediasource.LoadedMediaSource
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class LiveManifestRefresherTest {
    @Test
    fun compatibleRefreshReplacesOnlyTheCurrentSourcesManifestUri() {
        val result = SingleSubject.create<LiveManifestCandidate>()
        val f = Fixture { result }
        assertTrue(f.refresh())
        verify(f.dash, never()).replaceManifestUri(any(Uri::class.java))
        result.onSuccess(LiveManifestCandidate(info(), manifest()))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(true), f.results)
        verify(f.dash).replaceManifestUri(Uri.parse(MPD))
        verify(f.engine, never()).stop()
        verify(f.player, never()).reloadPlayQueueManager()
    }

    @Test
    fun changedTimelineOrFailedPreflightUsesTheBoundedRebuildPath() {
        val incompatible = Fixture { Single.just(LiveManifestCandidate(info(), manifest(start = 60))) }
        incompatible.refresh()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(false), incompatible.results)
        verify(incompatible.dash, never()).replaceManifestUri(any(Uri::class.java))
        val failed = Fixture { Single.error(IOException("Rejected")) }
        failed.refresh()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(false), failed.results)
    }

    @Test
    fun canceledOrStaleRequestsCannotChangePlayback() {
        listOf(false, true).forEach { changeItem ->
            val result = SingleSubject.create<LiveManifestCandidate>()
            val f = Fixture { result }
            f.refresh()
            if (changeItem) {
                `when`(f.queue.item).thenReturn(mock(PlayQueueItem::class.java))
            } else {
                f.refresher.cancel()
            }
            result.onSuccess(LiveManifestCandidate(info(), manifest()))
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(f.results.isEmpty())
            verify(f.dash, never()).replaceManifestUri(any(Uri::class.java))
        }
    }

    @Test
    fun refreshRequiresMatchingTracksPositionAndRetainedDvrHistory() {
        val old = manifest()
        val formats = listOf(old.getPeriod(0).adaptationSets[0].representations[0].format)
        fun accepts(start: Int = 0, codec: String = "avc1.42c00a", id: String = "0", protected: Boolean = true, position: Long = 70_000_000): Boolean = LiveManifestRefresher.compatible(old, manifest(start, codec, id), formats, position, protected, 0)
        assertTrue(accepts())
        assertTrue(accepts(start = 2))
        assertFalse(accepts(start = 60))
        assertTrue(accepts(start = 60, protected = false))
        assertFalse(accepts(start = 60, protected = false, position = 1_000_000))
        assertFalse(accepts(codec = "vp09.00.10.08"))
        assertFalse(accepts(id = "different"))
        assertFalse(accepts(position = 500_000_000))
    }

    @Test
    fun directRefreshRejectsOtherHostsAndEmbeddedCredentials() {
        assertTrue(LiveManifestRefresher.safeManifestUrl(MPD))
        assertTrue(LiveManifestRefresher.safeManifestUrl("https://manifest.googlevideo.com/live.mpd"))
        listOf("http://youtube.com/live.mpd", "https://youtube.com.evil.test/live.mpd", "https://private.test/live.mpd", "https://secret@youtube.com/live.mpd", "$MPD#fragment").forEach {
            assertFalse(LiveManifestRefresher.safeManifestUrl(it))
        }
    }

    private class Fixture(load: (PlayQueueItem) -> Single<LiveManifestCandidate>) {
        val player = mock(Player::class.java)
        val engine = mock(ExoPlayer::class.java)
        val queue = mock(PlayQueue::class.java)
        val item = mock(PlayQueueItem::class.java)
        val dash = mock(DashMediaSource::class.java)
        val results = mutableListOf<Boolean>()
        val refresher = LiveManifestRefresher(player, { true }, load, Schedulers.trampoline())

        init {
            val previous = manifest()
            val timeline = mock(Timeline::class.java)
            val window = Timeline.Window().apply { manifest = previous }
            `when`(timeline.isEmpty).thenReturn(false)
            `when`(timeline.getWindow(eq(0), any(Timeline.Window::class.java))).thenReturn(window)
            `when`(engine.currentTimeline).thenReturn(timeline)
            `when`(engine.videoFormat).thenReturn(previous.getPeriod(0).adaptationSets[0].representations[0].format)
            `when`(engine.currentPosition).thenReturn(70_000)
            `when`(engine.playbackState).thenReturn(Media3Player.STATE_READY)
            `when`(player.getExoPlayer()).thenReturn(engine)
            `when`(player.playQueue).thenReturn(queue)
            `when`(queue.item).thenReturn(item)
            `when`(player.playWhenReady).thenReturn(true)
            `when`(item.url).thenReturn(URL)
            `when`(item.serviceId).thenReturn(ServiceList.YouTube.serviceId)
            val loaded = LoadedMediaSource(dash, StreamInfoTag.of(info()), item, Long.MAX_VALUE)
            `when`(engine.currentMediaItem).thenReturn(loaded.mediaItem)
        }

        fun refresh() = refresher.refresh(engine, item) { results.add(it) }
    }

    companion object {
        private const val URL = "https://www.youtube.com/watch?v=live"
        private const val MPD = "https://manifest.googlevideo.com/live.mpd?token=private"
        private fun info() = StreamInfo(ServiceList.YouTube.serviceId, "live", URL, "Live").apply {
            streamType = StreamType.LIVE_STREAM
            dashMpdUrl = MPD
        }

        private fun manifest(start: Int = 0, codec: String = "avc1.42c00a", id: String = "0") = YoutubeDashLiveManifestParser().parse(
            Uri.parse(MPD),
            ByteArrayInputStream(
                """
                <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="dynamic"
                  availabilityStartTime="1970-01-01T00:00:00Z" minimumUpdatePeriod="PT1S" minBufferTime="PT2S">
                  <Period id="0" start="PT0S"><AdaptationSet id="0" contentType="video">
                    <Representation id="$id" mimeType="video/mp4" codecs="$codec" bandwidth="2000" width="64" height="64">
                      <SegmentTemplate timescale="1" initialization="init.m4s" media="chunk-${'$'}Number${'$'}.m4s" startNumber="1">
                        <SegmentTimeline><S t="$start" d="2" r="59"/></SegmentTimeline>
                      </SegmentTemplate>
                    </Representation>
                  </AdaptationSet></Period>
                </MPD>
                """.trimIndent().toByteArray()
            )
        )
    }
}
