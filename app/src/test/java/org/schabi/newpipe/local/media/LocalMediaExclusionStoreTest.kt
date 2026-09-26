/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LocalMediaExclusionStoreTest {
    @Test
    fun `media folder identity includes volume and relative path`() {
        val external = item(volume = "external_primary", path = "Movies/Family/")
        val card = item(volume = "1234-5678", path = "Movies/Family/")

        assertEquals(
            "external_primary|Movies/Family",
            LocalMediaExclusionStore.mediaFolderKey(external)
        )
        assertNotEquals(
            LocalMediaExclusionStore.mediaFolderKey(external),
            LocalMediaExclusionStore.mediaFolderKey(card)
        )
    }

    @Test
    fun `document folder identity includes root and complete nested path`() {
        val first = LocalMediaDocumentLocation(
            rootUri = "content://tree/root",
            path = listOf("Movies", "Family")
        )
        val second = first.copy(path = listOf("Movies", "Work"))

        assertEquals(
            "content://tree/root|Movies/Family",
            LocalMediaExclusionStore.documentLocationKey(first)
        )
        assertNotEquals(
            LocalMediaExclusionStore.documentLocationKey(first),
            LocalMediaExclusionStore.documentLocationKey(second)
        )
    }

    private fun item(volume: String, path: String) = LocalMediaItem(
        mediaStoreId = 1L,
        contentUri = "content://media/item",
        title = "Item",
        artist = "",
        album = "",
        folder = "Family",
        mimeType = "video/mp4",
        durationSeconds = 1L,
        addedAtSeconds = 1L,
        isVideo = true,
        relativePath = path,
        volumeName = volume
    )
}
