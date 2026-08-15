/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.fragments.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalMediaMetadataFormatterTest {
    @Test
    fun `formats common local media mime types`() {
        assertEquals("MP4", LocalMediaMetadataFormatter.format("video/mp4"))
        assertEquals("MP3", LocalMediaMetadataFormatter.format("audio/mpeg"))
        assertEquals("MKV", LocalMediaMetadataFormatter.format("video/x-matroska"))
    }

    @Test
    fun `formats landscape resolution`() {
        val metadata = LocalMediaTechnicalMetadata(width = 1920, height = 1080)

        assertEquals("1920 × 1080", LocalMediaMetadataFormatter.resolution(metadata))
    }

    @Test
    fun `applies video rotation to resolution`() {
        val metadata = LocalMediaTechnicalMetadata(
            width = 1920,
            height = 1080,
            rotation = 90
        )

        assertEquals("1080 × 1920", LocalMediaMetadataFormatter.resolution(metadata))
    }
}
