/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.media

enum class LocalMediaVideoCategory { VIDEOS, FOLDERS }

object LocalMediaVideoIndex {
    fun folders(
        items: List<LocalMediaItem>,
        unknownFolder: String
    ): List<LocalMediaGroup> = items.groupBy { item ->
        "${item.volumeName}:${item.relativePath}"
    }.map { (key, groupedItems) ->
        val first = groupedItems.first()
        LocalMediaGroup(
            stableKey = "folder:$key",
            title = first.folder.ifBlank { unknownFolder },
            subtitle = folderSubtitle(first),
            items = groupedItems.sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER, LocalMediaItem::title)
            ),
            thumbnailUri = groupedItems.firstNotNullOfOrNull(LocalMediaItem::thumbnailUri),
            kind = LocalMediaGroupKind.VIDEO_FOLDER
        )
    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, LocalMediaGroup::title))

    private fun folderSubtitle(item: LocalMediaItem): String = listOf(
        item.volumeName,
        item.relativePath
    ).filter(String::isNotBlank).joinToString(" • ")
}
