package org.schabi.newpipe.player

import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import java.io.IOException
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class LiveMediaLoadRecoveryTest {
    private val engine = mock(ExoPlayer::class.java)
    private val controller = mock(PlayerErrorController::class.java)
    private val listener = LiveMediaLoadRecovery(engine, controller)
    private val eventTime = mock(EventTime::class.java)
    private val spec = DataSpec.Builder().setUri("https://example.com/segment").build()
    private val info = LoadEventInfo(1, spec, spec.uri, emptyMap(), 0, 1, 0)
    private val audioSegment = media(C.DATA_TYPE_MEDIA, C.TRACK_TYPE_AUDIO)

    @Test
    fun reportsOnlyActiveRejectedMediaSegmentsAndSuccessfulLoads() {
        listener.onLoadError(eventTime, info, audioSegment, httpError(403), true)
        listener.onLoadError(eventTime, info, audioSegment, httpError(404), false)
        listener.onLoadError(
            eventTime,
            info,
            media(C.DATA_TYPE_MANIFEST, C.TRACK_TYPE_UNKNOWN),
            httpError(403),
            false
        )
        verifyNoInteractions(controller)

        listener.onLoadError(eventTime, info, audioSegment, httpError(403), false)
        listener.onLoadCompleted(eventTime, info, audioSegment)
        verify(controller, times(1)).onLiveMediaSegmentRejected(engine, C.TRACK_TYPE_AUDIO, 1_000)
        verify(controller, times(1)).onLiveMediaSegmentLoaded(engine, C.TRACK_TYPE_AUDIO, 1_000)
    }

    private fun media(dataType: Int, trackType: Int) = MediaLoadData(
        dataType,
        trackType,
        null,
        C.SELECTION_REASON_UNKNOWN,
        null,
        1_000,
        3_000
    )

    private fun httpError(status: Int) = HttpDataSource.InvalidResponseCodeException(
        status,
        "HTTP error",
        IOException("Rejected"),
        emptyMap(),
        spec,
        byteArrayOf()
    )
}
