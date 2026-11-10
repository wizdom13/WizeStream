package org.schabi.newpipe.local.radio

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

internal data class RadioStation(
    val name: String,
    val url: String,
    val details: String = "",
    val hls: Boolean = false,
    val logoUrl: String? = null
) {
    fun json(): JSONObject = JSONObject()
        .put("name", name)
        .put("url", url)
        .put("details", details)
        .put("hls", hls)
        .put("logoUrl", logoUrl)

    companion object {
        fun fromJson(json: JSONObject): RadioStation? {
            val url = secureRadioUrl(json.optString("url")) ?: return null
            val name = json.optString("name").trim().takeIf(String::isNotEmpty) ?: return null
            return RadioStation(name, url, json.optString("details"), json.optBoolean("hls"), secureRadioUrl(json.optString("logoUrl")))
        }
    }
}

internal fun secureRadioUrl(value: String): String? = value.trim().toHttpUrlOrNull()
    ?.takeIf { it.isHttps && it.username.isEmpty() && it.password.isEmpty() }
    ?.newBuilder()?.fragment(null)?.build()?.toString()

internal fun isRadioHls(url: String): Boolean = url.toHttpUrlOrNull()?.encodedPath?.endsWith(".m3u8", ignoreCase = true) == true
