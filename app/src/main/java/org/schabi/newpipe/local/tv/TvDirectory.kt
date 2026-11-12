package org.schabi.newpipe.local.tv

import android.content.Context
import java.io.IOException
import java.text.Collator
import java.util.Locale
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray

internal enum class TvDirectorySource(val key: String, val title: String) {
    IPTV_ORG("iptv-org", "IPTV-org"),
    FREE_TV("free-tv", "Free-TV")
}

internal data class TvDirectoryCountry(val code: String, val name: String, val iptv: Boolean, val free: String) {
    fun label(locale: Locale = Locale.getDefault()): String = Locale.Builder().setRegion(code).build().getDisplayCountry(locale).takeIf { it != code } ?: name
    fun supports(source: TvDirectorySource): Boolean = if (source == TvDirectorySource.IPTV_ORG) iptv else free.isNotEmpty()
    fun address(source: TvDirectorySource): String {
        require(supports(source))
        return if (source == TvDirectorySource.IPTV_ORG) {
            "https://iptv-org.github.io/iptv/countries/${code.lowercase(Locale.ROOT)}.m3u"
        } else {
            "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlists/playlist_$free.m3u8"
        }
    }
}

internal data class TvDirectorySelection(val source: TvDirectorySource, val country: TvDirectoryCountry) {
    val id: String get() = "directory:${source.key}:${country.code}"
    val name: String get() = "${source.title} · ${country.label()}"
    val address: String get() = country.address(source)
}

/** Country lists are bundled; playlists are fetched only after choosing a country. */
internal object TvDirectory {
    const val REFRESH_INTERVAL = 24 * 60 * 60 * 1000L
    private var catalog: List<TvDirectoryCountry>? = null

    @Synchronized
    fun countries(context: Context): List<TvDirectoryCountry> {
        catalog?.let { return it }
        val json = JSONArray(context.assets.open("live-tv/countries.json").bufferedReader().use { it.readText() })
        return (0 until json.length()).map { index ->
            val row = json.getJSONObject(index)
            val code = row.getString("code")
            val free = row.optString("free")
            require(code.matches(Regex("[A-Z]{2}")) && free.matches(Regex("[a-z_]*")))
            TvDirectoryCountry(code, row.getString("name"), row.getBoolean("iptv"), free)
        }.also { catalog = it }
    }

    fun choices(context: Context, source: TvDirectorySource): List<TvDirectoryCountry> = countries(context)
        .filter { it.supports(source) }.sortedWith(compareBy(Collator.getInstance()) { it.label() })

    fun selection(context: Context, playlist: TvPlaylist): TvDirectorySelection? {
        val parts = playlist.id.split(':')
        if (parts.size != 3 || parts[0] != "directory") return null
        val source = TvDirectorySource.entries.firstOrNull { it.key == parts[1] } ?: return null
        val country = countries(context).firstOrNull { it.code == parts[2] && it.supports(source) } ?: return null
        return TvDirectorySelection(source, country).takeIf { it.address == playlist.address }
    }

    fun stale(playlist: TvPlaylist, now: Long): Boolean = playlist.updatedAt <= 0 || now < playlist.updatedAt || now - playlist.updatedAt >= REFRESH_INTERVAL

    suspend fun load(selection: TvDirectorySelection): Pair<TvPlaylist, Int> {
        val result = TvPlaylistLoader.load(selection.address, selection.name).second
        return prepare(selection, result)
    }

    fun prepare(selection: TvDirectorySelection, result: TvImport, now: Long = System.currentTimeMillis()): Pair<TvPlaylist, Int> {
        val supported = result.channels.filter { channel ->
            val host = channel.url.toHttpUrlOrNull()?.host.orEmpty().removePrefix("www.")
            host !in setOf("youtube.com", "youtu.be", "m.youtube.com", "dailymotion.com", "dai.ly")
        }.map {
            it.copy(name = directoryName(it.name).ifBlank { it.name }, country = selection.country.code, tvgId = directoryId(it.tvgId))
        }
        if (supported.isEmpty()) throw IOException("No supported channels in this directory")
        val channels = combineTvChannels(supported)
        return TvPlaylist(selection.id, selection.name, selection.address, channels, now) to
            (result.skipped + result.channels.size - supported.size)
    }
}

private fun directoryName(name: String): String = name.replace(Regex("\\s*\\(\\d{3,4}p\\)", RegexOption.IGNORE_CASE), "")
    .replace(Regex("\\s*\\[Not 24/7]", RegexOption.IGNORE_CASE), "").replace("Ⓖ", "").replace("Ⓢ", "").trim()

private fun directoryId(id: String): String = id.replace(Regex("@(SD|HD|FHD|UHD|4K|8K)$", RegexOption.IGNORE_CASE), "").lowercase(Locale.ROOT)

internal fun TvChannel.directoryIdentity(): String = when {
    country.isEmpty() -> url
    tvgId.isNotEmpty() -> "channel:$tvgId"
    else -> "$country:${name.lowercase(Locale.ROOT)}"
}

/** Merge country-scoped directory entries, while retaining custom channels with distinct URLs. */
internal fun combineTvChannels(channels: List<TvChannel>): List<TvChannel> {
    val output = linkedMapOf<String, TvChannel>()
    val names = mutableMapOf<String, String>()
    val seenUrls = mutableSetOf<String>()
    for (channel in channels) {
        val nameKey = "${channel.country}:${channel.name.lowercase(Locale.ROOT)}"
        val named = names[nameKey]?.let(output::get)
        val key = if (channel.country.isNotEmpty() && named != null && (channel.tvgId.isEmpty() || named.tvgId.isEmpty() || named.tvgId == channel.tvgId)) {
            requireNotNull(names[nameKey])
        } else {
            channel.directoryIdentity()
        }
        val existing = output[key]
        val added = channel.urls.filter { seenUrls.add(it) }
        if (added.isEmpty() && existing == null) continue
        if (existing == null) {
            output[key] = channel.copy(url = added.first(), alternatives = added.drop(1).take(31))
            if (channel.country.isNotEmpty()) names.putIfAbsent(nameKey, key)
        } else {
            val urls = (existing.urls + added).distinct().take(32)
            output[key] = existing.copy(
                alternatives = urls.drop(1),
                logo = existing.logo ?: channel.logo,
                group = (existing.group.split(';') + channel.group.split(';')).map(String::trim).filter(String::isNotEmpty).distinct().joinToString("; ").take(128),
                tvgId = existing.tvgId.ifEmpty { channel.tvgId }
            )
            if (channel.country.isNotEmpty()) names.putIfAbsent(nameKey, key)
        }
    }
    return output.values.toList()
}

/** Carry favorites across directory refreshes even when a broadcaster replaces its stream URL. */
internal fun TvLibrary.withPlaylist(playlist: TvPlaylist): TvLibrary {
    val old = playlists.firstOrNull {
        it.id == playlist.id || if (playlist.address == null) it.address == null && it.name == playlist.name else it.address == playlist.address
    }
    val updated = playlist.copy(id = if (playlist.id.startsWith("directory:")) playlist.id else old?.id ?: playlist.id)
    val next = playlists.filterNot { it.id == updated.id || it.id == old?.id } + updated
    val validUrls = next.flatMap { it.channels }.flatMap { it.urls }.toSet()
    val saved = old?.channels.orEmpty().filter { it.urls.any(favorites::contains) }.map { it.directoryIdentity() }.toSet()
    val retained = favorites.intersect(validUrls) + updated.channels.filter { it.directoryIdentity() in saved }.flatMap { it.urls }
    return copy(playlists = next, favorites = retained)
}
