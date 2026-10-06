package org.schabi.newpipe.player

import android.app.Application
import android.os.Looper
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import org.schabi.newpipe.R
import org.schabi.newpipe.player.datasource.InvidiousMediaResponse
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.resolver.VideoPlaybackResolver
import org.schabi.newpipe.player.video.VideoAdjustmentController

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class InvidiousMediaErrorTest {
    @Test
    fun instanceErrorPausesWithoutSkippingOrRetryingAndExplainsTheFailure() {
        val context = RuntimeEnvironment.getApplication()
        val player = mock(Player::class.java)
        val engine = mock(ExoPlayer::class.java)
        val queue = mock(PlayQueue::class.java)
        val dispatcher = mock(PlayerEventDispatcher::class.java)
        `when`(player.context).thenReturn(context)
        `when`(player.exoPlayer).thenReturn(engine)
        `when`(player.playQueue).thenReturn(queue)
        `when`(player.videoAdjustments).thenReturn(mock(VideoAdjustmentController::class.java))
        val response = InvidiousMediaResponse.InvalidResponseException(
            "Invidious returned HTML instead of media",
            DataSpec.Builder().setUri("https://example.org/stream").build()
        )
        val error = PlaybackException("Source error", response, response.reason)
        PlayerErrorController(player, dispatcher, mock(VideoPlaybackResolver::class.java)).onPlayerError(error)
        shadowOf(Looper.getMainLooper()).idle()

        verify(engine).pause()
        verify(player).changeState(Player.STATE_PAUSED)
        verify(queue, never()).error()
        verify(player, never()).reloadPlayQueueManager()
        verify(dispatcher).notifyPlayerError(error, true)
        assertEquals(context.getString(R.string.invidious_media_error), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun unrelatedContentTypeErrorsKeepNormalQueueErrorHandling() {
        val context = RuntimeEnvironment.getApplication()
        val player = mock(Player::class.java)
        val engine = mock(ExoPlayer::class.java)
        val queue = mock(PlayQueue::class.java)
        val dispatcher = mock(PlayerEventDispatcher::class.java)
        `when`(player.context).thenReturn(context)
        `when`(player.exoPlayer).thenReturn(engine)
        `when`(player.playQueue).thenReturn(queue)
        `when`(player.videoAdjustments).thenReturn(mock(VideoAdjustmentController::class.java))
        val response = HttpDataSource.InvalidContentTypeException(
            "text/html",
            DataSpec.Builder().setUri("https://other.example.org/stream").build()
        )
        val error = PlaybackException("Source error", response, response.reason)
        PlayerErrorController(player, dispatcher, mock(VideoPlaybackResolver::class.java)).onPlayerError(error)
        shadowOf(Looper.getMainLooper()).idle()

        verify(queue).error()
        verify(engine, never()).pause()
        verify(dispatcher).notifyPlayerError(error, false)
        assertEquals(context.getString(R.string.error_report_notification_toast), ShadowToast.getTextOfLatestToast())
    }
}
