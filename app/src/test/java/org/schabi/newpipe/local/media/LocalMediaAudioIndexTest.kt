/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.media

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalMediaAudioIndexTest {
    @Test
    fun `artists use stable media store identities`() {
        val groups = LocalMediaAudioIndex.groups(
            listOf(
                audio(1, artist = "Artist", artistId = 10),
                audio(2, artist = "Artist", artistId = 11)
            ),
            LocalMediaAudioCategory.ARTISTS,
            "Unknown artist",
            "Unknown album",
            "Unknown genre"
        )

        assertEquals(listOf("artist:10", "artist:11"), groups.map(LocalMediaGroup::stableKey))
    }

    @Test
    fun `album tracks are ordered by disc and track`() {
        val groups = LocalMediaAudioIndex.groups(
            listOf(
                audio(1, albumId = 20, disc = 2, track = 1),
                audio(2, albumId = 20, disc = 1, track = 2),
                audio(3, albumId = 20, disc = 1, track = 1)
            ),
            LocalMediaAudioCategory.ALBUMS,
            "Unknown artist",
            "Unknown album",
            "Unknown genre"
        )

        assertEquals(listOf(3L, 2L, 1L), groups.single().items.map(LocalMediaItem::mediaStoreId))
    }

    @Test
    fun `tracks can appear in each assigned genre`() {
        val groups = LocalMediaAudioIndex.groups(
            listOf(audio(1, genres = setOf("Jazz", "Fusion"))),
            LocalMediaAudioCategory.GENRES,
            "Unknown artist",
            "Unknown album",
            "Unknown genre"
        )

        assertEquals(listOf("Fusion", "Jazz"), groups.map(LocalMediaGroup::title))
    }

    private fun audio(
        id: Long,
        artist: String = "Artist",
        artistId: Long = 10,
        albumId: Long = 20,
        disc: Int = 1,
        track: Int = 1,
        genres: Set<String> = emptySet()
    ) = LocalMediaItem(
        mediaStoreId = id,
        contentUri = "content://audio/$id",
        title = "Track $id",
        artist = artist,
        album = "Album",
        folder = "Music",
        mimeType = "audio/mpeg",
        durationSeconds = 60,
        addedAtSeconds = id,
        isVideo = false,
        artistId = artistId,
        albumId = albumId,
        trackNumber = track,
        discNumber = disc,
        genres = genres
    )
}
