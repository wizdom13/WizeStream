/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `whole folder scans have a defensive item limit`() {
        assertEquals(5000, LocalMediaDocumentBrowser.MAXIMUM_GROUP_ITEMS)
    }
}
