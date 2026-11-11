package org.schabi.newpipe.local.tv

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal data class TvLibrary(val playlists: List<TvPlaylist> = emptyList(), val favorites: Set<String> = emptySet())

/** Private, profile-scoped cached playlists. File writes must run off the UI thread. */
internal class TvPlaylistStore(context: Context, profile: String) {
    private val file = AtomicFile(
        File(context.filesDir, "live-tv/${MessageDigest.getInstance("SHA-256").digest(profile.toByteArray()).joinToString("") { "%02x".format(it) }}.json")
    )

    fun read(): TvLibrary = synchronized(lock) {
        if (!file.baseFile.exists()) return TvLibrary()
        return try {
            if (file.baseFile.length() > MAX_LIBRARY_BYTES) throw TvPlaylistLimitException()
            val data = file.openRead().use { it.readBytes() }
            if (data.size > MAX_LIBRARY_BYTES) throw TvPlaylistLimitException()
            val root = JSONObject(data.toString(Charsets.UTF_8))
            val sources = root.optJSONArray("playlists") ?: JSONArray()
            if (sources.length() > 10) throw TvPlaylistLimitException()
            val playlists = (0 until sources.length()).mapNotNull { index ->
                val source = sources.optJSONObject(index) ?: return@mapNotNull null
                val rows = source.optJSONArray("channels") ?: JSONArray()
                val channels = (0 until rows.length()).mapNotNull { rows.optJSONObject(it)?.let(TvChannel::parse) }
                TvPlaylist(source.getString("id"), source.getString("name"), source.optString("address").let { tvUrl(it) }, channels)
            }
            if (playlists.sumOf { it.channels.size } > M3uParser.MAX_CHANNELS) throw TvPlaylistLimitException()
            val favorites = root.optJSONArray("favorites") ?: JSONArray()
            TvLibrary(playlists, (0 until favorites.length()).mapNotNull { tvUrl(favorites.optString(it)) }.toSet())
        } catch (error: JSONException) {
            throw IOException("Invalid TV library", error)
        }
    }

    fun write(library: TvLibrary): Unit = synchronized(lock) {
        if (library.playlists.size > 10 || library.playlists.sumOf { it.channels.size } > M3uParser.MAX_CHANNELS) throw TvPlaylistLimitException()
        val data = JSONObject().put("playlists", JSONArray().apply { library.playlists.forEach { put(it.json()) } })
            .put("favorites", JSONArray(library.favorites.toList())).toString().toByteArray(Charsets.UTF_8)
        if (data.size > MAX_LIBRARY_BYTES) throw TvPlaylistLimitException()
        file.baseFile.parentFile?.mkdirs()
        val output = file.startWrite()
        try {
            output.write(data)
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
    }

    fun delete(): Unit = synchronized(lock) { file.delete() }

    companion object {
        private val lock = Any()
        const val MAX_LIBRARY_BYTES = 8 * 1024 * 1024
    }
}
