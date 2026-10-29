package org.schabi.newpipe.settings.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LibreTubeBackupTest {
    @Test fun readsLibreTubeAliasesAndPrefersRichPlaylistsWithoutDuplicatingThem() {
        val backup = LibreTubeBackupParser.parse(
            """{
          "format":"Piped", "version":1,
          "localSubscriptions":[{"channelId":"UCabcdefghijklmnopqrstuv","name":"Channel"}],
          "channelGroups":[{"name":"Science","channels":["UCabcdefghijklmnopqrstuv"],"index":2}],
          "watchHistory":[{"videoId":"abcdefghijk","title":"Video","uploadDate":"2020-01-02"}],
          "watchPositions":[{"videoId":"abcdefghijk","position":12500}],
          "localPlaylists":[{"playlist":{"id":4,"name":"Favourites"},"videos":[{"videoId":"abcdefghijk"},{"videoId":"abcdefghijk"}]}],
          "playlists":[{"name":"Favourites","videos":["abcdefghijk"]}],
          "playlistBookmarks":[{"playlistId":"PLabcdefghijk","playlistName":"Saved","videos":8}],
          "preferences":[{"key":"token","value":"ignored"}]
        }"""
        )
        assertEquals("Science", backup.groups.single().name)
        assertEquals("Channel", backup.subscriptions.single().name)
        assertEquals(12500L, backup.positions["abcdefghijk"])
        assertEquals(2, backup.playlists.single().videos.size)
        assertEquals("Saved", backup.bookmarks.single().name)
        assertEquals(0, backup.skipped)
    }

    @Test fun rejectsForeignFormatsAndExcessiveNestingAndMalformedCategories() {
        for (json in listOf("{}", "{\"format\":\"Other\",\"subscriptions\":[]}", "{\"version\":2,\"subscriptions\":[]}", "{\"watchHistory\":[],\"subscriptions\":123}")) {
            assertThrows(Exception::class.java) { LibreTubeBackupParser.parse(json) }
        }
        assertThrows(IllegalArgumentException::class.java) { LibreTubeBackupParser.parse("[".repeat(33) + "0" + "]".repeat(33)) }
    }

    @Test fun skipsInvalidRowsAndDoesNotUseUntrustedUrlsOrNegativePositions() {
        val backup = LibreTubeBackupParser.parse(
            """{
          "watchHistory":[{"videoId":"https://evil.example/watch?v=abcdefghijk"},{"videoId":"abcdefghijk","thumbnailUrl":"file:///secret"}],
          "watchPositions":[{"videoId":"abcdefghijk","position":-10}],
          "playlists":[{"name":"Fallback","videos":["/watch?v=abcdefghijk","https://youtu.be/lmnopqrstuv"]}]
        }"""
        )
        assertEquals(2, backup.skipped)
        assertEquals(null, backup.history.single().thumbnail)
        assertTrue(backup.positions.isEmpty())
        assertEquals(listOf("abcdefghijk", "lmnopqrstuv"), backup.playlists.single().videos.map { it.id })
    }

    @Test fun limitsFileSizeAndEntryCount() {
        assertThrows(IllegalArgumentException::class.java) {
            LibreTubeBackupParser.parse(" ".repeat(LibreTubeBackupParser.MAX_BYTES + 1).byteInputStream())
        }
        assertThrows(IllegalArgumentException::class.java) {
            LibreTubeBackupParser.parse("{\"watchHistory\":[" + List(50_001) { "null" }.joinToString(",") + "]}")
        }
    }
}
