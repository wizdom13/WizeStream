/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings.export

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonParserException
import com.grack.nanojson.JsonWriter
import java.io.InputStream
import java.net.URI
import java.net.URLDecoder
import java.util.UUID

/** FreeTube .db exports are newline-delimited JSON, not SQLite databases. */
internal object FreeTubeBackup {
    const val MAX_BYTES = 16 * 1024 * 1024
    private const val MAX_ENTRIES = 50_000
    private val VIDEO = Regex("[A-Za-z0-9_-]{11}")
    private val CHANNEL = Regex("UC[A-Za-z0-9_-]{22}")

    fun parse(input: InputStream): LibreTubeBackup {
        val bytes = input.readTakeoutBytes(MAX_BYTES)
        require(bytes.size <= MAX_BYTES)
        return parse(bytes.toString(Charsets.UTF_8))
    }

    fun parse(text: String): LibreTubeBackup {
        require(text.length <= MAX_BYTES)
        val raw = text.removePrefix("\uFEFF").trim()
        require(raw.isNotEmpty())
        checkDepth(raw)
        val objects = if (raw.startsWith("[")) {
            JsonParser.array().from(raw).toList()
        } else {
            try {
                listOf(JsonParser.`object`().from(raw))
            } catch (_: JsonParserException) {
                raw.lineSequence().filter { it.isNotBlank() }.map { JsonParser.`object`().from(it) }.take(MAX_ENTRIES + 1).toList()
            }
        }
        require(objects.size <= MAX_ENTRIES)
        val records = linkedMapOf<String, JsonObject>()
        objects.forEachIndexed { index, value ->
            val row = value as? JsonObject ?: throw IllegalArgumentException("Invalid FreeTube record")
            val key = row.getString("_id", "").ifEmpty { "row:$index" }
            if (row.getBoolean("\$\$deleted", false)) records.remove(key) else records[key] = row
        }
        val subscriptions = linkedMapOf<String, LibreTubeChannel>()
        val groups = mutableListOf<LibreTubeGroup>()
        val playlists = mutableListOf<LibreTubePlaylist>()
        var skipped = 0
        var entries = objects.size
        var recognized = false
        fun count() {
            require(++entries <= MAX_ENTRIES)
        }
        fun channel(row: JsonObject): String? {
            count()
            val id = identifier(row.getString("id", row.getString("channelId", row.getString("url", ""))), true)
            if (id == null) {
                skipped++
                return null
            }
            val name = row.getString("name", row.getString("channelName", id)).trim().take(200).ifEmpty { id }
            subscriptions.putIfAbsent(id, LibreTubeChannel(id, name))
            return id
        }
        fun readRecord(row: JsonObject) {
            val channels = row["subscriptions"] as? JsonArray
            val videos = row["videos"] as? JsonArray
            if (channels != null) {
                recognized = true
                val ids = channels.mapNotNull { item ->
                    val value = item as? JsonObject
                    if (value == null) {
                        skipped++
                        null
                    } else {
                        channel(value)
                    }
                }.distinct()
                val name = row.getString("name", "").trim().take(180)
                if (name.isNotEmpty()) groups.add(LibreTubeGroup("FreeTube · $name", ids, groups.size.toLong()))
            } else if (videos != null && (row.containsKey("playlistName") || row.containsKey("name"))) {
                recognized = true
                val name = row.getString("playlistName", row.getString("name", "")).trim().take(200)
                if (name.isEmpty()) {
                    skipped++
                    return
                }
                val items = videos.mapNotNull { value ->
                    count()
                    val video = value as? JsonObject
                    val id = identifier(video?.let { it.getString("videoId", it.getString("url", "")) }, false)
                    if (id == null || video == null) {
                        skipped++
                        null
                    } else {
                        val duration = (video["lengthSeconds"] as? Number)?.toLong()
                            ?: (video["lengthSeconds"] as? String)?.toLongOrNull() ?: -1
                        LibreTubeVideo(id, video.getString("title", id).take(4096), video.getString("author", "").take(200), duration.coerceAtLeast(-1))
                    }
                }
                playlists.add(LibreTubePlaylist(name, items))
            } else if (row.containsKey("channelId")) {
                // Early FreeTube subscription exports stored one channel per JSON line.
                recognized = true
                channel(row)
            } else if (!row.keys.any { it.startsWith("\$\$") }) {
                skipped++
            }
        }
        records.values.forEach { row ->
            if (row.containsKey("profiles") || row.containsKey("playlists")) {
                listOf("profiles", "playlists").forEach { key ->
                    row.getArray(key)?.forEach { item ->
                        count()
                        readRecord(item as? JsonObject ?: throw IllegalArgumentException("Invalid FreeTube category"))
                    }
                }
            } else {
                readRecord(row)
            }
        }
        require(recognized) { "Select a FreeTube subscriptions or playlists export" }
        return LibreTubeBackup(subscriptions.values.toList(), groups, emptyList(), emptyMap(), playlists, emptyList(), skipped)
    }

