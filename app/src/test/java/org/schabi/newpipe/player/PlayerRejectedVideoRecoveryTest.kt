package org.schabi.newpipe.player

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import java.io.IOException
import java.util.Collections
import java.util.Optional
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.player.resolver.VideoPlaybackResolver

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class PlayerRejectedVideoRecoveryTest {
    @Test
    fun visionOsRejectionTriesAnotherFormatOnlyOnceAndKeepsPosition() {
        val fixture = Fixture()
        assertTrue(fixture.recover())
        assertFalse(fixture.recover())
        verify(fixture.resolver).rejectVideoStreamForCurrentItem(fixture.url, 303)
        verify(fixture.player, times(1)).setRecovery()
        verify(fixture.player, times(1)).reloadPlayQueueManager()
        verify(fixture.queue, never()).unsetRecovery(0)
        verify(fixture.queue, never()).error()
        verify(fixture.resolver, never()).setPlaybackQuality(anyString())

        fixture.controller.resetRecovery()
        verify(fixture.resolver).clearRejectedVideoStream()
        assertTrue(fixture.recover())
    }

    @Test
    fun fallbackIsWiredAfterThreeRefreshAttemptsAreExhausted() {
        val fixture = Fixture()
        val method = PlayerErrorController::class.java.getDeclaredMethod(
            "tryRecoverFromYouTubeMediaUrlFailure",
            PlaybackException::class.java
        ).apply { isAccessible = true }
        val error = fixture.error()
        repeat(3) { assertTrue(method.invoke(fixture.controller, error) as Boolean) }
        verify(fixture.resolver, never()).rejectVideoStreamForCurrentItem(anyString(), anyInt())
        assertTrue(method.invoke(fixture.controller, error) as Boolean)
        verify(fixture.resolver).rejectVideoStreamForCurrentItem(fixture.url, 303)
        verify(fixture.player, never()).changeState(Player.STATE_PAUSED)
        fixture.controller.resetRecovery()
    }

    @Test
    fun retainedItagIsItemScopedAndClearedOnReset() {
        val resolver = VideoPlaybackResolver(
            mock(android.content.Context::class.java),
            mock(org.schabi.newpipe.player.helper.PlayerDataSource::class.java),
            mock(VideoPlaybackResolver.QualityResolver::class.java)
        )
        val method = VideoPlaybackResolver::class.java.getDeclaredMethod(
            "consumeRejectedItag",
            String::class.java
        ).apply { isAccessible = true }
        resolver.rejectVideoStreamForCurrentItem("one", 303)
        org.junit.Assert.assertNull(method.invoke(resolver, "two"))
        org.junit.Assert.assertEquals(303, method.invoke(resolver, "one"))
        org.junit.Assert.assertEquals(303, method.invoke(resolver, "one"))
        resolver.clearRejectedVideoStream()
        org.junit.Assert.assertNull(method.invoke(resolver, "one"))
        resolver.rejectVideoStreamOnce("one", 398)
        org.junit.Assert.assertEquals(398, method.invoke(resolver, "one"))
        org.junit.Assert.assertNull(method.invoke(resolver, "one"))
    }

    @Test
    fun mismatchedAudioOrMissingDiagnosticsDoNotRejectTheVideo() {
        val fixture = Fixture()
        assertFalse(fixture.recover(itag = 140))
        assertFalse(fixture.recover(diagnostic = false))
        assertFalse(fixture.recover(status = 404))
        verify(fixture.resolver, never()).rejectVideoStreamForCurrentItem(anyString(), anyInt())
    }

    @Test
    fun noAlternativeLiveAudioAndOtherServicesDoNotUseVideoFallback() {
        val fixture = Fixture()
        `when`(fixture.info.videoOnlyStreams).thenReturn(listOf(fixture.selected))
        assertFalse(fixture.recover())
        `when`(fixture.info.videoOnlyStreams).thenReturn(listOf(fixture.selected, fixture.alternative))
        `when`(fixture.info.streamType).thenReturn(StreamType.LIVE_STREAM)
        assertFalse(fixture.recover())
        `when`(fixture.info.streamType).thenReturn(StreamType.VIDEO_STREAM)
        `when`(fixture.player.videoPlayerSelected()).thenReturn(false)
        assertFalse(fixture.recover())
        `when`(fixture.player.videoPlayerSelected()).thenReturn(true)
        `when`(fixture.item.serviceId).thenReturn(ServiceList.SoundCloud.serviceId)
        assertFalse(fixture.recover())
        verify(fixture.player, never()).reloadPlayQueueManager()
    }

    private class Fixture {
        val url = "https://www.youtube.com/watch?v=zvv06jKcMtE"
        val player = mock(Player::class.java)
        val dispatcher = mock(PlayerEventDispatcher::class.java)
        val resolver = mock(VideoPlaybackResolver::class.java)
        val queue = mock(PlayQueue::class.java)
        val item = mock(PlayQueueItem::class.java)
        val info = mock(StreamInfo::class.java)
        val selected = mock(VideoStream::class.java)
        val alternative = mock(VideoStream::class.java)
        val controller = PlayerErrorController(player, dispatcher, resolver)

        init {
            `when`(player.currentStreamInfo).thenReturn(Optional.of(info))
            `when`(player.selectedVideoStream).thenReturn(Optional.of(selected))
            `when`(player.videoPlayerSelected()).thenReturn(true)
            `when`(player.playQueue).thenReturn(queue)
            `when`(queue.item).thenReturn(item)
            `when`(item.url).thenReturn(url)
            `when`(item.serviceId).thenReturn(ServiceList.YouTube.serviceId)
            `when`(info.streamType).thenReturn(StreamType.VIDEO_STREAM)
            `when`(selected.itag).thenReturn(303)
            `when`(alternative.itag).thenReturn(22)
            `when`(info.videoStreams).thenReturn(emptyList())
            `when`(info.videoOnlyStreams).thenReturn(listOf(selected, alternative))
        }

        fun error(status: Int = 403, itag: Int = 303, diagnostic: Boolean = true): PlaybackException {
            val cause = HttpDataSource.InvalidResponseCodeException(
                status,
                "Forbidden",
                IOException(
                    if (diagnostic) "YouTube media request diagnostic: client=VISIONOS, itag=$itag" else "network"
                ),
                Collections.emptyMap(),
                androidx.media3.datasource.DataSpec.Builder().setUri("https://media.example.com/video").build(),
                byteArrayOf()
            )
            return PlaybackException("source", cause, PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)
        }

        fun recover(status: Int = 403, itag: Int = 303, diagnostic: Boolean = true): Boolean {
            val error = error(status, itag, diagnostic)
            val method = PlayerErrorController::class.java.getDeclaredMethod(
                "tryRecoverFromRejectedYouTubeVideo",
                PlaybackException::class.java,
                PlayQueueItem::class.java
            ).apply { isAccessible = true }
            return method.invoke(controller, error, item) as Boolean
        }
    }
}
