package org.schabi.newpipe.local.tv

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {
    @Test
    fun parsesUnicodeQuotedCommasRelativeUrlsAndGroupsWithoutMetadataBleeding() {
        val playlist = """
            #EXTM3U
            #EXTINF:-1 tvg-logo="/logos/a.png" group-title="News, World",قناة الأخبار, مباشر
            channels/a.m3u8
            #EXTINF:-1,Second
            #EXTGRP:Music
            https://music.example/live.ts
            https://other.example/live.ts
        """.trimIndent()
        val result = M3uParser.parse(playlist, "https://tv.example/list.m3u")
        assertEquals(3, result.channels.size)
        assertEquals("قناة الأخبار, مباشر", result.channels[0].name)
        assertEquals("News, World", result.channels[0].group)
        assertEquals("https://tv.example/channels/a.m3u8", result.channels[0].url)
        assertEquals("https://tv.example/logos/a.png", result.channels[0].logo)
        assertEquals("Music", result.channels[1].group)
        assertEquals("", result.channels[2].group)
        assertEquals(null, result.channels[2].logo)
    }

    @Test
    fun unsupportedEntriesAreSkippedAndDuplicateUrlsAreNotRepeated() {
        val result = M3uParser.parse(
            """
            #EXTM3U
            #EXTINF:-1,Unsupported
            http://insecure.example/live
            file:///sdcard/movie.mp4
            content://documents/1
            https://user:password@tv.example/live
            #EXTINF:-1,First
            https://tv.example/live
            #EXTINF:-1,Duplicate
            https://tv.example/live
            """.trimIndent()
        )
        assertEquals(4, result.skipped)
        assertEquals("First", result.channels.single().name)
        assertEquals(null, tvUrl("http://tv.example/live"))
        assertEquals(null, tvUrl("http://redirect.example/live", "https://tv.example/list"))
    }

    @Test
    fun distinguishesHlsManifestsFromChannelPlaylistsAndBoundsInput() {
        val hls = "#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\nsegment.ts"
        assertTrue(M3uParser.isHls(hls))
        assertFalse(M3uParser.isHls("#EXTM3U\n#EXTINF:-1,Channel\nhttps://tv.example/live"))
        assertThrows(IOException::class.java) { M3uParser.parse(hls) }
        assertThrows(IOException::class.java) { M3uParser.parse("<html>Not a playlist</html>", "https://tv.example/list") }
        assertThrows(IOException::class.java) { M3uParser.parse("x".repeat(M3uParser.MAX_BYTES + 1)) }
        assertThrows(IOException::class.java) { TvPlaylistLoader.read(ByteArray(M3uParser.MAX_BYTES + 1).inputStream()) }
        assertThrows(IOException::class.java) { M3uParser.parse("#EXTM3U\n# no playable entries") }
    }
}
