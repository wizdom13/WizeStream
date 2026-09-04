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
        val player = Files.readString(
            projectDirectory.resolve("java/org/schabi/newpipe/player/Player.java")
        )
        val loaderCall = player.indexOf("LocalMediaMetadataLoader.INSTANCE.load")
        val staleItemCheck = player.indexOf(".getItem().isSameItem(item)", loaderCall)
        val metadataMutation = player.indexOf("item.applyLocalMetadata", staleItemCheck)

        assertTrue(loaderCall >= 0)
        assertTrue(staleItemCheck > loaderCall)
        assertTrue(metadataMutation > staleItemCheck)
        assertTrue(player.indexOf("playQueue.notifyChange()", metadataMutation) > metadataMutation)
        assertTrue(
            player.indexOf("notifyMetadataUpdateToListeners()", metadataMutation) >
                metadataMutation
        )
    }
}
