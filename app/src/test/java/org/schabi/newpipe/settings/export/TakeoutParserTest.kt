package org.schabi.newpipe.settings.export

import java.io.File
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TakeoutParserTest {
    @Test
    fun zipCombinesPlaylistOrderAndHistoryWhileIgnoringOtherProducts() {
        val file = File.createTempFile("takeout-test", ".zip")
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                fun entry(name: String, text: String) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(text.toByteArray())
                    zip.closeEntry()
                }
                entry("Takeout/YouTube and YouTube Music/playlists/Science-videos.csv", "\uFEFFVideo ID,Time Added\r\nabcdefghijk,2024-01-01\r\nlmnopqrstuv,2024-01-02\r\n")
                entry("Takeout/YouTube and YouTube Music/history/watch-history.json", """[{"title":"Watched Science","titleUrl":"https://www.youtube.com/watch?v=abcdefghijk","time":"2024-02-03T04:05:06.123Z"},{"title":"Deleted video","time":"2024-02-03T00:00:00Z"}]""")
                entry("Takeout/My Activity/Search/search-history.json", "invalid ignored file")
            }
            val result = TakeoutParser.parse(file, "takeout.zip")
            assertEquals("Science", result.playlists.single().name)
            assertEquals(listOf("abcdefghijk", "lmnopqrstuv"), result.playlists.single().videos.map { it.title })
            assertEquals(Instant.parse("2024-02-03T04:05:06.123Z").toEpochMilli(), result.history.single().timestamp)
            assertEquals("Science", result.history.single().video.title)
            assertEquals(1, result.skipped)
        } finally {
            file.delete()
        }
    }

    @Test
    fun csvSupportsQuotedMetadataAndRejectsBrokenQuotes() {
        assertEquals(listOf(listOf("a,b", "two\nlines", "say \"hi\"")), TakeoutParser.csv("\"a,b\",\"two\nlines\",\"say \"\"hi\"\"\""))
        assertThrows(IllegalArgumentException::class.java) { TakeoutParser.csv("\"unclosed") }
    }

    @Test
    fun htmlPreservesUtcDatesAndSkipsUnknownLocalizedDates() {
        val file = File.createTempFile("watch-history", ".html")
        try {
            file.writeText("""<div class="content-cell">Watched <a href="https://www.youtube.com/watch?v=abcdefghijk">Science</a><br>Feb 3, 2024, 4:05:06 AM UTC</div>""")
            val result = TakeoutParser.parse(file, "watch-history.html")
            assertEquals(Instant.parse("2024-02-03T04:05:06Z").toEpochMilli(), result.history.single().timestamp)
        } finally {
            file.delete()
        }
    }
    @Test
    fun zipPreservesSeparateMetadataEmptyPlaylistsSubscriptionsAndSearches() {
        val file = File.createTempFile("takeout-data", ".zip")
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                fun entry(name: String, text: String) {
                    zip.putNextEntry(ZipEntry("Takeout/YouTube and YouTube Music/$name"))
                    zip.write(text.toByteArray())
                    zip.closeEntry()
                }
                entry("playlists/PLabcdefghijk-videos.csv", "Video ID,Time Added\nabcdefghijk,2024-01-01\nabcdefghijk,2024-01-02\n")
                entry("playlists/Empty-videos.csv", "Video ID,Time Added\n")
                entry("playlists/playlists.csv", "Playlist ID,Title\nPLabcdefghijk,Science\nPLemptyabcde,Empty\nPLlinkabcdef,Link only\n")
                entry("subscriptions/subscriptions.csv", "Channel Id,Channel Url,Channel Title\nUCabcdefghijklmnopqrstuv,https://www.youtube.com/channel/UCabcdefghijklmnopqrstuv,Science channel\n,https://www.youtube.com/channel/UClmnopqrstuvabcdefghijk,Second channel\ninvalid,https://example.com/channel/UCabcdefghijklmnopqrstuv,Unavailable\n")
                entry("history/search-history.json", """[{"title":"Localized title","titleUrl":"https://www.youtube.com/results?search_query=space+%26+time","time":"2024-02-03T04:05:06.123Z"},{"titleUrl":"https://www.youtube.com/results?search_query=space+%26+time","time":"2024-02-04T04:05:06.123Z"},{"titleUrl":"https://example.com/results?search_query=unrelated","time":"2024-02-04T04:05:06Z"}]""")
            }
            val result = TakeoutParser.parse(file, "takeout.zip")
            assertEquals(listOf("Science", "Empty", "Link only"), result.playlists.map { it.name })
            assertEquals(listOf("https://www.youtube.com/playlist?list=PLabcdefghijk", "https://www.youtube.com/playlist?list=PLemptyabcde", "https://www.youtube.com/playlist?list=PLlinkabcdef"), result.playlists.map { it.url })
            assertEquals(listOf(2, 0, 0), result.playlists.map { it.videos.size })
            assertEquals(listOf("Science channel", "Second channel"), result.subscriptions.map { it.name })
            assertEquals("https://www.youtube.com/channel/UClmnopqrstuvabcdefghijk", result.subscriptions[1].url)
            assertEquals(listOf("space & time", "space & time"), result.searches.map { it.query })
            assertEquals(Instant.parse("2024-02-03T04:05:06.123Z").toEpochMilli(), result.searches.first().timestamp)
            assertEquals(2, result.skipped)
        } finally {
            file.delete()
        }
    }

    @Test
    fun standaloneCsvKeepsEmbeddedPlaylistIdentityAndEmptyPlaylist() {
        val file = File.createTempFile("takeout-playlist", ".csv")
        try {
            file.writeText("Playlist Id,Playlist Title\nPLabcdefghijk,\"Music, science\"\n\nVideo ID,Time Added\n")
            val result = TakeoutParser.parse(file, "unrelated-filename.csv")
            assertEquals(TakeoutPlaylist("Music, science", emptyList(), "https://www.youtube.com/playlist?list=PLabcdefghijk"), result.playlists.single())
            file.writeText("Channel Id,Channel Url,Channel Title\nUCabcdefghijklmnopqrstuv,,Science\n")
            assertEquals("Science", TakeoutParser.parse(file, "subscriptions.csv").subscriptions.single().name)
        } finally {
            file.delete()
        }
    }

    @Test
    fun searchHtmlUsesQueryUrlAndOriginalDateWithoutGuessingLocalizedDates() {
        val file = File.createTempFile("takeout-search", ".html")
        try {
            file.writeText("""<div class="content-cell">Searched for <a href="https://www.youtube.com/results?search_query=%D9%81%D8%B6%D8%A7%D8%A1+science">space</a><br>Feb 3, 2024, 4:05:06 AM UTC</div><div class="content-cell"><a href="https://www.youtube.com/results?search_query=unknown">Unknown date</a><br>not a date</div>""")
            val result = TakeoutParser.parse(file, "search-history.html")
            assertEquals("فضاء science", result.searches.single().query)
            assertEquals(Instant.parse("2024-02-03T04:05:06Z").toEpochMilli(), result.searches.single().timestamp)
            assertEquals(1, result.skipped)
        } finally {
            file.delete()
        }
    }

}
