/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.media

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LocalMediaGroupQueueBuilderTest {
    @Test
    fun `ordered group playback preserves library order`() {
        val group = group()

        assertEquals(group.items, LocalMediaGroupQueueBuilder.items(group, shuffle = false))
    }

    @Test
    fun `shuffled group playback retains every item exactly once`() {
        val group = group()
        val shuffled = LocalMediaGroupQueueBuilder.items(
            group,
            shuffle = true,
            random = Random(7)
        )

        assertEquals(group.items.toSet(), shuffled.toSet())
        assertNotEquals(group.items, shuffled)
    }

    private fun group(): LocalMediaGroup = LocalMediaGroup(
        stableKey = "album:1",
        title = "Album",
        subtitle = "Artist",
        items = (1L..5L).map(::audio),
        thumbnailUri = null,
        kind = LocalMediaGroupKind.ALBUM
    )

    private fun audio(id: Long) = LocalMediaItem(
        mediaStoreId = id,
        contentUri = "content://audio/$id",
        title = "Track $id",
        artist = "Artist",
        album = "Album",
        folder = "Music",
        mimeType = "audio/mpeg",
        durationSeconds = 60,
        addedAtSeconds = id,
        isVideo = false
    )
}
