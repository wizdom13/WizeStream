package org.schabi.newpipe.local.playlist.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TextPlaylistParserTest {
    @Test fun normalizesVideoLinksAndPreservesLineNumbersAndOccurrences() {
        val data = TextPlaylistParser.parse(
            (
                "\uFEFF# watch later\r\n\r\n" +
                    "https://youtu.be/abcdefghijk?t=10\r\n" +
                    "https://www.youtube.com/shorts/lmnopqrstuv\r\n" +
                    "https://music.youtube.com/watch?v=abcdefghijk&list=PL123\r\n" +
                    "https://www.youtube.com/live/ABCDEFGHIJK\r\n"
                ).byteInputStream()
        )
        assertEquals(listOf(3, 4, 5, 6), data.links.map { it.line })
        assertEquals(listOf("abcdefghijk", "lmnopqrstuv", "abcdefghijk", "ABCDEFGHIJK"), data.links.map { it.url.substringAfter("v=") })
        assertTrue(data.invalidLines.isEmpty())
    }

    @Test fun rejectsOtherHostsPlaylistsCredentialsAndMalformedLinks() {
        val lines = listOf(
            "https://evil.example/watch?v=abcdefghijk", "https://youtube.com.evil.example/watch?v=abcdefghijk",
            "file:///tmp/abcdefghijk", "https://name:secret@youtube.com/watch?v=abcdefghijk", "https://youtube.com:8443/watch?v=abcdefghijk",
            "https://youtube.com/playlist?list=PL123", "https://youtube.com/@channel", "abcdefghijk", "https://youtu.be/short",
            "https://youtu.be/abcdefghijk https://youtu.be/lmnopqrstuv"
        )
        val data = TextPlaylistParser.parse(lines.joinToString("\n").byteInputStream())
        assertTrue(data.links.isEmpty())
        assertEquals((1..lines.size).toList(), data.invalidLines)
    }

    @Test fun enforcesByteLineAndEncodingLimits() {
        assertThrows(IllegalArgumentException::class.java) { TextPlaylistParser.parse("a".repeat(TextPlaylistParser.MAX_BYTES + 1).byteInputStream()) }
        assertThrows(IllegalArgumentException::class.java) { TextPlaylistParser.parse(("# ignored\n".repeat(2001)).byteInputStream()) }
        assertThrows(java.nio.charset.CharacterCodingException::class.java) { TextPlaylistParser.parse(byteArrayOf(0xC3.toByte(), 0x28).inputStream()) }
        assertEquals(2000, TextPlaylistParser.parse("https://youtu.be/abcdefghijk\n".repeat(2000).byteInputStream()).links.size)
    }
}
