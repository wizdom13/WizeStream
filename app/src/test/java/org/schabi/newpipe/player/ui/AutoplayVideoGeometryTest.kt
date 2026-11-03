package org.schabi.newpipe.player.ui

import android.view.View.MeasureSpec
import androidx.media3.common.Player as Media3Player
import androidx.media3.common.VideoSize
import androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
import androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
import androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.player.PlaybackParametersController
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.PlayerAudioController
import org.schabi.newpipe.player.PlayerErrorController
import org.schabi.newpipe.player.PlayerMedia3ListenerController
import org.schabi.newpipe.player.PlayerMetadataController
import org.schabi.newpipe.player.SleepTimerPlaybackController
import org.schabi.newpipe.player.SponsorBlockPlaybackController
import org.schabi.newpipe.views.ExpandableSurfaceView

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AutoplayVideoGeometryTest {
    private class Fixture(mode: Int) {
        val surface = ExpandableSurfaceView(RuntimeEnvironment.getApplication(), null)
        val player = mock(Player::class.java, RETURNS_DEEP_STUBS)
        val engine = mock(Media3Player::class.java)
        val ui = mock(PlayerUi::class.java)
        val listener = PlayerMedia3ListenerController(
            player,
            mock(PlayerAudioController::class.java),
            mock(PlayerMetadataController::class.java),
            mock(PlaybackParametersController::class.java),
            mock(SponsorBlockPlaybackController::class.java),
            mock(PlayerErrorController::class.java),
            mock(SleepTimerPlaybackController::class.java)
        )

        init {
            surface.setResizeMode(mode)
            surface.setAspectRatio(16f / 9f)
            `when`(player.UIs()).thenReturn(PlayerUiList(ui))
            `when`(engine.videoSize).thenReturn(VideoSize(1920, 1080))
            doAnswer {
                surface.clearAspectRatio()
                null
            }.`when`(ui).onMediaItemTransition()
            doAnswer { invocation ->
                PlayerVideoGeometry.apply(surface, invocation.getArgument(0))
                null
            }.`when`(ui).onVideoSizeChanged(org.mockito.ArgumentMatchers.any(VideoSize::class.java))
        }

        fun transition() {
            listener.onPositionDiscontinuity(
                Media3Player.PositionInfo(null, 0, null, null, 0, 0, 0, -1, -1),
                Media3Player.PositionInfo(null, 1, null, null, 1, 0, 0, -1, -1),
                Media3Player.DISCONTINUITY_REASON_AUTO_TRANSITION
            )
        }

        fun layout() {
            surface.measure(
                MeasureSpec.makeMeasureSpec(2400, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(1080, MeasureSpec.EXACTLY)
            )
            surface.layout(0, 0, surface.measuredWidth, surface.measuredHeight)
        }
    }

    @Test
    fun seamlessAutoplayRestoresGeometryWithoutAnotherDecoderCallbackInEveryResizeMode() {
        for (mode in listOf(RESIZE_MODE_FIT, RESIZE_MODE_FILL, RESIZE_MODE_ZOOM)) {
            val f = Fixture(mode)
            f.surface.setUserTransform(2f, 20f, 20f)
            f.transition()
            assertEquals(0f, f.surface.videoAspectRatio, 0f)
            // No onVideoSizeChanged or onRenderedFirstFrame event arrives from the decoder.
            f.listener.onEvents(f.engine)
            f.layout()
            assertEquals(16f / 9f, f.surface.videoAspectRatio, 0.0001f)
            assertEquals(mode, f.surface.getResizeMode())
            assertEquals(1f, f.surface.userZoomScale, 0f)
            assertEquals(if (mode == RESIZE_MODE_FIT) 1920 else 2400, f.surface.measuredWidth)
            assertEquals(if (mode == RESIZE_MODE_ZOOM) 1.25f else 1f, f.surface.scaleY, 0.0001f)
        }
    }

    @Test
    fun unknownSizeWaitsForValidStateAndLaterEventsDoNotOverwriteGeometry() {
        val f = Fixture(RESIZE_MODE_FIT)
        f.transition()
        `when`(f.engine.videoSize).thenReturn(VideoSize.UNKNOWN)
        f.listener.onEvents(f.engine)
        assertEquals(0f, f.surface.videoAspectRatio, 0f)
        `when`(f.engine.videoSize).thenReturn(VideoSize(1080, 1920))
        f.listener.onEvents(f.engine)
        assertEquals(9f / 16f, f.surface.videoAspectRatio, 0.0001f)
        f.surface.setAspectRatio(4f / 3f)
        f.listener.onEvents(f.engine)
        assertEquals(4f / 3f, f.surface.videoAspectRatio, 0.0001f)
    }

    @Test
    fun sizeDeliveredBeforeTransitionIsRestoredAfterGeometryIsCleared() {
        val f = Fixture(RESIZE_MODE_FIT)
        f.listener.onVideoSizeChanged(VideoSize(1920, 1080))
        f.transition()
        f.listener.onEvents(f.engine)
        assertEquals(16f / 9f, f.surface.videoAspectRatio, 0.0001f)
    }
}
