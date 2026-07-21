package org.schabi.newpipe.update

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateChecksumTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun fromGitHubDigestAcceptsOnlySha256() {
        val checksum = "AB".repeat(32)

        assertEquals(
            checksum.lowercase(),
            UpdateChecksum.fromGitHubDigest("sha256:$checksum")
        )
        assertNull(UpdateChecksum.fromGitHubDigest("sha512:${"ab".repeat(64)}"))
        assertNull(UpdateChecksum.fromGitHubDigest("sha256:abcd"))
        assertNull(UpdateChecksum.fromGitHubDigest(null))
    }

    @Test
    fun calculateAndMatchSha256UsesFileContents() {
        val file = File(temporaryFolder.root, "update.apk").apply {
            writeText("abc")
        }
        val expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

        assertEquals(expected, UpdateChecksum.calculateSha256(file))
        assertTrue(UpdateChecksum.matches(file, expected))
        assertFalse(UpdateChecksum.matches(file, "00".repeat(32)))
        assertFalse(UpdateChecksum.matches(file, "invalid"))
    }
}
