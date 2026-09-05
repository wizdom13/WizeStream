/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.media

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMediaArtworkTest {
    private val projectDirectory = if (Files.exists(Path.of("src/main"))) {
        Path.of("src/main")
    } else {
        Path.of("app/src/main")
    }

    @Test
    fun `artwork sampling keeps decoded images bounded`() {
        assertEquals(1, calculateArtworkSampleSize(512, 512, 512))
        assertEquals(2, calculateArtworkSampleSize(1_024, 1_024, 512))
        assertEquals(8, calculateArtworkSampleSize(4_096, 4_096, 512))
        assertEquals(8, calculateArtworkSampleSize(4_096, 500, 512))
    }

    @Test
    fun `local audio prefers embedded artwork and updates player surfaces`() {
        val loader = Files.readString(
            projectDirectory.resolve(
                "java/org/schabi/newpipe/local/media/LocalMediaThumbnailLoader.kt"
            )
        )
        val thumbnailController = Files.readString(
            projectDirectory.resolve(
                "java/org/schabi/newpipe/player/PlayerThumbnailController.kt"
            )
        )
        val queue = Files.readString(
            projectDirectory.resolve(
                "java/org/schabi/newpipe/player/playqueue/PlayQueueItemBuilder.java"
            )
        )

        assertTrue(loader.contains("retriever.embeddedPicture"))
        assertTrue(loader.indexOf("loadEmbeddedArtwork") < loader.indexOf("loadArtworkUri"))
        assertTrue(loader.contains("ArrayBlockingQueue(MAXIMUM_PENDING_THUMBNAILS)"))
        assertTrue(thumbnailController.contains("LocalMediaThumbnailLoader.loadBitmap"))
        assertTrue(queue.contains("LocalMediaThumbnailLoader.INSTANCE.load"))
    }
}
