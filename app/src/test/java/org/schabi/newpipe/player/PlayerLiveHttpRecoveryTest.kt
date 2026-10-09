package org.schabi.newpipe.player

import android.os.Looper
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player as Media3Player
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import java.io.IOException
import java.time.Duration
import java.util.Optional
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doAnswer
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
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.player.resolver.VideoPlaybackResolver

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class PlayerLiveHttpRecoveryTest {
    @Test
    fun exhaustedRefreshRetriesSwitchToHlsAtLivePosition() {
        val fixture = Fixture()
        val method = PlayerErrorController::class.java.getDeclaredMethod(
            "tryRecoverFromYouTubeMediaUrlFailure",
            PlaybackException::class.java
        ).apply { isAccessible = true }

        repeat(3) {
            assertTrue(method.invoke(fixture.controller, httpError(403)) as Boolean)
        }
        verify(fixture.resolver, never()).preferHlsForLiveStream(anyString())

        assertTrue(method.invoke(fixture.controller, httpError(403)) as Boolean)

        verify(fixture.resolver).preferHlsForLiveStream(fixture.info.url)
        verify(fixture.queue).unsetRecovery(0)
        verify(fixture.player).reloadPlayQueueManager()
        verify(fixture.player, never()).changeState(Player.STATE_PAUSED)
        verify(fixture.queue, never()).error()
        fixture.controller.resetRecovery()
    }

    @Test
    fun fallbackRunsOnlyOnceEvenIfDashIsSelectedAgain() {
        val fixture = Fixture()

        assertTrue(fixture.recover())
        fixture.hlsPreferred = false
        assertFalse(fixture.recover())

        verify(fixture.player, times(1)).reloadPlayQueueManager()
    }

    @Test
    fun alreadySelectedHlsIsNotRestartedByDashFallback() {
        val fixture = Fixture()
        fixture.hlsPreferred = true

        assertFalse(fixture.recover())
        verify(fixture.player, never()).reloadPlayQueueManager()
    }

    @Test
    fun otherHttpStatusesDoNotUse403Fallback() {
        val fixture = Fixture()

        assertFalse(fixture.recover(404))
        assertFalse(fixture.recover(410))
        assertFalse(fixture.recover(500))
        assertTrue(fixture.recover())
    }

    @Test
    fun missingAlternativeManifestDoesNotUseFallback() {
        val fixture = Fixture()
        fixture.info.hlsUrl = ""

        assertFalse(fixture.recover())
        fixture.info.hlsUrl = "https://example.com/live.m3u8"
        fixture.info.dashMpdUrl = ""
        assertFalse(fixture.recover())
        verify(fixture.player, never()).reloadPlayQueueManager()
    }

    @Test
    fun directVideoStreamsDoNotUseLiveManifestFallback() {
        val fixture = Fixture()
        fixture.info.streamType = StreamType.VIDEO_STREAM
        fixture.info.videoStreams = listOf(mock(VideoStream::class.java))

        assertFalse(fixture.recover())
        verify(fixture.player, never()).reloadPlayQueueManager()
    }

    @Test
    fun otherServicesAndAudioPlaybackDoNotUseFallback() {
        val fixture = Fixture()
        `when`(fixture.item.serviceId).thenReturn(ServiceList.SoundCloud.serviceId)
        assertFalse(fixture.recover())

        `when`(fixture.item.serviceId).thenReturn(ServiceList.YouTube.serviceId)
        // Background presentation can be audio-only while still using the video resolver.
        `when`(fixture.player.isAudioOnly).thenReturn(true)
        `when`(fixture.player.videoPlayerSelected()).thenReturn(false)
        assertFalse(fixture.recover())
        verify(fixture.player, never()).reloadPlayQueueManager()
    }

    @Test
    fun backgroundLiveStallFallsBackAfterTenSecondsOnlyOnce() {
        val fixture = Fixture()
        fixture.state(Media3Player.STATE_READY)
        `when`(fixture.player.isAudioOnly).thenReturn(true)
        fixture.state(Media3Player.STATE_BUFFERING)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(9_999))
        verify(fixture.player, never()).reloadPlayQueueManager()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1))
        verify(fixture.resolver).preferHlsForLiveStream(fixture.info.url)
        verify(fixture.queue).unsetRecovery(0)
        verify(fixture.player).reloadPlayQueueManager()

        fixture.hlsPreferred = false
        fixture.state(Media3Player.STATE_READY)
        fixture.state(Media3Player.STATE_BUFFERING)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        verify(fixture.player, times(1)).reloadPlayQueueManager()
    }

    @Test
    fun streamThatStartsInBackgroundCanRecoverFromALaterStall() {
        val fixture = Fixture()
        `when`(fixture.player.isAudioOnly).thenReturn(true)
        fixture.state(Media3Player.STATE_READY)
        fixture.state(Media3Player.STATE_BUFFERING)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        verify(fixture.player).reloadPlayQueueManager()
    }

    @Test
    fun pauseCancelsStallAndResumeRearmsAlreadyBufferingPlayer() {
        val fixture = Fixture()
        fixture.state(Media3Player.STATE_READY)
        fixture.state(Media3Player.STATE_BUFFERING)
        `when`(fixture.player.playWhenReady).thenReturn(false)
        fixture.controller.onPlayWhenReadyChanged(false)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        verify(fixture.player, never()).reloadPlayQueueManager()

        `when`(fixture.player.playWhenReady).thenReturn(true)
        fixture.controller.onPlayWhenReadyChanged(true)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        verify(fixture.player).reloadPlayQueueManager()
    }

    @Test
    fun initialBufferingAndShortStallsDoNotDiscardDash() {
        val fixture = Fixture()
        fixture.state(Media3Player.STATE_BUFFERING)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20))
        fixture.state(Media3Player.STATE_READY)
        fixture.state(Media3Player.STATE_BUFFERING)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(9))
        fixture.state(Media3Player.STATE_READY)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        verify(fixture.player, never()).reloadPlayQueueManager()
    }

    @Test
    fun staleRecoveryCannotRestartAnotherQueueItemOrResetPlayer() {
        val fixture = Fixture()
        fixture.state(Media3Player.STATE_READY)
        fixture.state(Media3Player.STATE_BUFFERING)
        `when`(fixture.item.url).thenReturn("https://www.youtube.com/watch?v=other")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        verify(fixture.player, never()).reloadPlayQueueManager()

        `when`(fixture.item.url).thenReturn(fixture.info.url)
        fixture.state(Media3Player.STATE_READY)
        fixture.state(Media3Player.STATE_BUFFERING)
        fixture.controller.resetRecovery()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        verify(fixture.player, never()).reloadPlayQueueManager()
    }

    @Test
    fun releasedEngineCannotRunPendingRecovery() {
        val fixture = Fixture()
        fixture.state(Media3Player.STATE_READY)
        fixture.state(Media3Player.STATE_BUFFERING)
        `when`(fixture.player.exoPlayerIsNull()).thenReturn(true)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        verify(fixture.player, never()).reloadPlayQueueManager()
    }

    private class Fixture {
        val player = mock(Player::class.java)
        val engine = mock(ExoPlayer::class.java)
        val queue = mock(PlayQueue::class.java)
        val item = mock(PlayQueueItem::class.java)
        val resolver = mock(VideoPlaybackResolver::class.java)
        val info = StreamInfo(
            ServiceList.YouTube.serviceId,
            "live",
            "https://www.youtube.com/watch?v=f0JQ1XrlAoQ",
            "Live stream"
        ).apply {
            streamType = StreamType.LIVE_STREAM
            dashMpdUrl = "https://example.com/live.mpd"
            hlsUrl = "https://example.com/live.m3u8"
        }
        var hlsPreferred = false
        val controller = PlayerErrorController(
            player,
            mock(PlayerEventDispatcher::class.java),
            resolver
        )

        init {
            `when`(player.exoPlayer).thenReturn(engine)
            `when`(player.playWhenReady).thenReturn(true)
            `when`(player.playQueue).thenReturn(queue)
            `when`(queue.item).thenReturn(item)
            `when`(item.serviceId).thenReturn(ServiceList.YouTube.serviceId)
            `when`(item.url).thenReturn(info.url)
            `when`(player.currentStreamInfo).thenReturn(Optional.of(info))
            `when`(player.videoPlayerSelected()).thenReturn(true)
            `when`(resolver.isHlsPreferredForLiveStream(info.url)).thenAnswer { hlsPreferred }
            doAnswer {
                hlsPreferred = true
                null
            }.`when`(resolver).preferHlsForLiveStream(info.url)
        }

        fun state(state: Int) {
            `when`(engine.playbackState).thenReturn(state)
            controller.onPlaybackStateChanged(state)
        }

        fun recover(status: Int = 403): Boolean = controller.tryRecoverFromLiveDashHttpFailure(httpError(status), item)
    }

    companion object {
        private fun httpError(status: Int): PlaybackException = PlaybackException(
            "Source error",
            HttpDataSource.InvalidResponseCodeException(
                status,
                "HTTP error",
                IOException("Rejected segment"),
                emptyMap(),
                DataSpec.Builder().setUri("https://example.com/segment").build(),
                byteArrayOf()
            ),
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
        )
    }
}
