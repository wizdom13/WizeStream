/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.download

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.MediaFormat
import us.shandian.giga.postprocessing.Mp3OutputOptions

internal class DownloadOutputPlanFactoryTest {
    @Test
    fun `native audio keeps its format and size`() {
        val plan = DownloadOutputPlanFactory.forAudio(
            baseFilename = "episode",
            sourceFormat = MediaFormat.M4A,
            sourceSize = 8_000,
            durationSeconds = 120,
            mp3OutputSelected = false,
            mp3BitrateKbps = 192
        )

        assertEquals("episode.m4a", plan.filename)
        assertEquals(MediaFormat.M4A.mimeType, plan.mimeType)
        assertEquals(8_000, plan.estimatedSize)
    }

    @Test
    fun `WebM Opus audio uses Ogg metadata`() {
        val plan = DownloadOutputPlanFactory.forAudio(
            baseFilename = "episode",
            sourceFormat = MediaFormat.WEBMA_OPUS,
            sourceSize = 7_000,
            durationSeconds = 120,
            mp3OutputSelected = false,
            mp3BitrateKbps = 192
        )

        assertEquals("episode.opus", plan.filename)
        assertEquals("audio/ogg", plan.mimeType)
        assertEquals(7_000, plan.estimatedSize)
    }

    @Test
    fun `MP3 output estimates transcoded size`() {
        val plan = DownloadOutputPlanFactory.forAudio(
            baseFilename = "episode",
            sourceFormat = MediaFormat.M4A,
            sourceSize = 8_000,
            durationSeconds = 120,
            mp3OutputSelected = true,
            mp3BitrateKbps = 256
        )

        assertEquals("episode.mp3", plan.filename)
        assertEquals(MediaFormat.MP3.mimeType, plan.mimeType)
        assertEquals(
            Mp3OutputOptions.estimateRequiredBytes(8_000, 120, 256),
            plan.estimatedSize
        )
    }

    @Test
    fun `native MP3 output keeps source size`() {
        val plan = DownloadOutputPlanFactory.forAudio(
            baseFilename = "episode",
            sourceFormat = MediaFormat.MP3,
            sourceSize = 6_000,
            durationSeconds = 120,
            mp3OutputSelected = true,
            mp3BitrateKbps = 320
        )

        assertEquals("episode.mp3", plan.filename)
        assertEquals(6_000, plan.estimatedSize)
    }

    @Test
    fun `video uses selected stream metadata`() {
        val plan = DownloadOutputPlanFactory.forVideo(
            baseFilename = "video",
            format = MediaFormat.WEBM,
            sourceSize = 42_000
        )

        assertEquals("video.webm", plan.filename)
        assertEquals(MediaFormat.WEBM.mimeType, plan.mimeType)
        assertEquals(42_000, plan.estimatedSize)
    }

    @Test
    fun `TTML subtitle is saved with SRT suffix`() {
        val plan = DownloadOutputPlanFactory.forSubtitle(
            baseFilename = "captions-en",
            format = MediaFormat.TTML,
            sourceSize = 2_000
        )

        assertEquals("captions-en.srt", plan.filename)
        assertEquals(MediaFormat.TTML.mimeType, plan.mimeType)
        assertEquals(2_000, plan.estimatedSize)
    }

    @Test
    fun `unknown format preserves legacy trailing separator`() {
        val plan = DownloadOutputPlanFactory.forVideo(
            baseFilename = "video",
            format = null,
            sourceSize = -1
        )

        assertEquals("video.", plan.filename)
        assertEquals(null, plan.mimeType)
        assertEquals(-1, plan.estimatedSize)
    }
}
