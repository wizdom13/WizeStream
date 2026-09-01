/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.media

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalMediaRepositoryTest {
    @Test
    fun `modern media locations retain the complete relative path`() {
        val location = localMediaLocation("Music/Artist/Album/", 29)

        assertEquals("Music/Artist/Album", location.relativePath)
        assertEquals("Album", location.folder)
    }

    @Test
    fun `legacy media locations use the parent file path`() {
        val location = localMediaLocation("/storage/emulated/0/Music/Track.mp3", 28)

        assertEquals("/storage/emulated/0/Music", location.relativePath)
        assertEquals("Music", location.folder)
    }

    @Test
    fun `media store track values preserve disc and track numbers`() {
        assertEquals(7 to 2, splitMediaStoreTrack(2_007))
        assertEquals(7 to 0, splitMediaStoreTrack(7))
        assertEquals(0 to 0, splitMediaStoreTrack(0))
    }
}
