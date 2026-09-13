package org.schabi.newpipe.settings.export

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import java.io.File
import java.io.InputStream
import java.net.URI
import java.net.URLDecoder
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipFile
import org.jsoup.Jsoup
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeStreamLinkHandlerFactory

internal data class TakeoutVideo(val url: String, val title: String)
internal data class TakeoutWatch(val video: TakeoutVideo, val timestamp: Long)
internal data class TakeoutPlaylist(val name: String, val videos: List<TakeoutVideo>, val url: String? = null)
internal data class TakeoutSubscription(val url: String, val name: String)
internal data class TakeoutSearch(val query: String, val timestamp: Long)
internal data class TakeoutData(
    val playlists: List<TakeoutPlaylist>,
    val history: List<TakeoutWatch>,
    val skipped: Int,
    val subscriptions: List<TakeoutSubscription> = emptyList(),
    val searches: List<TakeoutSearch> = emptyList()
)

internal object TakeoutParser {
    private const val MAX_FILE = 32 * 1024 * 1024
    private const val MAX_TOTAL = 64 * 1024 * 1024
    private const val MAX_RECORDS = 100000

    private data class PlaylistMetadata(val name: String, val url: String?)
    private data class PlaylistFile(val stem: String, val playlist: TakeoutPlaylist)

