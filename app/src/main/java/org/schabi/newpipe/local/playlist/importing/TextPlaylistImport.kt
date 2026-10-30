package org.schabi.newpipe.local.playlist.importing

import java.io.InputStream
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.concurrent.CancellationException
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity
import org.schabi.newpipe.database.stream.model.StreamEntity

internal data class TextVideoLink(val line: Int, val url: String)
internal data class TextPlaylistPreview(val links: List<TextVideoLink>, val invalidLines: List<Int>)
internal data class TextPlaylistResult(val added: Int, val duplicates: Int, val failedLines: List<Int>)

internal object TextPlaylistParser {
    const val MAX_BYTES = 1024 * 1024
    const val MAX_LINES = 2000
    private val idPattern = Regex("[A-Za-z0-9_-]{11}")

    fun parse(input: InputStream): TextPlaylistPreview {
        val bytes = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(bytes.size() + count <= MAX_BYTES) { "File too large" }
            bytes.write(buffer, 0, count)
        }
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString().removePrefix("\uFEFF")
        val links = mutableListOf<TextVideoLink>()
        val invalid = mutableListOf<Int>()
        text.lineSequence().forEachIndexed { index, raw ->
            require(index < MAX_LINES || raw.isEmpty() && index == MAX_LINES) { "Too many lines" }
            val line = raw.trim()
            if (line.isNotEmpty() && !line.startsWith("#")) {
                val url = canonicalUrl(line)
                if (url == null) invalid.add(index + 1) else links.add(TextVideoLink(index + 1, url))
            }
        }
        return TextPlaylistPreview(links, invalid)
    }

    fun canonicalUrl(text: String): String? = runCatching {
        val uri = URI(text)
        require(uri.scheme?.lowercase() in setOf("http", "https") && uri.userInfo == null && uri.port == -1)
        val host = uri.host?.lowercase()
        require(host in setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be", "www.youtu.be"))
        val path = uri.path.orEmpty()
        val id = when {
            host == "youtu.be" || host == "www.youtu.be" -> path.removePrefix("/")
            path == "/watch" -> uri.rawQuery?.split('&')?.firstOrNull { it.startsWith("v=") }?.removePrefix("v=")
            path.startsWith("/shorts/") -> path.removePrefix("/shorts/")
            path.startsWith("/live/") -> path.removePrefix("/live/")
            path.startsWith("/embed/") -> path.removePrefix("/embed/")
            else -> null
        }
        require(id != null && idPattern.matches(id))
        "https://www.youtube.com/watch?v=$id"
    }.getOrNull()
}

/** Resolve outside the transaction; save the successful rows together, preserving their order. */
internal class TextPlaylistImporter(
    private val database: AppDatabase,
    private val profileId: String,
    private val resolve: (String) -> StreamEntity,
    private val checkActive: () -> Unit = {},
    private val progress: (Int, Int) -> Unit = { _, _ -> }
) {
    fun import(preview: TextPlaylistPreview, playlistId: Long?, name: String, skipDuplicates: Boolean): TextPlaylistResult {
        require(playlistId != null || name.isNotBlank() && name.length <= 200)
        checkActive()
        if (playlistId != null) require(database.playlistDAO().getPlaylistDirectForProfile(profileId, playlistId) != null)
        val known = if (playlistId == null || !skipDuplicates) {
            mutableSetOf()
        } else {
            database.playlistStreamDAO()
                .getOrderedStreamsDirectForProfile(profileId, playlistId).filter { it.serviceId == 0 }.map { it.url }.toMutableSet()
        }
        val resolved = mutableMapOf<String, StreamEntity?>()
        val pending = mutableListOf<StreamEntity>()
        val failures = mutableListOf<Int>()
        var duplicates = 0
        preview.links.forEachIndexed { index, link ->
            checkActive()
            if (skipDuplicates && !known.add(link.url)) {
                duplicates++
            } else {
                if (!resolved.containsKey(link.url)) {
                    resolved[link.url] = try {
                        database.streamDAO().getStreamDirect(0, link.url) ?: resolve(link.url).also {
                            require(it.serviceId == 0 && TextPlaylistParser.canonicalUrl(it.url) == link.url)
                        }.copy(uid = 0, url = link.url)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        null
                    }
                }
                val stream = resolved[link.url]
                if (stream == null) failures.add(link.line) else pending.add(stream)
            }
            checkActive()
            progress(index + 1, preview.links.size)
        }
        var added = 0
        database.runInTransaction {
            checkActive()
            val existing = playlistId?.let { requireNotNull(database.playlistDAO().getPlaylistDirectForProfile(profileId, it)) }
            val current = existing?.let { database.playlistStreamDAO().getOrderedStreamsDirectForProfile(profileId, it.uid) }.orEmpty()
            val currentUrls = current.filter { it.serviceId == 0 }.map { it.url }.toMutableSet()
            val rows = pending.filter {
                if (skipDuplicates && !currentUrls.add(it.url)) {
                    duplicates++
                    false
                } else {
                    true
                }
            }
            if (rows.isNotEmpty()) {
                val playlist = existing ?: PlaylistEntity(
                    name = name.trim(),
                    isThumbnailPermanent = false,
                    thumbnailStreamId = PlaylistEntity.DEFAULT_THUMBNAIL_ID,
                    displayIndex = (database.playlistDAO().getAllDirectForProfile(profileId).maxOfOrNull { it.displayIndex } ?: -1) + 1,
                    profileId = profileId
                ).also { it.uid = database.playlistDAO().insert(it) }
                var next = database.playlistStreamDAO().getMaximumIndexDirect(playlist.uid) + 1
                rows.forEach { stream ->
                    checkActive()
                    val streamId = database.streamDAO().getStreamDirect(0, stream.url)?.uid ?: database.streamDAO().insert(stream.copy(uid = 0))
                    database.playlistStreamDAO().insert(PlaylistStreamEntity(playlist.uid, streamId, next++))
                    if (current.isEmpty() && added == 0 && !playlist.isThumbnailPermanent) {
                        playlist.thumbnailStreamId = streamId
                        database.playlistDAO().updateForProfile(profileId, playlist)
                    }
                    added++
                }
            }
            checkActive()
        }
        return TextPlaylistResult(added, duplicates, failures)
    }
}
