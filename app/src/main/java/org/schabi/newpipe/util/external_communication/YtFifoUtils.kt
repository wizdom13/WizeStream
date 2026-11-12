package org.schabi.newpipe.util.external_communication

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceManager
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import org.schabi.newpipe.R

/**
 * Optional handoff to a self-hosted [yt-fifo](https://github.com/jagjordi/yt-fifo) server, which
 * plays the audio of a stream on a Snapcast multi-room setup ("home audio"). The server is reached
 * over HTTPS; configure its base URL in the video and audio settings.
 */
object YtFifoUtils {
    private const val TAG = "YtFifoUtils"
    private const val MAX_RESPONSE_BYTES = 64L * 1024
    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val mainHandler = Handler(Looper.getMainLooper())

    // yt-fifo resolves the stream with yt-dlp before answering, which can take several seconds.
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .callTimeout(95, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .build()
    }

    /** Reject invalid settings immediately instead of silently hiding the handoff actions. */
    @JvmStatic
    fun configurePreference(preference: EditTextPreference) {
        preference.setOnPreferenceChangeListener { _, value ->
            val raw = value as? String ?: return@setOnPreferenceChangeListener false
            val valid = raw.isBlank() || normalizeServerUrl(raw) != null
            if (!valid) toast(preference.context, preference.context.getString(R.string.yt_fifo_url_invalid))
            valid
        }
    }

    /** The configured HTTPS server, or null when the setting is empty or invalid. */
    @JvmStatic
    fun serverUrl(context: Context): HttpUrl? = normalizeServerUrl(
        PreferenceManager.getDefaultSharedPreferences(context).getString(context.getString(R.string.yt_fifo_url_key), null)
    )

    /** Whether the home audio entries are offered, i.e. a server URL is set. */
    @JvmStatic
    fun shouldShowHomeAudio(context: Context): Boolean = serverUrl(context) != null

    /**
     * Sends the stream to the server asynchronously and reports the outcome in a toast: the resolved
     * title on success, the server's error message otherwise. [playNow] interrupts whatever the server
     * is playing, otherwise the stream is appended to its queue. Returns false when no server is set.
     */
    @JvmStatic
    fun sendToHomeAudio(context: Context, streamUrl: String, playNow: Boolean): Boolean {
        val server = serverUrl(context) ?: return false
        val appContext = context.applicationContext
        client.newCall(buildRequest(server, streamUrl, playNow)).enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.w(TAG, "Could not confirm yt-fifo handoff", e)
                    val reason = e.localizedMessage ?: e.javaClass.simpleName
                    toast(appContext, appContext.getString(R.string.home_audio_failed, reason))
                }

                override fun onResponse(call: Call, response: Response) {
                    toast(appContext, responseMessage(appContext, response, playNow, streamUrl))
                }
            }
        )
        return true
    }

    /** Accept HTTPS base URLs without credentials, query parameters or fragments. */
    internal fun normalizeServerUrl(raw: String?): HttpUrl? {
        var url = raw?.trim()?.trimEnd('/') ?: return null
        if (url.isEmpty()) return null
        if (!url.contains("://")) url = "https://$url"
        return url.toHttpUrlOrNull()?.takeIf {
            it.isHttps && it.username.isEmpty() && it.password.isEmpty() && it.query == null && it.fragment == null
        }
    }

    internal fun buildRequest(server: HttpUrl, streamUrl: String, playNow: Boolean): Request {
        val body = JSONObject().put("url", streamUrl).put("play_now", playNow).toString()
        return Request.Builder()
            .url(server.newBuilder().addPathSegment("queue").build())
            .post(body.toRequestBody(JSON))
            .build()
    }

    /** Close and bound every response, including failures while reading a successful HTTP reply. */
    internal fun responseMessage(context: Context, response: Response, playNow: Boolean, fallbackTitle: String): String = try {
        response.use {
            val body = it.body
            if (body.contentLength() > MAX_RESPONSE_BYTES || body.source().request(MAX_RESPONSE_BYTES + 1)) {
                throw IOException("Response exceeds limit")
            }
            resultMessage(context, body.string(), it.code, playNow, fallbackTitle)
        }
    } catch (error: IOException) {
        Log.w(TAG, "Could not read yt-fifo reply", error)
        context.getString(R.string.home_audio_failed, context.getString(R.string.home_audio_response_error))
    }

    /** The toast text for a `POST /queue` response. HTTP success alone does not confirm enqueueing. */
    internal fun resultMessage(context: Context, body: String, code: Int, playNow: Boolean, fallbackTitle: String): String {
        if (code !in 200..299) {
            return context.getString(R.string.home_audio_failed, errorMessage(body, code))
        }
        val title = firstTitle(body, fallbackTitle)
            ?: return context.getString(R.string.home_audio_failed, context.getString(R.string.home_audio_response_error))
        return context.getString(if (playNow) R.string.home_audio_playing else R.string.home_audio_queued, title)
    }

    /** The title of the first enqueued track in `{"enqueued": [{"title": …}, …]}`. */
    internal fun firstTitle(json: String, fallback: String): String? = try {
        val result = JSONObject(json)
        if (result.has("error")) {
            null
        } else {
            val tracks = result.getJSONArray("enqueued")
            val track = tracks.getJSONObject(0)
            if ((0 until tracks.length()).any { tracks.optJSONObject(it) == null }) {
                null
            } else {
                (track.opt("title") as? String)?.takeIf { it.isNotBlank() } ?: fallback
            }
        }
    } catch (_: JSONException) {
        null
    }

    /** The message of an `{"error": …}` body, or the HTTP status when there is none. */
    internal fun errorMessage(json: String, code: Int): String = try {
        JSONObject(json).optString("error").ifEmpty { "HTTP $code" }
    } catch (_: JSONException) {
        "HTTP $code"
    }

    private fun toast(context: Context, message: String) {
        mainHandler.post { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
    }
}
