package org.schabi.newpipe.settings.export

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import java.io.InputStream
import java.net.URI

internal data class LibreTubeVideo(val id: String, val title: String, val uploader: String = "", val duration: Long = -1, val thumbnail: String? = null) {
    val url: String get() = "https://www.youtube.com/watch?v=$id"
}
internal data class LibreTubeChannel(val id: String, val name: String) {
    val url: String get() = "https://www.youtube.com/channel/$id"
}
internal data class LibreTubeGroup(val name: String, val channels: List<String>, val order: Long)
internal data class LibreTubePlaylist(val name: String, val videos: List<LibreTubeVideo>)
internal data class LibreTubeBookmark(val id: String, val name: String, val count: Long)
internal data class LibreTubeBackup(
    val subscriptions: List<LibreTubeChannel>,
    val groups: List<LibreTubeGroup>,
    val history: List<LibreTubeVideo>,
    val positions: Map<String, Long>,
    val playlists: List<LibreTubePlaylist>,
    val bookmarks: List<LibreTubeBookmark>,
    val skipped: Int
)

internal object LibreTubeBackupParser {
    const val MAX_BYTES = 16 * 1024 * 1024
    private val videoPattern = Regex("[A-Za-z0-9_-]{11}")
    private val channelPattern = Regex("UC[A-Za-z0-9_-]{22}")
    private val playlistPattern = Regex("[A-Za-z0-9_-]{2,150}")

    fun parse(input: InputStream): LibreTubeBackup {
        val bytes = input.readTakeoutBytes(MAX_BYTES)
        require(bytes.size <= MAX_BYTES) { "Backup exceeds 16 MiB" }
        return parse(bytes.toString(Charsets.UTF_8))
    }

