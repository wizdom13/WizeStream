package org.schabi.newpipe.player.video

import android.os.Bundle
import androidx.media3.common.Effect
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class VideoAdjustmentControllerTest {
    private val saved = mutableListOf<VideoAdjustmentState>()
    private val effect = mock(Effect::class.java)
    private var restarts = 0
    private var failures = 0
    private var engine = mock(ExoPlayer::class.java)
    private lateinit var controller: VideoAdjustmentController

    init {
        controller = VideoAdjustmentController(
            VideoAdjustmentState(),
            { saved.add(it) },
            {
                restarts++
                controller.detach()
                engine = mock(ExoPlayer::class.java)
                controller.attach(engine)
            },
            { failures++ },
            { listOf(effect) }
        )
    }

    @Test
    fun defaultPlaybackNeverInstallsAnEffectsPipeline() {
        controller.attach(engine)
        assertFalse(controller.pipelineActive)
        verify(engine, never()).setVideoEffects(anyList())
    }

    @Test
    fun enablingAndDisablingRebuildsButSliderChangesStayOnTheSameEngine() {
        controller.attach(engine)
        controller.update(VideoAdjustmentState(enabled = true))
        assertEquals(1, restarts)
        assertTrue(controller.pipelineActive)
        val activeEngine = engine
        controller.update(controller.state.copy(brightness = 30, saturation = 0))
        assertEquals(1, restarts)
        assertTrue(activeEngine === engine)
        controller.update(controller.state.copy(enabled = false))
        assertEquals(2, restarts)
        assertFalse(controller.pipelineActive)
        verify(engine, never()).setVideoEffects(anyList())
    }

    @Test
    fun pausedPreviewSeeksToTheSamePositionWithoutStartingPlayback() {
        controller.attach(engine)
        controller.update(VideoAdjustmentState(enabled = true))
        `when`(engine.currentPosition).thenReturn(12345)
        controller.update(controller.state.copy(contrast = 25))
        verify(engine).seekTo(12345)
        verify(engine, never()).play()
    }

    @Test
    fun playingPreviewDoesNotSeekOrRestart() {
        controller.attach(engine)
        controller.update(VideoAdjustmentState(enabled = true))
        `when`(engine.playWhenReady).thenReturn(true)
        clearInvocations(engine)
        controller.update(controller.state.copy(brightness = -25))
        verify(engine).setVideoEffects(listOf(effect))
        verify(engine, never()).seekTo(org.mockito.ArgumentMatchers.anyLong())
        assertEquals(1, restarts)
    }

    @Test
    fun rememberingChangesOnlyPersistence() {
        controller.attach(engine)
        controller.update(VideoAdjustmentState(enabled = true, brightness = 10))
        clearInvocations(engine)
        controller.update(controller.state.copy(remember = true))
        assertTrue(saved.last().remember)
        assertEquals(1, restarts)
        verify(engine, never()).setVideoEffects(anyList())
    }

    @Test
    fun processorFailureRetriesOnceWithoutEffectsAndPersistsDisabledState() {
        controller.attach(engine)
        controller.update(VideoAdjustmentState(enabled = true, remember = true, saturation = 150))
        val error = TestPlaybackException(PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED)
        assertTrue(controller.recover(error))
        assertFalse(controller.recover(error))
        assertFalse(controller.state.enabled)
        assertTrue(controller.failed)
        assertEquals(150, controller.state.saturation)
        assertFalse(saved.last().enabled)
        assertEquals(2, restarts)
        assertEquals(1, failures)
        verify(engine, never()).setVideoEffects(anyList())
    }

    @Test
    fun networkErrorsRemainWithTheNormalRecoveryHandler() {
        controller.attach(engine)
        controller.update(VideoAdjustmentState(enabled = true))
        assertFalse(controller.recover(TestPlaybackException(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)))
        assertTrue(controller.state.enabled)
        assertEquals(1, restarts)
    }

    @Test
    fun engineReattachmentPreservesSessionValuesWithoutRequiringRemember() {
        controller.update(VideoAdjustmentState(enabled = true, brightness = 35, contrast = -10, saturation = 175))
        controller.attach(engine)
        controller.detach()
        val replacement = mock(ExoPlayer::class.java)
        controller.attach(replacement)
        assertEquals(VideoAdjustmentState(true, 35, -10, 175), controller.state)
        verify(replacement).setVideoEffects(listOf(effect))
    }

    @Test
    fun resetAndCorruptValuesRespectTheDocumentedSliderRanges() {
        controller.update(VideoAdjustmentState(true, -500, 999, -20, true))
        assertEquals(VideoAdjustmentState(true, -100, 100, 0, true), controller.state)
        controller.update(controller.state.reset())
        assertEquals(VideoAdjustmentState(enabled = true, remember = true), controller.state)
    }

    // Supply the timestamp explicitly: JVM tests have no Android system clock.
    private class TestPlaybackException(code: Int) : PlaybackException("Fixture", null, code, mock(Bundle::class.java), 0L)
}
