package org.schabi.newpipe.util.external_communication

import android.content.Context
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceManager
import java.io.IOException
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
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
        listOf("http://host", "https://user:secret@host", "https://host?token=secret", "https://host/#fragment").forEach {
            assertNull(YtFifoUtils.normalizeServerUrl(it))
        }
        assertEquals("https://192.168.1.10:8888/", YtFifoUtils.normalizeServerUrl(" 192.168.1.10:8888/ ").toString())
        assertEquals("https://audio.example/base", YtFifoUtils.normalizeServerUrl("https://audio.example/base//").toString())
    }

    @Test
    fun entriesAreOfferedOnlyWithAServerConfigured() {
        assertFalse(YtFifoUtils.shouldShowHomeAudio(context))
        assertFalse(YtFifoUtils.sendToHomeAudio(context, "https://www.youtube.com/watch?v=abc", true))
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(context.getString(R.string.yt_fifo_url_key), "192.168.1.10:8888").apply()
        assertTrue(YtFifoUtils.shouldShowHomeAudio(context))
        assertEquals("https://192.168.1.10:8888/", YtFifoUtils.serverUrl(context).toString())
    }

    @Test
    fun preferenceRejectsInvalidAddressesAndAllowsDisabling() {
        val preference = EditTextPreference(context)
        YtFifoUtils.configurePreference(preference)
        assertFalse(preference.callChangeListener("http://host"))
        assertTrue(preference.callChangeListener("https://audio.example"))
        assertTrue(preference.callChangeListener("   "))
    }

    @Test
    fun postsTheStreamUrlToTheQueueEndpoint() {
        val request = YtFifoUtils.buildRequest("https://host:8888/".toHttpUrl(), "https://www.youtube.com/watch?v=abc", true)
        assertEquals("POST", request.method)
        assertEquals("https://host:8888/queue", request.url.toString())
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

    @Test
    fun invalidSuccessRepliesNeverReportAQueuedOrPlayingTrack() {
        val failure = context.getString(R.string.home_audio_failed, context.getString(R.string.home_audio_response_error))
        listOf(
            "",
            "<html>Welcome</html>",
            "{}",
            "{",
            """{"enqueued":[]}""",
            """{"enqueued":[null]}""",
            """{"enqueued":[{},false]}""",
            """{"error":"failed","enqueued":[{}]}"""
        ).forEach { reply ->
            listOf(true, false).forEach { playNow ->
                assertEquals(failure, YtFifoUtils.resultMessage(context, reply, 200, playNow, "fallback"))
            }
        }
    }

    @Test
    fun closesValidOversizedAndInterruptedResponsesAndReportsReadFailures() {
        val failure = context.getString(R.string.home_audio_failed, context.getString(R.string.home_audio_response_error))
        listOf(false, true).forEach { knownLength ->
            val oversized = TrackedBody("x".repeat(65 * 1024), knownLength = knownLength)
            assertEquals(failure, YtFifoUtils.responseMessage(context, response(oversized), false, "fallback"))
            assertTrue(oversized.closed)
        }
        val interrupted = TrackedBody("", failRead = true)
        assertEquals(failure, YtFifoUtils.responseMessage(context, response(interrupted), true, "fallback"))
        assertTrue(interrupted.closed)

        val valid = TrackedBody("""{"enqueued":[{"title":"Track"}]}""")
        assertEquals(
            context.getString(R.string.home_audio_queued, "Track"),
            YtFifoUtils.responseMessage(context, response(valid), false, "fallback")
        )
        assertTrue(valid.closed)
    }

    private fun response(body: ResponseBody): Response = Response.Builder()
        .request(Request.Builder().url("https://audio.example/queue").build())
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(body)
        .build()

    private class TrackedBody(content: String, failRead: Boolean = false, private val knownLength: Boolean = true) : ResponseBody() {
        var closed = false
        private val length = content.toByteArray().size.toLong()
        private val source = object : ForwardingSource(Buffer().writeUtf8(content)) {
            override fun read(sink: Buffer, byteCount: Long): Long {
                if (failRead) throw IOException("Connection interrupted")
                return super.read(sink, byteCount)
            }

            override fun close() {
                closed = true
                super.close()
            }
        }.buffer()

        override fun contentType(): MediaType? = null
        override fun contentLength(): Long = if (knownLength) length else -1
        override fun source(): BufferedSource = source
    }
}