    fun parse(text: String): LibreTubeBackup {
        require(text.length <= MAX_BYTES) { "Backup exceeds 16 MiB" }
        var depth = 0
        var quoted = false
        var escaped = false
        for (c in text) {
            if (quoted) {
                if (escaped) {
                    escaped = false
                } else if (c == '\\') {
                    escaped = true
                } else if (c == '"') {
                    quoted = false
                }
            } else {
                when (c) {
                    '"' -> quoted = true

                    '{', '[' -> {
                        depth++
                        require(depth <= 32) { "Backup nesting is too deep" }
                    }

                    '}', ']' -> depth--
                }
            }
        }
        val root = JsonParser.`object`().from(text.removePrefix("\uFEFF"))
        require(root.getString("format", "Piped") in setOf("Piped", "LibreTube") && root.getInt("version", 1) == 1) { "Unsupported backup format/version" }
        val fields = listOf("subscriptions", "localSubscriptions", "groups", "channelGroups", "watchHistory", "watchPositions", "localPlaylists", "playlists", "playlistBookmarks")
        require(fields.any { root[it] is JsonArray }) { "Select a LibreTube JSON backup" }
        var skipped = 0
        var entries = 0
        fun array(obj: JsonObject, vararg names: String): List<Any?> {
            val key = names.firstOrNull { obj[it] != null } ?: return emptyList()
            val value = obj[key]
            require(value is JsonArray) { "Invalid backup category: $key" }
            entries += value.size
            require(entries <= 50_000) { "Backup contains more than 50,000 entries" }
            return value.toList()
        }
        fun string(obj: JsonObject, key: String): String = (obj[key] as? String)?.trim()?.take(4096).orEmpty()
        fun video(value: Any?): LibreTubeVideo? {
            val obj = value as? JsonObject
            val id = identifier(if (value is String) value else obj?.let { string(it, "videoId") }, "video")
            if (id == null) {
                skipped++
                return null
            }
            return LibreTubeVideo(
                id,
                obj?.let { string(it, "title") }?.ifEmpty { id } ?: id,
                obj?.let { string(it, "uploader") }.orEmpty(),
                ((obj?.get("duration") as? Number)?.toLong() ?: -1).coerceAtLeast(-1),
                obj?.let { image(string(it, "thumbnailUrl")) }
            )
        }
        val subscriptions = array(root, "subscriptions", "localSubscriptions").mapNotNull { value ->
            val obj = value as? JsonObject
            val id = identifier(if (value is String) value else obj?.let { string(it, "channelId").ifEmpty { string(it, "url") } }, "channel")
            if (id == null) {
                skipped++
                null
            } else {
                LibreTubeChannel(id, obj?.let { string(it, "name") }?.ifEmpty { id } ?: id)
            }
        }.distinctBy { it.id }
        val groups = array(root, "groups", "channelGroups").mapNotNull { value ->
            val obj = value as? JsonObject
            val name = obj?.let { string(it, "groupName").ifEmpty { string(it, "name") } }.orEmpty().take(200)
            if (obj == null || name.isEmpty()) {
                skipped++
                null
            } else {
                LibreTubeGroup(
                    name,
                    array(obj, "channels").mapNotNull {
                        identifier(it as? String, "channel") ?: run {
                            skipped++
                            null
                        }
                    }.distinct(),
                    (obj["index"] as? Number)?.toLong() ?: 0
                )
            }
        }.sortedBy { it.order }
        val history = array(root, "watchHistory").mapNotNull(::video).distinctBy { it.id }
        val positions = linkedMapOf<String, Long>()
        array(root, "watchPositions").forEach { value ->
            val obj = value as? JsonObject
            val id = obj?.let { identifier(string(it, "videoId"), "video") }
            val position = (obj?.get("position") as? Number)?.toLong()
            if (id == null || position == null || position < 0 || position > 365L * 24 * 60 * 60 * 1000) skipped++ else positions.putIfAbsent(id, position)
        }
        // LibreTube exports playlists twice. Prefer the richer local representation, never both.
        val local = array(root, "localPlaylists")
        val playlists = (if (local.isNotEmpty()) local else array(root, "playlists")).mapNotNull { value ->
            val obj = value as? JsonObject
            val metadata = obj?.get("playlist") as? JsonObject ?: obj
            val name = metadata?.let { string(it, "name") }.orEmpty().take(200)
            if (obj == null || name.isEmpty()) {
                skipped++
                null
            } else {
                LibreTubePlaylist(name, array(obj, "videos").mapNotNull(::video))
            }
        }
        val bookmarks = array(root, "playlistBookmarks").mapNotNull { value ->
            val obj = value as? JsonObject
            val id = obj?.let { identifier(string(it, "playlistId"), "playlist") }
            if (obj == null || id == null) {
                skipped++
                null
            } else {
                LibreTubeBookmark(id, string(obj, "playlistName").ifEmpty { id }, ((obj["videos"] as? Number)?.toLong() ?: 0).coerceAtLeast(0))
            }
        }.distinctBy { it.id }
        return LibreTubeBackup(subscriptions, groups, history, positions, playlists, bookmarks, skipped)
    }

    private fun identifier(value: String?, kind: String): String? {
        val text = value?.trim() ?: return null
        val pattern = when (kind) {
            "video" -> videoPattern
            "channel" -> channelPattern
            else -> playlistPattern
        }
        if (pattern.matches(text)) return text
        return runCatching {
            val uri = URI(text)
            require(uri.userInfo == null && (uri.host == null || uri.host.lowercase() in setOf("youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be")))
            val id = when (kind) {
                "channel" -> uri.path?.removePrefix("/channel/")
                "video" -> if (uri.host == "youtu.be") uri.path.removePrefix("/") else uri.rawQuery?.split('&')?.firstOrNull { it.startsWith("v=") }?.removePrefix("v=")
                else -> uri.rawQuery?.split('&')?.firstOrNull { it.startsWith("list=") }?.removePrefix("list=")
            }
            id?.takeIf(pattern::matches)
        }.getOrNull()
    }

    private fun image(value: String): String? = runCatching {
        val uri = URI(value)
        val host = uri.host.orEmpty().lowercase()
        value.takeIf { uri.scheme == "https" && uri.userInfo == null && (host == "ytimg.com" || host.endsWith(".ytimg.com")) }
    }.getOrNull()
}