    fun parse(file: File, name: String): TakeoutData {
        val playlistFiles = mutableListOf<PlaylistFile>()
        val metadata = mutableListOf<PlaylistMetadata>()
        val history = mutableListOf<TakeoutWatch>()
        val searches = mutableListOf<TakeoutSearch>()
        val subscriptions = mutableListOf<TakeoutSubscription>()
        var skipped = 0
        var bytes = 0
        fun read(name: String, input: InputStream) {
            check(!Thread.currentThread().isInterrupted) { "Import cancelled" }
            val data = input.readTakeoutBytes(MAX_FILE)
            require(data.size <= MAX_FILE) { "Takeout file exceeds 32 MiB; select a smaller export." }
            bytes += data.size
            require(bytes <= MAX_TOTAL) { "Selected Takeout data exceeds 64 MiB." }
            val text = data.toString(Charsets.UTF_8).removePrefix("\uFEFF")
            when (name.substringAfterLast('.').lowercase(Locale.ROOT)) {
                "csv" -> {
                    val rows = csv(text)
                    val headerIndex = rows.indexOfFirst { row -> row.any { it.trim().equals("Video ID", true) } }
                    val metadataRows = if (headerIndex < 0) rows else rows.take(headerIndex)
                    val metaHeader = metadataRows.indexOfFirst { row ->
                        row.any { it.trim().equals("Playlist ID", true) || it.trim().equals("Playlist URL", true) || it.trim().equals("Playlist Title", true) }
                    }
                    val fileMetadata = if (metaHeader >= 0) {
                        val header = metadataRows[metaHeader].map { it.trim().lowercase(Locale.ROOT) }
                        metadataRows.drop(metaHeader + 1).filter { it.any(String::isNotBlank) }.mapNotNull { row ->
                            val title = field(row, header, "playlist title", "title")
                            val url = playlistUrl(field(row, header, "playlist url", "playlist id"))
                            if (title.isBlank() && url == null) null else PlaylistMetadata(title.ifBlank { name.substringAfterLast('/').substringBeforeLast('.') }, url)
                        }
                    } else {
                        emptyList()
                    }
                    if (headerIndex >= 0) {
                        val header = rows[headerIndex].map { it.trim().lowercase(Locale.ROOT) }
                        val videos = rows.drop(headerIndex + 1).filter { it.any(String::isNotBlank) }.mapNotNull { row ->
                            video(field(row, header, "video id"), field(row, header, "title", "video title")).also { if (it == null) skipped++ }
                        }
                        val stem = name.substringAfterLast('/').substringBeforeLast('.').removeSuffix("-videos")
                        val info = fileMetadata.firstOrNull()
                        // Older exports put a plain Title column before the video section.
                        val titleHeader = metadataRows.indexOfFirst { row -> row.any { it.trim().equals("Title", true) } }
                        val oldTitle = if (titleHeader >= 0) {
                            field(metadataRows.getOrNull(titleHeader + 1).orEmpty(), metadataRows[titleHeader].map { it.trim().lowercase(Locale.ROOT) }, "title")
                        } else {
                            ""
                        }
                        playlistFiles.add(PlaylistFile(stem, TakeoutPlaylist(info?.name ?: oldTitle.ifBlank { stem }, videos, info?.url)))
                    } else if (metaHeader >= 0) {
                        metadata.addAll(fileMetadata)
                    } else {
                        val headerRow = rows.indexOfFirst { row -> row.any { it.trim().equals("Channel ID", true) || it.trim().equals("Channel URL", true) } }
                        if (headerRow >= 0) {
                            val header = rows[headerRow].map { it.trim().lowercase(Locale.ROOT) }
                            for (row in rows.drop(headerRow + 1).filter { it.any(String::isNotBlank) }) {
                                val url = channelUrl(field(row, header, "channel id")) ?: channelUrl(field(row, header, "channel url"))
                                if (url == null) skipped++ else subscriptions.add(TakeoutSubscription(url, field(row, header, "channel title", "title").ifBlank { url.substringAfterLast('/') }.take(1000)))
                            }
                        }
                    }
                }

                "json" -> {
                    for (entry in JsonParser.array().from(text)) {
                        val row = entry as? JsonObject
                        val time = runCatching { Instant.parse(row?.getString("time", "")).toEpochMilli() }.getOrNull()
                        val link = row?.getString("titleUrl", "").orEmpty()
                        val video = video(link, row?.getString("title", "").orEmpty().removePrefix("Watched "))
                        val query = searchQuery(link)
                        when {
                            time != null && video != null -> history.add(TakeoutWatch(video, time))
                            time != null && query != null -> searches.add(TakeoutSearch(query, time))
                            else -> skipped++
                        }
                    }
                }

                "html" -> {
                    for (cell in Jsoup.parse(text).select("div.content-cell")) {
                        val link = cell.select("a[href]").firstOrNull { video(it.attr("href"), "") != null || searchQuery(it.attr("href")) != null }
                        if (link == null) {
                            if (cell.text().isNotBlank()) skipped++
                            continue
                        }
                        val video = video(link.attr("href"), link.text())
                        val query = searchQuery(link.attr("href"))
                        val time = cell.wholeText().lines().mapNotNull(::htmlTime).firstOrNull()
                        when {
                            time != null && video != null -> history.add(TakeoutWatch(video, time))
                            time != null && query != null -> searches.add(TakeoutSearch(query, time))
                            else -> skipped++
                        }
                    }
                }
            }
            require(history.size + searches.size + subscriptions.size + metadata.size + playlistFiles.sumOf { it.playlist.videos.size + 1 } <= MAX_RECORDS) {
                "Takeout export exceeds 100,000 records."
            }
        }
        val zip = file.inputStream().use { it.read() == 'P'.code && it.read() == 'K'.code }
        if (zip) {
            ZipFile(file).use { archive ->
                val entries = archive.entries()
                var count = 0
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    require(++count <= 10000) { "Too many files in Takeout archive." }
                    val path = entry.name.lowercase(Locale.ROOT)
                    val parts = path.split('/')
                    val youtube = parts.any { it == "youtube" || it == "youtube and youtube music" || it == "youtube music" }
                    val extracted = parts.size == 1 || parts.first() in listOf("playlists", "subscriptions", "history")
                    val supported = path.endsWith(".csv") && (parts.contains("playlists") || path.endsWith("-videos.csv") || path.endsWith("subscriptions.csv")) ||
                        listOf("watch-history.json", "watch-history.html", "search-history.json", "search-history.html").any(path::endsWith)
                    if (!entry.isDirectory && (youtube || extracted) && supported) archive.getInputStream(entry).use { read(entry.name, it) }
                }
            }
        } else {
            file.inputStream().use { read(name, it) }
        }
        // Match separate metadata by ID first, then by an unambiguous title. Archive order is irrelevant.
        val byId = metadata.groupBy { it.url?.substringAfter("list=") }
        val byName = metadata.groupBy { it.name }
        val used = mutableSetOf<PlaylistMetadata>()
        val playlists = playlistFiles.map { entry ->
            val info = byId[entry.playlist.url?.substringAfter("list=") ?: entry.stem]?.singleOrNull() ?: byName[entry.stem]?.singleOrNull()
            if (info != null) used.add(info)
            entry.playlist.copy(name = info?.name ?: entry.playlist.name, url = entry.playlist.url ?: info?.url)
        }.toMutableList()
        playlists.addAll(metadata.filterNot { it in used }.map { TakeoutPlaylist(it.name, emptyList(), it.url) })
        require(playlists.isNotEmpty() || history.isNotEmpty() || subscriptions.isNotEmpty() || searches.isNotEmpty()) {
            "No supported YouTube playlists, subscriptions or history found. Select Takeout ZIP, CSV or history JSON/HTML. Export history as JSON for reliable dates."
        }
        return TakeoutData(playlists, history, skipped, subscriptions, searches)
    }

    private fun field(row: List<String>, header: List<String>, vararg names: String): String = names.firstNotNullOfOrNull { name ->
        row.getOrNull(header.indexOf(name))?.trim()?.takeIf { it.isNotEmpty() }
    }.orEmpty()

    private fun youtubeUri(value: String): URI? = runCatching {
        URI(value).takeIf { it.scheme in listOf("http", "https") && it.host?.lowercase(Locale.ROOT) in listOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com") }
    }.getOrNull()

    private fun parameter(uri: URI, key: String): String? = runCatching {
        uri.rawQuery?.split('&')?.firstOrNull { it.substringBefore('=') == key }?.substringAfter('=')?.let { URLDecoder.decode(it, "UTF-8") }
    }.getOrNull()

    private fun playlistUrl(value: String): String? {
        val id = if (value.matches(Regex("[A-Za-z0-9_-]{10,100}"))) value else youtubeUri(value)?.let { parameter(it, "list") }
        return id?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{10,100}")) }?.let { "https://www.youtube.com/playlist?list=$it" }
    }

    private fun channelUrl(value: String): String? {
        val id = if (value.startsWith("UC")) value else youtubeUri(value)?.path?.removePrefix("/channel/")
        return id?.takeIf { it.matches(Regex("UC[A-Za-z0-9_-]{22}")) }?.let { "https://www.youtube.com/channel/$it" }
    }

    private fun searchQuery(value: String): String? = youtubeUri(value)?.takeIf { it.path == "/results" }?.let {
        parameter(it, "search_query")?.trim()?.takeIf { query -> query.isNotEmpty() && query.length <= 10000 }
    }

    private fun video(value: String, title: String): TakeoutVideo? = runCatching {
        val factory = YoutubeStreamLinkHandlerFactory.getInstance()
        val id = if (value.matches(Regex("[A-Za-z0-9_-]{11}"))) value else factory.getId(value)
        TakeoutVideo(factory.getUrl(id), title.ifBlank { id }.take(1000))
    }.getOrNull()

    private fun htmlTime(value: String): Long? {
        val text = value.trim().replace('\u202f', ' ').replace('\u00a0', ' ')
        return runCatching { Instant.parse(text).toEpochMilli() }.getOrNull() ?: listOf(
            "MMM d, uuuu, h:mm:ss a z",
            "MMM d, uuuu, HH:mm:ss z",
            "d MMM uuuu, HH:mm:ss z"
        ).firstNotNullOfOrNull { pattern ->
            runCatching { ZonedDateTime.parse(text, DateTimeFormatter.ofPattern(pattern, Locale.US)).toInstant().toEpochMilli() }.getOrNull()
        }
    }

    /** RFC 4180 quoted fields, embedded newlines, doubled quotes, CRLF and empty columns. */
    internal fun csv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val value = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val char = text[i++]
            when {
                char == '"' && quoted && i < text.length && text[i] == '"' -> {
                    value.append('"')
                    i++
                }

                char == '"' -> quoted = !quoted

                char == ',' && !quoted -> {
                    row.add(value.toString())
                    require(row.size <= 1000) { "Too many CSV columns." }
                    value.setLength(0)
                }

                (char == '\n' || char == '\r') && !quoted -> {
                    row.add(value.toString())
                    rows.add(row)
                    require(rows.size <= MAX_RECORDS + 100) { "Too many CSV rows." }
                    row = mutableListOf()
                    value.setLength(0)
                    if (char == '\r' && i < text.length && text[i] == '\n') i++
                }

                else -> value.append(char)
            }
        }
        require(!quoted) { "Unclosed quoted CSV field." }
        if (row.isNotEmpty() || value.isNotEmpty()) {
            row.add(value.toString())
            rows.add(row)
        }
        return rows
    }
}
