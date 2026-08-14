package org.wisso.wizestream.desktop.backend

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopLibraryTest {
    @Test
    fun `library editors persist and remove synchronized materialized records`() {
        val directory = createTempDirectory("wizestream-library-test")
        try {
            DesktopDatabase(directory).use { database ->
                val library = DesktopLibrary(database.connection())
                val stream = DesktopLibrary.stream(
                    0,
                    "https://www.youtube.com/watch?v=phase3fixture",
                    "Phase 3 fixture",
                    600,
                    "VIDEO_STREAM",
                    "WizeStream",
                    "https://www.youtube.com/@wizestream",
                    "https://i.ytimg.com/vi/phase3fixture/hqdefault.jpg"
                )

                library.saveSubscription(
                    0,
                    "https://www.youtube.com/@wizestream",
                    "WizeStream",
                    "https://example.com/avatar.png"
                )
                assertEquals("WizeStream", library.subscriptions().single()["name"])
                library.updateSubscriptionAvatar(
                    0,
                    "https://www.youtube.com/@wizestream",
                    "https://example.com/refreshed-avatar.png"
                )
                assertEquals(
                    "https://example.com/refreshed-avatar.png",
                    library.subscriptions().single()["avatarUrl"]
                )

                val playlist = library.createPlaylist("Study")
                val playlistId = playlist.getValue("id") as String
                val item = library.addPlaylistItem(playlistId, stream)
                assertEquals("Phase 3 fixture", library.playlistItems(playlistId).single()["title"])
                assertEquals(1L, library.playlists().single()["itemCount"])

                val history = library.recordHistory(stream)
                assertEquals("Phase 3 fixture", library.history().single()["title"])
                val search = library.recordSearch(0, "Phase 3")
                assertEquals("Phase 3", library.searchHistory().single()["query"])

                val note = library.saveLearningNote(null, stream, 42, "Remember this point")
                assertEquals("Remember this point", library.learningNotes().single()["note"])
                library.saveLearningNote(note.getValue("id") as String, stream, 50, "Updated")
                assertEquals(50L, library.learningNotes().single()["positionSeconds"])

                library.deletePlaylistItem(playlistId, item.getValue("itemId") as String)
                library.deletePlaylist(playlistId)
                library.deleteHistory(history.getValue("id") as String)
                library.deleteSearch(search.getValue("id") as String)
                library.deleteLearningNote(note.getValue("id") as String)
                library.deleteSubscription(0, "https://www.youtube.com/@wizestream")

                assertTrue(library.playlists().isEmpty())
                assertTrue(library.history().isEmpty())
                assertTrue(library.searchHistory().isEmpty())
                assertTrue(library.learningNotes().isEmpty())
                assertTrue(library.subscriptions().isEmpty())
            }
        } finally {
            directory.toFile().deleteRecursively()
            assertTrue(Files.notExists(directory))
        }
    }
}
