/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.transcript

import android.app.Application
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TranscriptRepositoryTest {
    @Test fun webVttPreservesTimestampsMarkupAndRepeatedPhrases() {
        val text = "WEBVTT\n\n00:01.500 --> 00:02.500\n<b>Hello</b> &amp; welcome\n\n00:04.000 --> 00:06.000\nHello &amp; welcome\n"
        assertEquals(
            listOf(TranscriptLine(1500, "Hello & welcome"), TranscriptLine(4000, "Hello & welcome")),
            TranscriptRepository.parse(text.toByteArray(), "text/vtt")
        )
    }

    @Test fun ttmlHandlesSpansAndMultilineCaptions() {
        val text = """<tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="00:00:03.200" end="00:00:05.000">Hello <span>world</span><br/>Again</p></div></body></tt>"""
        assertEquals(listOf(TranscriptLine(3200, "Hello world\nAgain")), TranscriptRepository.parse(text.toByteArray(), "application/ttml+xml"))
    }

    @Test fun subripSupportsHourOffsetsAndMilliseconds() {
        val text = "1\n01:02:03,450 --> 01:02:06,000\nA lesson\n"
        assertEquals(listOf(TranscriptLine(3_723_450, "A lesson")), TranscriptRepository.parse(text.toByteArray(), "application/x-subrip"))
    }

    @Test fun searchKeepsOriginalTimestampsAndIgnoresCase() {
        val lines = listOf(TranscriptLine(0, "First lesson"), TranscriptLine(4000, "SECOND lesson"))
        assertEquals(listOf(lines[1]), TranscriptRepository.filter(lines, " second "))
        assertEquals(lines, TranscriptRepository.filter(lines, "  "))
        assertEquals(emptyList<TranscriptLine>(), TranscriptRepository.filter(lines, "absent"))
    }

    @Test fun rejectsOversizedPayloadsEvenWithoutContentLength() {
        assertThrows(IllegalArgumentException::class.java) {
            TranscriptRepository.readBounded(ByteArrayInputStream(ByteArray(TranscriptRepository.MAX_BYTES + 1)))
        }
    }

    @Test fun rejectsUnsupportedFormats() {
        assertThrows(IllegalArgumentException::class.java) { TranscriptRepository.parse(byteArrayOf(), "text/html") }
    }
}
