package org.schabi.newpipe.cast

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.media3.exoplayer.ExoPlayer
import java.util.Optional
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.Player

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class TubeBeamHandoffTest {
    @Test
    fun externalHandoffNeverPausesLocalPlaybackEvenWhenLaunchFails() {
        val info = mock(StreamInfo::class.java)
        `when`(info.url).thenReturn("https://www.youtube.com/watch?v=abc")
        `when`(info.streamType).thenReturn(StreamType.VIDEO_STREAM)
        val player = mock(Player::class.java)
        val engine = mock(ExoPlayer::class.java)
        `when`(player.currentStreamInfo).thenReturn(Optional.of(info))
        `when`(player.exoPlayer).thenReturn(engine)
        `when`(engine.currentPosition).thenReturn(42_500L)
        var sent: Intent? = null
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication() as Context) {
            override fun startActivity(intent: Intent) {
                sent = intent
            }
        }
        assertTrue(FCastManager.sendToTubeBeam(context, info, player))
        assertEquals("https://www.youtube.com/watch?v=abc&t=42", sent!!.getStringExtra(Intent.EXTRA_TEXT))
        `when`(info.streamType).thenReturn(StreamType.LIVE_STREAM)
        assertTrue(FCastManager.sendToTubeBeam(context, info, player))
        assertEquals(info.url, sent!!.getStringExtra(Intent.EXTRA_TEXT))
        `when`(info.streamType).thenReturn(StreamType.VIDEO_STREAM)
        `when`(player.currentStreamInfo).thenReturn(Optional.empty())
        assertTrue(FCastManager.sendToTubeBeam(context, info, player))
        assertEquals(info.url, sent!!.getStringExtra(Intent.EXTRA_TEXT))
        val missing = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) {
                throw ActivityNotFoundException()
            }
        }
        assertFalse(FCastManager.sendToTubeBeam(missing, info, player))
        verify(player, never()).pause()
    }
}
