package org.schabi.newpipe.util.external_communication

import android.content.Context
import androidx.preference.PreferenceManager
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class YtFifoUtilsTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun normalizesTypedServerUrls() {
        assertNull(YtFifoUtils.normalizeServerUrl(null))
        assertNull(YtFifoUtils.normalizeServerUrl(""))
        assertNull(YtFifoUtils.normalizeServerUrl("   "))
        assertNull(YtFifoUtils.normalizeServerUrl("not a url"))
        assertNull(YtFifoUtils.normalizeServerUrl("ftp://host/"))
        assertEquals("http://192.168.1.10:8888/", YtFifoUtils.normalizeServerUrl(" 192.168.1.10:8888/ ").toString())
        assertEquals("https://audio.example/base", YtFifoUtils.normalizeServerUrl("https://audio.example/base//").toString())
    }

    @Test
    fun entriesAreOfferedOnlyWithAServerConfigured() {
        assertFalse(YtFifoUtils.shouldShowHomeAudio(context))
        assertFalse(YtFifoUtils.sendToHomeAudio(context, "https://www.youtube.com/watch?v=abc", true))
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(context.getString(R.string.yt_fifo_url_key), "192.168.1.10:8888").apply()
        assertTrue(YtFifoUtils.shouldShowHomeAudio(context))
        assertEquals("http://192.168.1.10:8888/", YtFifoUtils.serverUrl(context).toString())
    }

    @Test
    fun postsTheStreamUrlToTheQueueEndpoint() {
        val request = YtFifoUtils.buildRequest("http://host:8888/".toHttpUrl(), "https://www.youtube.com/watch?v=abc", true)
        assertEquals("POST", request.method)
        assertEquals("http://host:8888/queue", request.url.toString())
        assertEquals("application/json; charset=utf-8", request.body?.contentType().toString())
        val body = JSONObject(Buffer().also { request.body!!.writeTo(it) }.readUtf8())
        assertEquals("https://www.youtube.com/watch?v=abc", body.getString("url"))
        assertTrue(body.getBoolean("play_now"))
        assertEquals(setOf("url", "play_now"), body.keys().asSequence().toSet())

        val nested = YtFifoUtils.buildRequest("https://audio.example/base".toHttpUrl(), "u", false)
        assertEquals("https://audio.example/base/queue", nested.url.toString())
    }

    @Test
    fun reportsTheResolvedTitleOrTheServersError() {
        val ok = """{"enqueued":[{"id":"3","title":"Me at the zoo"}],"queue_length":1}"""
        assertEquals(
            context.getString(R.string.home_audio_playing, "Me at the zoo"),
            YtFifoUtils.resultMessage(context, ok, 200, true, "fallback")
        )
        assertEquals(
            context.getString(R.string.home_audio_queued, "fallback"),
            YtFifoUtils.resultMessage(context, """{"enqueued":[{"id":"3"}]}""", 200, false, "fallback")
        )
        assertEquals(
            context.getString(R.string.home_audio_failed, "yt-dlp: This video is unavailable"),
            YtFifoUtils.resultMessage(context, """{"error":"yt-dlp: This video is unavailable"}""", 422, true, "x")
        )
        assertEquals(
            context.getString(R.string.home_audio_failed, "HTTP 502"),
            YtFifoUtils.resultMessage(context, "<html>bad gateway</html>", 502, true, "x")
        )
    }
}
