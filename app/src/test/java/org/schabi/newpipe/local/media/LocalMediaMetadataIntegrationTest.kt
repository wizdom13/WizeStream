/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.media

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMediaMetadataIntegrationTest {
    private val projectDirectory = if (Files.exists(Path.of("src/main"))) {
        Path.of("src/main")
    } else {
        Path.of("app/src/main")
    }

    @Test
    fun `player resolves tags lazily and rejects stale asynchronous results`() {
        val controller = Files.readString(
            projectDirectory.resolve(
                "java/org/schabi/newpipe/player/PlayerLocalMetadataController.kt"
            )
        )
        val loaderCall = controller.indexOf("LocalMediaMetadataLoader.load")
        val staleItemCheck = controller.indexOf("currentMetadata.item.isSameItem(item)", loaderCall)
        val metadataMutation = controller.indexOf("item.applyLocalMetadata", staleItemCheck)

        assertTrue(loaderCall >= 0)
        assertTrue(staleItemCheck > loaderCall)
        assertTrue(metadataMutation > staleItemCheck)
        assertTrue(controller.indexOf("playQueue?.notifyChange()", metadataMutation) > metadataMutation)
        assertTrue(
            controller.indexOf("notifyMetadataUpdateToListeners()", metadataMutation) >
                metadataMutation
        )
    }

    @Test
    fun `SAF selection resolves embedded tags before playback queue creation`() {
        val viewModel = Files.readString(
            projectDirectory.resolve(
                "java/org/schabi/newpipe/local/media/LocalMediaBrowserViewModel.kt"
            )
        )
        val fragment = Files.readString(
            projectDirectory.resolve(
                "java/org/schabi/newpipe/local/media/LocalMediaFragment.kt"
            )
        )

        val resolver = viewModel.indexOf("fun resolveMediaItem(")
        val metadataRead = viewModel.indexOf("LocalMediaMetadataLoader.readCached", resolver)
        val callback = viewModel.indexOf("mainHandler.post { onReady(item) }", metadataRead)
        val fragmentResolution = fragment.indexOf("browserViewModel.resolveMediaItem(entry)")
        val queueCreation = fragment.indexOf("LocalMediaPlayQueue", fragmentResolution)

        assertTrue(resolver >= 0)
        assertTrue(metadataRead > resolver)
        assertTrue(callback > metadataRead)
        assertTrue(fragmentResolution >= 0)
        assertTrue(queueCreation > fragmentResolution)
    }

    @Test
    fun `embedded metadata replaces SAF filename fallbacks without losing missing values`() {
        val item = LocalMediaItem(
            mediaStoreId = -1L,
            contentUri = "content://documents/42",
            title = "track-file",
            artist = "",
            album = "Fallback album",
            folder = "Music",
            mimeType = "application/octet-stream",
            durationSeconds = 0L,
            addedAtSeconds = 0L,
            isVideo = false
        )

        val resolved = item.withEmbeddedMetadata(
            LocalMediaEmbeddedMetadata(
                title = "Embedded title",
                artist = "Embedded artist",
                durationSeconds = 123L
            )
        )

        assertEquals("Embedded title", resolved.title)
        assertEquals("Embedded artist", resolved.artist)
        assertEquals("Fallback album", resolved.album)
        assertEquals(123L, resolved.durationSeconds)
    }
}
