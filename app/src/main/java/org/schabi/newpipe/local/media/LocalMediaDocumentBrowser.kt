/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.media

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.util.ArrayDeque

data class LocalMediaDocumentLocation(
    val rootUri: String,
    val path: List<String> = emptyList()
)

data class LocalMediaDocumentEntry(
    val location: LocalMediaDocumentLocation,
    val contentUri: String,
    val name: String,
    val mimeType: String,
    val isDirectory: Boolean,
    val isRoot: Boolean = false,
    val isAvailable: Boolean = true,
    val sizeBytes: Long = 0L,
    val lastModified: Long = 0L
) {
    val isVideo: Boolean
        get() = mimeType.startsWith("video/")
}

class LocalMediaDocumentBrowser(private val context: Context) {
    fun roots(rootUris: Set<String>): List<LocalMediaDocumentEntry> = rootUris.map { rootUri ->
        val document = runCatching {
            DocumentFile.fromTreeUri(context, Uri.parse(rootUri))
        }.getOrNull()
        val available = runCatching {
            document?.exists() == true && document.canRead() && document.isDirectory
        }.getOrDefault(false)
        LocalMediaDocumentEntry(
            location = LocalMediaDocumentLocation(rootUri),
            contentUri = rootUri,
            name = document?.name.orEmpty().ifBlank { rootUri },
            mimeType = document?.type.orEmpty(),
            isDirectory = true,
            isRoot = true,
            isAvailable = available
        )
    }.sortedWith(entryComparator)

    fun list(location: LocalMediaDocumentLocation): List<LocalMediaDocumentEntry> {
        val directory = resolve(location) ?: return emptyList()
        return runCatching { directory.listFiles().toList() }
            .getOrDefault(emptyList())
            .filter { it.isDirectory || isSupportedMedia(it.name.orEmpty(), it.type) }
            .map { document -> document.toEntry(location) }
            .sortedWith(entryComparator)
    }

    fun collectMedia(
        location: LocalMediaDocumentLocation,
        maximumItems: Int = MAXIMUM_GROUP_ITEMS
    ): List<LocalMediaItem> {
        val root = resolve(location) ?: return emptyList()
        val pending = ArrayDeque<Pair<DocumentFile, String>>()
        val visited = mutableSetOf<String>()
        val result = mutableListOf<LocalMediaItem>()
        pending.add(root to root.name.orEmpty())
        while (pending.isNotEmpty() && result.size < maximumItems) {
            val (directory, folder) = pending.removeFirst()
            if (!visited.add(directory.uri.toString())) continue
            val children = runCatching { directory.listFiles() }.getOrDefault(emptyArray())
            children.forEach { document ->
                if (result.size >= maximumItems) return@forEach
                when {
                    document.isDirectory -> pending.add(document to document.name.orEmpty())

                    isSupportedMedia(document.name.orEmpty(), document.type) -> {
                        result += document.toMediaItem(folder)
                    }
                }
            }
        }
        return result.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, LocalMediaItem::title))
    }

    fun mediaItem(entry: LocalMediaDocumentEntry): LocalMediaItem? {
        if (entry.isDirectory || !entry.isAvailable) return null
        val contentType = entry.mimeType.ifBlank { mimeTypeForName(entry.name) }
        val video = contentType.startsWith("video/")
        return LocalMediaItem(
            mediaStoreId = -1L,
            contentUri = entry.contentUri,
            title = entry.name.substringBeforeLast('.').ifBlank { entry.name },
            artist = "",
            album = "",
            folder = entry.location.path.dropLast(1).lastOrNull().orEmpty(),
            mimeType = contentType,
            durationSeconds = 0L,
            addedAtSeconds = entry.lastModified / 1000L,
            isVideo = video,
            thumbnailUri = entry.contentUri.takeIf { video },
            relativePath = entry.location.path.dropLast(1).joinToString("/"),
            sizeBytes = entry.sizeBytes
        )
    }

    fun isAvailable(location: LocalMediaDocumentLocation): Boolean = resolve(location) != null

    private fun resolve(location: LocalMediaDocumentLocation): DocumentFile? {
        var document = runCatching {
            DocumentFile.fromTreeUri(context, Uri.parse(location.rootUri))
        }.getOrNull() ?: return null
        location.path.forEach { name ->
            document = runCatching { document.findFile(name) }.getOrNull() ?: return null
        }
        return document.takeIf { it.isDirectory && it.canRead() }
    }

    private fun DocumentFile.toEntry(
        parent: LocalMediaDocumentLocation
    ): LocalMediaDocumentEntry {
        val displayName = name.orEmpty().ifBlank { uri.lastPathSegment.orEmpty() }
        return LocalMediaDocumentEntry(
            location = parent.copy(path = parent.path + displayName),
            contentUri = uri.toString(),
            name = displayName,
            mimeType = type.orEmpty().ifBlank { mimeTypeForName(displayName) },
            isDirectory = isDirectory,
            sizeBytes = runCatching { length() }.getOrDefault(0L),
            lastModified = runCatching { lastModified() }.getOrDefault(0L)
        )
    }

    private fun DocumentFile.toMediaItem(folder: String): LocalMediaItem {
        val displayName = name.orEmpty().ifBlank { uri.lastPathSegment.orEmpty() }
        val contentType = type.orEmpty().ifBlank { mimeTypeForName(displayName) }
        val video = contentType.startsWith("video/")
        return LocalMediaItem(
            mediaStoreId = -1L,
            contentUri = uri.toString(),
            title = displayName.substringBeforeLast('.').ifBlank { displayName },
            artist = "",
            album = "",
            folder = folder,
            mimeType = contentType,
            durationSeconds = 0L,
            addedAtSeconds = runCatching { lastModified() / 1000L }.getOrDefault(0L),
            isVideo = video,
            thumbnailUri = uri.toString().takeIf { video },
            relativePath = folder,
            sizeBytes = runCatching { length() }.getOrDefault(0L)
        )
    }

    companion object {
        const val MAXIMUM_GROUP_ITEMS = 5000

        private val audioExtensions = setOf(
            "aac", "flac", "m4a", "mp3", "oga", "ogg", "opus", "wav", "wma"
        )
        private val videoExtensions = setOf(
            "3gp", "avi", "m4v", "mkv", "mov", "mp4", "mpeg", "mpg", "webm", "wmv"
        )
        private val entryComparator = compareByDescending<LocalMediaDocumentEntry> {
            it.isDirectory
        }.thenBy(String.CASE_INSENSITIVE_ORDER, LocalMediaDocumentEntry::name)

        internal fun isSupportedMedia(name: String, mimeType: String?): Boolean {
            if (mimeType?.startsWith("audio/") == true) return true
            if (mimeType?.startsWith("video/") == true) return true
            return name.substringAfterLast('.', "").lowercase() in audioExtensions + videoExtensions
        }

        internal fun mimeTypeForName(name: String): String {
            val extension = name.substringAfterLast('.', "").lowercase()
            return when (extension) {
                in audioExtensions -> "audio/$extension"
                in videoExtensions -> "video/$extension"
                else -> "application/octet-stream"
            }
        }
    }
}
