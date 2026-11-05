package org.schabi.newpipe.player.info

import android.app.Application
import androidx.media3.common.Format
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PlaybackInformationTest {
    @Test
    fun `decoder state is captured before opening and cleared when engine changes`() {
        val info = PlaybackInformation()
        val first = mock(ExoPlayer::class.java)
        val second = mock(ExoPlayer::class.java)
        val event = mock(AnalyticsListener.EventTime::class.java)
        info.attach(first)
        info.onVideoDecoderInitialized(event, "video.decoder", 0, 0)
        info.onAudioDecoderInitialized(event, "audio.decoder", 0, 0)
        assertEquals("video.decoder", info.videoDecoder)
        info.onVideoDecoderReleased(event, "old.decoder")
        assertEquals("video.decoder", info.videoDecoder)
        info.onVideoDecoderReleased(event, "video.decoder")
        assertNull(info.videoDecoder)
        info.attach(second)
        verify(first).removeAnalyticsListener(info)
        verify(second).addAnalyticsListener(info)
        assertNull(info.audioDecoder)
        info.detach()
        verify(second).removeAnalyticsListener(info)
    }

    @Test
    fun `snapshot follows playing format and never invents unknown values`() {
        val context = RuntimeEnvironment.getApplication()
        val info = PlaybackInformation()
        val engine = mock(ExoPlayer::class.java)
        info.attach(engine)
        val format = Format.Builder().setWidth(1920).setHeight(1080).setFrameRate(30f)
            .setCodecs("avc1.640028").setAverageBitrate(2_000_000).build()
        `when`(engine.videoFormat).thenReturn(format)
        `when`(engine.totalBufferedDuration).thenReturn(4500L)
        assertTrue(info.text(context).contains("1920 × 1080"))
        assertTrue(info.text(context).contains("avc1.640028"))
        assertEquals("2000 kb/s", info.bitrate(format))
        assertNull(info.bitrate(Format.Builder().setPeakBitrate(5_000_000).build()))
        assertNull(info.codec(null))
        `when`(engine.videoFormat).thenReturn(null)
        val text = info.text(context)
        assertFalse(text.contains("1920 × 1080"))
        assertTrue(text.contains(context.getString(R.string.playback_info_unavailable)))
        info.detach()
        assertFalse(info.text(context).contains("4.5"))
    }
}