    fun exportSubscriptions(name: String, subscriptions: List<LibreTubeChannel>): String {
        require(subscriptions.size <= MAX_ENTRIES)
        val row = JsonObject().apply {
            put("_id", "allChannels")
            put("name", name.take(200))
            put("bgColor", "#6750A4")
            put("textColor", "#FFFFFF")
            put(
                "subscriptions",
                JsonArray().apply {
                    subscriptions.forEach { channel ->
                        require(CHANNEL.matches(channel.id))
                        add(
                            JsonObject().apply {
                                put("id", channel.id)
                                put("name", channel.name.take(200))
                                put("thumbnail", "")
                            }
                        )
                    }
                }
            )
        }
        return bounded(JsonWriter.string().`object`(row).done() + "\n")
    }

    fun exportPlaylists(playlists: List<LibreTubePlaylist>, now: Long): String {
        require(playlists.size + playlists.sumOf { it.videos.size } <= MAX_ENTRIES)
        return bounded(
            playlists.joinToString("\n", postfix = if (playlists.isEmpty()) "" else "\n") { playlist ->
                val row = JsonObject().apply {
                    put("_id", "ft-playlist--${UUID.randomUUID()}")
                    put("playlistName", playlist.name.take(200))
                    put("description", "")
                    put(
                        "videos",
                        JsonArray().apply {
                            playlist.videos.forEach { video ->
                                require(VIDEO.matches(video.id))
                                add(
                                    JsonObject().apply {
                                        put("videoId", video.id)
                                        put("title", video.title.take(4096))
                                        put("author", video.uploader.take(200))
                                        put("authorId", "")
                                        put("lengthSeconds", video.duration.coerceAtLeast(0))
                                        put("timeAdded", now)
                                        put("playlistItemId", UUID.randomUUID().toString())
                                    }
                                )
                            }
                        }
                    )
                }
                JsonWriter.string().`object`(row).done()
            }
        )
    }

    internal fun identifier(value: String?, channel: Boolean): String? {
        val text = value?.trim() ?: return null
        val pattern = if (channel) CHANNEL else VIDEO
        if (pattern.matches(text)) return text
        return runCatching {
            val uri = URI(text)
            if (uri.scheme !in setOf("https", "http") || uri.rawUserInfo != null || uri.host !in setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be")) return null
            val id = if (channel) {
                uri.path.takeIf { it.startsWith("/channel/") }?.removePrefix("/channel/")?.substringBefore('/')
            } else if (uri.host == "youtu.be") {
                uri.path.removePrefix("/").substringBefore('/')
            } else if (uri.path == "/watch") {
                uri.rawQuery?.split('&')?.firstOrNull { it.substringBefore('=') == "v" }?.substringAfter('=')?.let { URLDecoder.decode(it, "UTF-8") }
            } else {
                uri.path.takeIf { listOf("/shorts/", "/live/", "/embed/").any(it::startsWith) }?.split('/')?.getOrNull(2)
            }
            id?.takeIf(pattern::matches)
        }.getOrNull()
    }

    private fun bounded(text: String): String = text.also { require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) }

    private fun checkDepth(text: String) {
        var depth = 0
        var quoted = false
        var escaped = false
        text.forEach { char ->
            if (quoted) {
                if (escaped) {
                    escaped = false
                } else if (char == '\\') {
                    escaped = true
                } else if (char == '"') {
                    quoted = false
                }
            } else {
                when (char) {
                    '"' -> quoted = true
                    '{', '[' -> require(++depth <= 32)
                    '}', ']' -> depth--
                }
            }
        }
    }
}
