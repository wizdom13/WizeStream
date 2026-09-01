/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.media

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMediaSafIntegrationTest {
    private val projectDirectory = if (Files.exists(Path.of("src/main"))) {
        Path.of("src/main")
    } else {
        Path.of("app/src/main")
    }

    @Test
    fun `folder access uses persisted storage access framework grants`() {
        val fragment = Files.readString(
            projectDirectory.resolve(
                "java/org/schabi/newpipe/local/media/LocalMediaFragment.kt"
            )
        )
        assertTrue(fragment.contains("ActivityResultContracts.OpenDocumentTree()"))
        assertTrue(fragment.contains("takePersistableUriPermission"))
        assertTrue(fragment.contains("Intent.FLAG_GRANT_READ_URI_PERMISSION"))
    }

    @Test
    fun `folder browser does not request all files access`() {
        val manifest = Files.readString(projectDirectory.resolve("AndroidManifest.xml"))
        assertFalse(manifest.contains("MANAGE_EXTERNAL_STORAGE"))
    }
}
