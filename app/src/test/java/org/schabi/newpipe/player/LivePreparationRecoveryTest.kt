package org.schabi.newpipe.player

import android.os.Looper
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player as Media3Player
import androidx.media3.exoplayer.ExoPlayer
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.SingleSubject
import java.io.IOException
import java.time.Duration
import java.util.Optional
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.player.mediasource.FullLiveDvrUnavailableException
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class LivePreparationRecoveryTest {
    @Test
    fun startupFailureWithoutMetadataHasBackoffAndAFiniteBudget() {
        val f = Fixture()
        repeat(3) { index ->
            assertTrue(f.recovery.recover(error()))
            val delay = listOf(1_000L, 3_000L, 8_000L)[index]
            advance(delay - 1)
            assertEquals(index, f.loads)
            advance(1)
            assertEquals(index + 1, f.loads)
        }
        assertTrue(f.recovery.recover(error()))
        assertEquals(listOf(LivePreparationRecovery.StopReason.EXHAUSTED), f.stops)
        repeat(20) { f.recovery.recover(error()) }
        advance(120_000)
        assertEquals(3, f.loads)
        assertEquals(1, f.stops.size)
        verify(f.player, times(3)).reloadPlayQueueManager()
        f.recovery.reset()
    }

    @Test
    fun networkFailuresDuringTheStatusCheckAreAlsoBounded() {
        val f = Fixture { Single.error(IOException("Offline")) }
        f.recovery.recover(error())
        advance(12_000)
        assertEquals(3, f.loads)
        assertEquals(listOf(LivePreparationRecovery.StopReason.EXHAUSTED), f.stops)
        verify(f.player, never()).reloadPlayQueueManager()
        f.recovery.reset()
    }

    @Test
    fun duplicateErrorsCannotCreateParallelRetries() {
        val f = Fixture()
        repeat(10) { f.recovery.recover(error()) }
        advance(1_000)
        assertEquals(1, f.loads)
        verify(f.player).reloadPlayQueueManager()
        f.recovery.reset()
    }

    @Test
    fun protectedDvrPreparationFailureDoesNotDependOnUiMetadata() {
        val f = Fixture()
        assertTrue(f.recovery.recover(error(FullLiveDvrUnavailableException("DASH missing"))))
        advance(1_000)
        assertEquals(1, f.loads)
        f.recovery.reset()
    }

    @Test
    fun explicitlyEndedBroadcastWithoutMediaStopsInsteadOfPreparingAgain() {
        val f = Fixture {
            Single.just(
                liveInfo().apply {
                    streamType = StreamType.POST_LIVE_STREAM
                    dashMpdUrl = ""
                    hlsUrl = ""
                }
            )
        }
        f.recovery.recover(error())
        advance(1_000)
        assertEquals(listOf(LivePreparationRecovery.StopReason.ENDED), f.stops)
        verify(f.player, never()).reloadPlayQueueManager()
        f.recovery.reset()
    }

    @Test
    fun availableArchiveClearsLiveRestrictionsBeforeReloading() {
        val f = Fixture {
            Single.just(
                liveInfo().apply {
                    streamType = StreamType.VIDEO_STREAM
                    videoStreams = listOf(mock(VideoStream::class.java))
                }
            )
        }
        f.recovery.recover(error())
        advance(1_000)
        assertEquals(1, f.archives)
        assertTrue(f.stops.isEmpty())
        verify(f.player).reloadPlayQueueManager()
        f.recovery.reset()
    }

    @Test
    fun manifestOnlyArchiveRetainsItsAdaptivePlaybackPath() {
        val f = Fixture { Single.just(liveInfo().apply { streamType = StreamType.POST_LIVE_STREAM }) }
        f.recovery.recover(error())
        advance(1_000)
        assertEquals(1, f.archives)
        assertTrue(f.stops.isEmpty())
        verify(f.player).reloadPlayQueueManager()
        f.recovery.reset()
    }

    @Test
    fun failingArchivePreparationRetainsTheOriginalRetryBudget() {
        val archive = liveInfo().apply { streamType = StreamType.POST_LIVE_STREAM }
        val f = Fixture { Single.just(archive) }
        repeat(3) { index ->
            assertTrue(f.recovery.recover(error()))
            advance(listOf(1_000L, 3_000L, 8_000L)[index])
            `when`(f.player.currentStreamInfo).thenReturn(Optional.of(archive))
        }
        assertTrue(f.recovery.recover(error()))
        assertEquals(3, f.loads)
        assertEquals(listOf(LivePreparationRecovery.StopReason.EXHAUSTED), f.stops)
        f.recovery.reset()
    }

    @Test
    fun manifestOnlyMisclassifiedLiveIsNotDeclaredEnded() {
        val f = Fixture { Single.just(liveInfo().apply { streamType = StreamType.VIDEO_STREAM }) }
        f.recovery.recover(error())
        advance(1_000)
        assertEquals(0, f.archives)
        assertTrue(f.stops.isEmpty())
        verify(f.player).reloadPlayQueueManager()
        f.recovery.reset()
    }

    @Test
    fun pauseAndQueueReplacementCancelPendingAndInFlightWork() {
        val subject = SingleSubject.create<StreamInfo>()
        val f = Fixture { subject }
        f.recovery.recover(error())
        advance(1_000)
        f.recovery.onPlayWhenReadyChanged(false)
        subject.onSuccess(liveInfo())
        advance(1_000)
        verify(f.player, never()).reloadPlayQueueManager()

        f.recovery.recover(error())
        `when`(f.queue.item).thenReturn(mock(PlayQueueItem::class.java))
        advance(10_000)
        assertEquals(1, f.loads)
        f.recovery.reset()
    }

    @Test
    fun manualRetryAndStablePlaybackCanStartANewBudget() {
        val f = Fixture()
        repeat(3) { index ->
            f.recovery.recover(error())
            advance(listOf(1_000L, 3_000L, 8_000L)[index])
        }
        f.recovery.onPlaybackStateChanged(Media3Player.STATE_READY)
        advance(30_000)
        f.recovery.recover(error())
        advance(1_000)
        assertEquals(4, f.loads)
        assertTrue(f.stops.isEmpty())
        repeat(2) { index ->
            f.recovery.recover(error())
            advance(listOf(3_000L, 8_000L)[index])
        }
        f.recovery.recover(error())
        assertEquals(1, f.stops.size)
        f.recovery.onPlayWhenReadyChanged(true)
        f.recovery.recover(error())
        advance(1_000)
        assertEquals(7, f.loads)
        f.recovery.reset()
    }

    @Test
    fun unrelatedVodAndDecoderErrorsKeepTheirOwnRecoveryPaths() {
        val item = mock(PlayQueueItem::class.java)
        `when`(item.serviceId).thenReturn(ServiceList.YouTube.serviceId)
        `when`(item.url).thenReturn(URL)
        `when`(item.streamType).thenReturn(StreamType.VIDEO_STREAM)
        assertFalse(LivePreparationRecovery.eligible(item, null, error()))
        `when`(item.streamType).thenReturn(StreamType.LIVE_STREAM)
        assertFalse(LivePreparationRecovery.eligible(item, null, PlaybackException("Decoder", null, PlaybackException.ERROR_CODE_DECODING_FAILED)))
    }

    private class Fixture(load: () -> Single<StreamInfo> = { Single.just(liveInfo()) }) {
        val player = mock(Player::class.java)
        val queue = mock(PlayQueue::class.java)
        val item = mock(PlayQueueItem::class.java)
        var loads = 0
        var archives = 0
        val stops = mutableListOf<LivePreparationRecovery.StopReason>()
        val recovery = LivePreparationRecovery(
            player,
            { archives++ },
            { _, reason -> stops.add(reason) },
            {
                loads++
                load()
            },
            Schedulers.trampoline()
        )

        init {
            `when`(player.playQueue).thenReturn(queue)
            `when`(queue.item).thenReturn(item)
            `when`(item.serviceId).thenReturn(ServiceList.YouTube.serviceId)
            `when`(item.url).thenReturn(URL)
            `when`(item.streamType).thenReturn(StreamType.LIVE_STREAM)
            `when`(player.currentStreamInfo).thenReturn(Optional.empty())
            `when`(player.playWhenReady).thenReturn(true)
            `when`(player.getExoPlayer()).thenReturn(mock(ExoPlayer::class.java))
        }
    }

    companion object {
        private const val URL = "https://www.youtube.com/watch?v=live"
        private fun liveInfo() = StreamInfo(ServiceList.YouTube.serviceId, "live", URL, "Live").apply {
            streamType = StreamType.LIVE_STREAM
            dashMpdUrl = "https://example.test/live.mpd"
            hlsUrl = "https://example.test/live.m3u8"
        }

        private fun error(cause: Throwable = IOException("Preparation failed")) = PlaybackException("Live source", cause, PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
        private fun advance(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
    }
}
