package org.schabi.newpipe.player

import androidx.media3.common.C
import androidx.media3.common.Player as Media3Player
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class LiveLoadDiagnosticsTest {
    private val engine = mock(ExoPlayer::class.java)
    private val time = mock(EventTime::class.java)
    private val spec = DataSpec.Builder()
        .setUri("https://private.example/segment?token=secret")
        .setHttpRequestHeaders(mapOf("Authorization" to "secret-header"))
        .build()
    private val info = LoadEventInfo(42, spec, spec.uri, emptyMap(), 0, 321, 456)
    private val data = MediaLoadData(
        C.DATA_TYPE_MEDIA,
        C.TRACK_TYPE_AUDIO,
        null,
        C.SELECTION_REASON_UNKNOWN,
        null,
        1_000,
        3_000
    )
    private val listener = LiveLoadDiagnostics(engine) { true }

    @Before
    fun setUp() {
        ShadowLog.clear()
        `when`(engine.playbackState).thenReturn(Media3Player.STATE_BUFFERING)
        `when`(engine.totalBufferedDuration).thenReturn(0)
    }

    @Test
    fun tracesSegmentLifecycleAndBufferStateWithoutRequestSecrets() {
        listener.onLoadStarted(time, info, data)
        listener.onLoadCompleted(time, info, data)
        listener.onLoadCanceled(time, info, data)
        listener.onPlaybackStateChanged(time, Media3Player.STATE_BUFFERING)
        listener.onIsLoadingChanged(time, false)
        val logs = logs()
        assertTrue(logs.contains("started task=42"))
        assertTrue(logs.contains("completed task=42"))
        assertTrue(logs.contains("canceled task=42"))
        assertTrue(logs.contains("durationMs=321 bytes=456"))
        assertTrue(logs.contains("mediaStartMs=1000 mediaEndMs=3000"))
        assertTrue(logs.contains("bufferedDurationMs=0"))
        assertTrue(logs.contains("Live loading=false"))
        assertFalse(logs.contains("private.example"))
        assertFalse(logs.contains("secret"))
        assertFalse(logs.contains("Authorization"))
    }

    @Test
    fun rateLimitKeepsErrorsAndSafeHttpStatus() {
        repeat(20) { listener.onLoadStarted(time, info, data) }
        assertEquals(12, ShadowLog.getLogsForTag("LiveLoadDiagnostics").size)
        val error = HttpDataSource.InvalidResponseCodeException(
            403,
            "private.example?token=secret",
            IOException("signed URL private.example?token=secret"),
            mapOf("Authorization" to listOf("secret-header")),
            spec,
            byteArrayOf()
        )
        listener.onLoadError(time, info, data, error, false)
        assertEquals(13, ShadowLog.getLogsForTag("LiveLoadDiagnostics").size)
        val logs = logs()
        assertTrue(logs.contains("status=403 canceled=false"))
        assertTrue(logs.contains("InvalidResponseCodeException<-IOException"))
        assertTrue(logs.contains("omitted=8"))
        assertFalse(logs.contains("private.example"))
        assertFalse(logs.contains("secret"))
    }

    @Test
    fun ignoresOtherStreams() {
        val other = LiveLoadDiagnostics(engine) { false }
        other.onLoadStarted(time, info, data)
        other.onPlaybackStateChanged(time, Media3Player.STATE_BUFFERING)
        other.onIsLoadingChanged(time, true)
        assertTrue(ShadowLog.getLogsForTag("LiveLoadDiagnostics").isEmpty())
    }

    private fun logs(): String = ShadowLog.getLogsForTag("LiveLoadDiagnostics")
        .joinToString("\n") { it.msg }
}
