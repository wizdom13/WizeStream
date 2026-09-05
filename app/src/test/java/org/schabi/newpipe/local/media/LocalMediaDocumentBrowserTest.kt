/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMediaDocumentBrowserTest {
    @Test
    fun `audio and video mime types are supported`() {
        assertTrue(LocalMediaDocumentBrowser.isSupportedMedia("track.bin", "audio/flac"))
        assertTrue(LocalMediaDocumentBrowser.isSupportedMedia("movie.bin", "video/mp4"))
    }

    @Test
    fun `known extensions recover missing provider mime types`() {
        assertTrue(LocalMediaDocumentBrowser.isSupportedMedia("track.OPUS", null))
        assertTrue(LocalMediaDocumentBrowser.isSupportedMedia("movie.MKV", ""))
        assertEquals("audio/mp3", LocalMediaDocumentBrowser.mimeTypeForName("track.mp3"))
        assertEquals("video/webm", LocalMediaDocumentBrowser.mimeTypeForName("movie.webm"))
    }

    @Test
    fun `unrelated documents stay out of the media browser`() {
        assertFalse(LocalMediaDocumentBrowser.isSupportedMedia("notes.pdf", "application/pdf"))
        assertFalse(LocalMediaDocumentBrowser.isSupportedMedia("archive.zip", null))
    }

    @Test
    fun `folder artwork prefers standard cover names`() {
        val candidates = listOf(
            artwork("front.png", "image/png", "front"),
            artwork("folder.webp", "image/webp", "folder"),
            artwork("cover.JPG", "image/jpeg", "cover"),
            artwork("albumart.jpeg", "image/jpeg", "albumart")
        )

        assertEquals("cover", chooseLocalFolderArtwork(candidates))
        assertEquals("folder", chooseLocalFolderArtwork(candidates.filter { it.uri != "cover" }))
    }

    @Test
    fun `single generic folder image is fallback but ambiguous images are not guessed`() {
        val single = listOf(artwork("concert.png", "image/png", "concert"))
        assertEquals("concert", chooseLocalFolderArtwork(single))

        val ambiguous = listOf(
            artwork("concert.png", "image/png", "concert"),
            artwork("booklet.jpg", "image/jpeg", "booklet")
        )
        assertNull(chooseLocalFolderArtwork(ambiguous))
    }

    @Test
    fun `folder artwork accepts jpeg png and webp only`() {
        assertTrue(isSupportedLocalFolderArtwork("cover.jpg", null))
        assertTrue(isSupportedLocalFolderArtwork("cover.JPEG", null))
        assertTrue(isSupportedLocalFolderArtwork("opaque", "image/png"))
        assertTrue(isSupportedLocalFolderArtwork("folder.webp", "application/octet-stream"))
        assertFalse(isSupportedLocalFolderArtwork("cover.gif", "image/gif"))
        assertFalse(isSupportedLocalFolderArtwork("notes.txt", "text/plain"))
    }

    @Test
    fun `whole folder scans have a defensive item limit`() {
        assertEquals(5000, LocalMediaDocumentBrowser.MAXIMUM_GROUP_ITEMS)
    }

    private fun artwork(name: String, mimeType: String?, uri: String) = LocalFolderArtworkCandidate(name, mimeType, uri)
}
