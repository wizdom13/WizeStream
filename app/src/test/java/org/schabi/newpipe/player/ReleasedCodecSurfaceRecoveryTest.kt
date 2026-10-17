package org.schabi.newpipe.player

import android.media.MediaCodec
import androidx.media3.common.PlaybackException
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
import org.robolectric.annotation.Config
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.player.resolver.VideoPlaybackResolver
import org.schabi.newpipe.player.video.VideoAdjustmentController

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28, 35])
class ReleasedCodecSurfaceRecoveryTest {
    @Test
    fun errorHandlerRebuildsOnceWithoutDiscardingTheQueue() {
        val f = Fixture()
        `when`(f.player.videoAdjustments).thenReturn(mock(VideoAdjustmentController::class.java))
        f.controller.onPlayerError(f.error())
        assertFalse(f.controller.tryRecoverFromReleasedCodecSurface(f.error()))
        verify(f.player).restartForSurfaceRecovery()
        verify(f.player).onBuffering()
        verify(f.player, never()).onPlaybackShutdown()
        verify(f.queue, never()).error()
        verify(f.queue, never()).dispose()
        f.controller.resetRecovery()
        assertTrue(f.controller.tryRecoverFromReleasedCodecSurface(f.error()))
        verify(f.player, times(2)).restartForSurfaceRecovery()
    }

    @Test
    fun unrelatedRuntimeErrorsAndOtherCodecStatesAreNotRetried() {
        val f = Fixture()
        val unrelated = PlaybackException(
            "other",
            IllegalStateException(Fixture.MESSAGE),
            PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK
        )
        assertFalse(f.controller.tryRecoverFromReleasedCodecSurface(unrelated))
        assertFalse(f.controller.tryRecoverFromReleasedCodecSurface(f.error("Executing state")))
        val decoding = f.error(code = PlaybackException.ERROR_CODE_DECODING_FAILED)
        assertFalse(f.controller.tryRecoverFromReleasedCodecSurface(decoding))
        verify(f.player, never()).restartForSurfaceRecovery()
    }

    @Test
    fun missingQueueItemAndReleasedEngineDoNotConsumeTheRetry() {
        val f = Fixture()
        `when`(f.queue.item).thenReturn(null)
        assertFalse(f.controller.tryRecoverFromReleasedCodecSurface(f.error()))
        `when`(f.queue.item).thenReturn(f.item)
        `when`(f.player.exoPlayerIsNull()).thenReturn(true)
        assertFalse(f.controller.tryRecoverFromReleasedCodecSurface(f.error()))
        `when`(f.player.exoPlayerIsNull()).thenReturn(false)
        assertTrue(f.controller.tryRecoverFromReleasedCodecSurface(f.error()))
    }

    private class Fixture {
        val player = mock(Player::class.java)
        val queue = mock(PlayQueue::class.java)
        val item = mock(PlayQueueItem::class.java)
        val controller = PlayerErrorController(
            player,
            mock(PlayerEventDispatcher::class.java),
            mock(VideoPlaybackResolver::class.java)
        )

        init {
            `when`(player.playQueue).thenReturn(queue)
            `when`(queue.item).thenReturn(item)
            `when`(item.url).thenReturn("https://www.youtube.com/watch?v=test")
            `when`(item.serviceId).thenReturn(0)
        }

        fun error(
            message: String = MESSAGE,
            code: Int = PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK
        ): PlaybackException {
            val constructor = MediaCodec.CodecException::class.java.getDeclaredConstructor(
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java
            ).apply { isAccessible = true }
            val codec = constructor.newInstance(MediaCodec.CodecException.ERROR_RECLAIMED, 0, message)
            return PlaybackException("runtime", IllegalStateException("wrapped", codec), code)
        }

        companion object {
            const val MESSAGE = "setSurface() is valid only at Executing states; " +
                "currently at Released state"
        }
    }
}
