/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.media

enum class LocalMediaAudioCategory { TRACKS, ARTISTS, ALBUMS, GENRES }

data class LocalMediaGroup(
    val stableKey: String,
    val title: String,
    val subtitle: String,
    val items: List<LocalMediaItem>,
    val thumbnailUri: String?
)

object LocalMediaAudioIndex {
    fun groups(
        items: List<LocalMediaItem>,
        category: LocalMediaAudioCategory,
        unknownArtist: String,
        unknownAlbum: String,
        unknownGenre: String
    ): List<LocalMediaGroup> = when (category) {
        LocalMediaAudioCategory.TRACKS -> emptyList()
        LocalMediaAudioCategory.ARTISTS -> groupArtists(items, unknownArtist)
        LocalMediaAudioCategory.ALBUMS -> groupAlbums(items, unknownArtist, unknownAlbum)
        LocalMediaAudioCategory.GENRES -> groupGenres(items, unknownGenre)
    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, LocalMediaGroup::title))

    private fun groupArtists(
        items: List<LocalMediaItem>,
        unknownArtist: String
    ): List<LocalMediaGroup> = items.groupBy { item ->
        item.artistId.takeIf { it > 0 }?.let { "artist:$it" }
            ?: "artist-name:${item.artist.ifBlank { unknownArtist }}"
    }.map { (key, groupedItems) ->
        val title = groupedItems.first().artist.ifBlank { unknownArtist }
        LocalMediaGroup(
            stableKey = key,
            title = title,
            subtitle = "",
            items = groupedItems.sortedWith(trackComparator),
            thumbnailUri = groupedItems.firstNotNullOfOrNull(LocalMediaItem::thumbnailUri)
        )
    }

    private fun groupAlbums(
        items: List<LocalMediaItem>,
        unknownArtist: String,
        unknownAlbum: String
    ): List<LocalMediaGroup> = items.groupBy { item ->
        item.albumId.takeIf { it > 0 }?.let { "album:$it" }
            ?: "album-name:${item.artist}:${item.album}"
    }.map { (key, groupedItems) ->
        val first = groupedItems.first()
        LocalMediaGroup(
            stableKey = key,
            title = first.album.ifBlank { unknownAlbum },
            subtitle = first.artist.ifBlank { unknownArtist },
            items = groupedItems.sortedWith(trackComparator),
            thumbnailUri = groupedItems.firstNotNullOfOrNull(LocalMediaItem::thumbnailUri)
        )
    }

    private fun groupGenres(
        items: List<LocalMediaItem>,
        unknownGenre: String
    ): List<LocalMediaGroup> {
        val groups = linkedMapOf<String, MutableList<LocalMediaItem>>()
        items.forEach { item ->
            val genres = item.genres.ifEmpty { setOf(unknownGenre) }
            genres.forEach { genre -> groups.getOrPut(genre) { mutableListOf() } += item }
        }
        return groups.map { (genre, groupedItems) ->
            LocalMediaGroup(
                stableKey = "genre:$genre",
                title = genre,
                subtitle = "",
                items = groupedItems.sortedWith(trackComparator),
                thumbnailUri = groupedItems.firstNotNullOfOrNull(LocalMediaItem::thumbnailUri)
            )
        }
    }

    private val trackComparator = compareBy<LocalMediaItem>(
        LocalMediaItem::discNumber,
        LocalMediaItem::trackNumber,
        LocalMediaItem::title
    )
}
