package org.schabi.newpipe.player.mediasource

import androidx.media3.common.Timeline
import androidx.media3.exoplayer.dash.manifest.DashManifest
import androidx.media3.exoplayer.source.MediaSource
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class FullLiveDvrSourceTest {
    @Test
    fun nonRefreshableDashIsReportedWithoutPreparingShortHlsFallback() {
        val dash = mock(MediaSource::class.java)
        val hls = mock(MediaSource::class.java)
        val source = YoutubeLiveMediaSource(dash, hls, false)
        val timeline = mock(Timeline::class.java)
        val window = Timeline.Window().apply { manifest = mock(DashManifest::class.java) }
        `when`(timeline.getWindow(eq(0), any(Timeline.Window::class.java))).thenReturn(window)
        val update = YoutubeLiveMediaSource::class.java.getDeclaredMethod(
            "onChildSourceInfoRefreshed",
            Int::class.javaPrimitiveType,
            MediaSource::class.java,
            Timeline::class.java
        ).apply { isAccessible = true }
        update.invoke(source, 0, dash, timeline)
        assertThrows(FullLiveDvrUnavailableException::class.java) { source.maybeThrowSourceInfoRefreshError() }
        verifyNoInteractions(hls)
    }
}
