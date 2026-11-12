package org.schabi.newpipe.local.tv

import java.io.IOException
import java.util.Locale
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

internal class TvPlaylistLimitException : IOException()

internal fun tvUrl(value: String, base: String? = null): String? {
    val url = value.trim().toHttpUrlOrNull() ?: base?.toHttpUrlOrNull()?.resolve(value.trim())
    return url?.takeIf { it.isHttps && it.username.isEmpty() && it.password.isEmpty() && it.toString().length <= 8192 }
        ?.newBuilder()?.fragment(null)?.build()?.toString()
}

internal data class TvChannel(
    val name: String,
    val url: String,
    val group: String = "",
    val logo: String? = null,
    val hls: Boolean = false,
    val tvgId: String = "",
    val country: String = "",
    val alternatives: List<String> = emptyList()
) {
    val urls: List<String> get() = listOf(url) + alternatives
    fun json(): JSONObject = JSONObject().put("name", name).put("url", url).put("group", group).put("logo", logo).put("hls", hls)
        .put("tvgId", tvgId).put("country", country).put("alternatives", JSONArray(alternatives))

    companion object {
        fun parse(json: JSONObject): TvChannel? {
            val url = tvUrl(json.optString("url")) ?: return null
            val name = json.optString("name").trim().take(256).takeIf(String::isNotEmpty) ?: return null
            val alternatives = json.optJSONArray("alternatives") ?: JSONArray()
            if (alternatives.length() > 31) throw TvPlaylistLimitException()
            val streams = (0 until alternatives.length()).mapNotNull { tvUrl(alternatives.optString(it)) }.distinct().filterNot { it == url }
            return TvChannel(
                name,
                url,
                json.optString("group").take(128),
                tvUrl(json.optString("logo")),
                json.optBoolean("hls"),
                json.optString("tvgId").take(256),
                json.optString("country").takeIf { it.matches(Regex("[A-Z]{2}")) }.orEmpty(),
                streams
            )
        }
    }
}

internal data class TvPlaylist(val id: String, val name: String, val address: String?, val channels: List<TvChannel>, val updatedAt: Long = 0) {
    val streamCount: Int get() = channels.sumOf { it.urls.size }
    fun json(): JSONObject = JSONObject().put("id", id).put("name", name).put("address", address)
        .put("channels", JSONArray().apply { channels.forEach { put(it.json()) } }).put("updatedAt", updatedAt)
}

internal data class TvImport(val channels: List<TvChannel>, val skipped: Int)

internal object M3uParser {
    const val MAX_BYTES = 4 * 1024 * 1024
    const val MAX_CHANNELS = 5000
    private val attribute = Regex("([\\w-]+)=(?:\"([^\"]*)\"|'([^']*)')")

    fun isHls(text: String): Boolean = text.lineSequence().any {
        it.trim().startsWith("#EXT-X-TARGETDURATION:") || it.trim().startsWith("#EXT-X-STREAM-INF:")
    }

    fun parse(text: String, base: String? = null): TvImport {
        if (text.toByteArray(Charsets.UTF_8).size > MAX_BYTES) throw TvPlaylistLimitException()
        if (isHls(text)) throw IOException("Expected a channel playlist")
        val lines = text.removePrefix("\uFEFF").lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        if (lines.none { it.startsWith("#EXTM3U", true) || it.startsWith("#EXTINF:", true) } &&
            lines.filterNot { it.startsWith('#') }.any { it.toHttpUrlOrNull() == null }
        ) {
            throw IOException("Not an M3U playlist")
        }
        val channels = linkedMapOf<String, TvChannel>()
        var title = ""
        var group = ""
        var logo: String? = null
        var tvgId = ""
        var skipped = 0
        for (raw in text.removePrefix("\uFEFF").lineSequence()) {
            val line = raw.trim()
            if (line.startsWith("#EXTINF:", true)) {
                var quoted: Char? = null
                val comma = line.indexOfFirst { char ->
                    if (char == '\'' || char == '"') quoted = if (quoted == char) null else quoted ?: char
                    char == ',' && quoted == null
                }
                val fields = attribute.findAll(if (comma < 0) line else line.substring(0, comma))
                    .associate { it.groupValues[1].lowercase(Locale.ROOT) to it.groupValues[2].ifEmpty { it.groupValues[3] } }
                title = (if (comma < 0) "" else line.substring(comma + 1)).trim().ifEmpty { fields["tvg-name"].orEmpty() }.take(256)
                group = fields["group-title"].orEmpty().take(128)
                logo = fields["tvg-logo"]?.let { tvUrl(it, base) }
                tvgId = fields["tvg-id"].orEmpty().take(256)
            } else if (line.startsWith("#EXTGRP:", true)) {
                group = line.substringAfter(':').trim().take(128)
            } else if (line.isNotEmpty() && !line.startsWith('#')) {
                val url = tvUrl(line, base)
                if (url == null) {
                    skipped++
                } else {
                    if (channels.size >= MAX_CHANNELS && url !in channels) throw TvPlaylistLimitException()
                    channels.putIfAbsent(url, TvChannel(title.ifEmpty { url.toHttpUrlOrNull()!!.host }, url, group, logo, tvgId = tvgId))
                }
                title = ""
                group = ""
                logo = null
                tvgId = ""
            }
        }
        if (channels.isEmpty()) throw IOException("No supported channels")
        return TvImport(channels.values.toList(), skipped)
    }
}
