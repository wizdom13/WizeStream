package org.schabi.newpipe.local.tv

import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal object TvPlaylistLoader {
    private val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).followRedirects(false).build()

    fun read(input: InputStream): String {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > M3uParser.MAX_BYTES) throw TvPlaylistLimitException()
            output.write(buffer, 0, count)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    suspend fun load(address: String, name: String): Pair<String, TvImport> {
        var url = tvUrl(address) ?: throw IOException("HTTPS playlist required")
        repeat(4) {
            val response = fetch(url)
            if (response.first != null) {
                url = tvUrl(requireNotNull(response.first), url) ?: throw IOException("Unsupported redirect")
            } else {
                val text = response.second
                return url to if (M3uParser.isHls(text)) TvImport(listOf(TvChannel(name, url, hls = true)), 0) else M3uParser.parse(text, url)
            }
        }
        throw IOException("Too many redirects")
    }

    private suspend fun fetch(url: String): Pair<String?, String> = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url(url).build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = response.use {
                        when {
                            it.isRedirect -> (it.header("Location") ?: throw IOException("Missing redirect")) to ""
                            !it.isSuccessful -> throw IOException("Playlist request failed")
                            else -> null to read(it.body.byteStream())
                        }
                    }
                    if (continuation.isActive) continuation.resume(result)
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        })
    }
}
