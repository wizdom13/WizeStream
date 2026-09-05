/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.media

import java.nio.file.Files
import java.nio.file.Path
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
}
