package org.schabi.newpipe.player

import androidx.media3.common.C
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
class AudioDecoderOutputRecoveryTest {
    @Test
    fun audioCodecOutputFailureRebuildsOnceWithoutDiscardingTheQueue() {
        val f = Fixture()
        `when`(f.player.videoAdjustments).thenReturn(mock(VideoAdjustmentController::class.java))

        f.controller.onPlayerError(f.error())

        verify(f.player).saveStreamProgressState()
        verify(f.player).setRecovery()
        verify(f.player).onBuffering()
        verify(f.player).restartForAudioTrackRecovery()
        verify(f.player, never()).onPlaybackShutdown()
        verify(f.queue, never()).error()
        verify(f.queue, never()).dispose()
        assertFalse(f.controller.tryRecoverFromAudioDecoderOutputFailure(f.error()))

        f.controller.resetRecovery()
        assertTrue(f.controller.tryRecoverFromAudioDecoderOutputFailure(f.error()))
        verify(f.player, times(2)).restartForAudioTrackRecovery()
    }

    @Test
    fun classificationRequiresAudioDecodingCodeAndMediaCodecOutputFrame() {
        val error = Fixture().error()
        assertTrue(
            PlayerHttpErrorRecovery.isRecoverableAudioDecoderOutputFailure(
                PlaybackException.ERROR_CODE_DECODING_FAILED,
                C.TRACK_TYPE_AUDIO,
                error
            )
        )
        assertTrue(
            PlayerHttpErrorRecovery.isRecoverableAudioDecoderOutputFailure(
                PlaybackException.ERROR_CODE_DECODING_FAILED,
                C.TRACK_TYPE_AUDIO,
                Fixture().error("getOutputBuffer")
            )
        )
        assertFalse(
            PlayerHttpErrorRecovery.isRecoverableAudioDecoderOutputFailure(
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                C.TRACK_TYPE_AUDIO,
                error
            )
        )
        assertFalse(
            PlayerHttpErrorRecovery.isRecoverableAudioDecoderOutputFailure(
                PlaybackException.ERROR_CODE_DECODING_FAILED,
                C.TRACK_TYPE_VIDEO,
                error
            )
        )

        val unrelated = IllegalStateException("codec state").apply {
            stackTrace = arrayOf(
                StackTraceElement(
                    "android.media.MediaCodec",
                    "dequeueOutputBuffer",
                    "MediaCodec.java",
                    -2
                )
            )
        }
        assertFalse(
            PlayerHttpErrorRecovery.isRecoverableAudioDecoderOutputFailure(
                PlaybackException.ERROR_CODE_DECODING_FAILED,
                C.TRACK_TYPE_AUDIO,
                unrelated
            )
        )
    }

    @Test
    fun missingQueueItemAndReleasedEngineDoNotConsumeTheRetry() {
        val f = Fixture()
        `when`(f.queue.item).thenReturn(null)
        assertFalse(f.controller.tryRecoverFromAudioDecoderOutputFailure(f.error()))
        `when`(f.queue.item).thenReturn(f.item)
        `when`(f.player.exoPlayerIsNull()).thenReturn(true)
        assertFalse(f.controller.tryRecoverFromAudioDecoderOutputFailure(f.error()))
        `when`(f.player.exoPlayerIsNull()).thenReturn(false)
        assertTrue(f.controller.tryRecoverFromAudioDecoderOutputFailure(f.error()))
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

        fun error(codecMethod: String = "releaseOutputBuffer"): PlaybackException {
            val codecState = IllegalStateException("codec output state").apply {
                stackTrace = arrayOf(
                    StackTraceElement(
                        "android.media.MediaCodec",
                        codecMethod,
                        "MediaCodec.java",
                        -2
                    )
                )
            }
            return PlaybackException(
                "MediaCodecAudioRenderer error",
                RuntimeException("Decoder failed: OMX.google.aac.decoder", codecState),
                PlaybackException.ERROR_CODE_DECODING_FAILED
            )
        }
    }
}
