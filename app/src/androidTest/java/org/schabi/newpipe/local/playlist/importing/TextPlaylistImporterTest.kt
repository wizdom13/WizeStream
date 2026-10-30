package org.schabi.newpipe.local.playlist.importing

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.profiles.ProfileManager

class TextPlaylistImporterTest {
    private val profile = ProfileManager.DEFAULT_PROFILE_ID
    private val first = "https://www.youtube.com/watch?v=abcdefghijk"
    private val second = "https://www.youtube.com/watch?v=lmnopqrstuv"
    private fun preview(vararg urls: String) = TextPlaylistPreview(urls.mapIndexed { i, url -> TextVideoLink(i + 1, url) }, emptyList())
    private fun stream(url: String) = StreamEntity(serviceId = 0, url = url, title = url.substringAfter("v="), streamType = StreamType.VIDEO_STREAM, duration = 60, uploader = "Channel")
    private fun withDatabase(block: (AppDatabase) -> Unit) {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        try {
            block(db)
        } finally {
            db.close()
        }
    }

    @Test fun createsAndAppendsInOrderWithOptionalDuplicatesAndNoUnneededRequests() = withDatabase { db ->
        var requests = 0
        val importer = TextPlaylistImporter(db, profile, resolve = {
            requests++
            stream(it)
        })
        assertEquals(3, importer.import(preview(first, second, first), null, "Later", false).added)
        assertEquals(2, requests)
        val playlist = db.playlistDAO().getAllDirectForProfile(profile).single()
        assertEquals(listOf(first, second, first), db.playlistStreamDAO().getOrderedStreamsDirectForProfile(profile, playlist.uid).map { it.url })
        val repeat = importer.import(preview(second, first), playlist.uid, "", true)
        assertEquals(0, repeat.added)
        assertEquals(2, repeat.duplicates)
        assertEquals(2, requests)
        assertEquals(1, importer.import(preview(second), playlist.uid, "", false).added)
        assertEquals(2, requests)
        assertEquals(listOf(first, second, first, second), db.playlistStreamDAO().getOrderedStreamsDirectForProfile(profile, playlist.uid).map { it.url })
    }

    @Test fun reportsUnavailableLinesWithoutCreatingEmptyPlaylistsAndKeepsSuccesses() = withDatabase { db ->
        val importer = TextPlaylistImporter(db, profile, resolve = { if (it == second) error("Unavailable") else stream(it) })
        assertEquals(listOf(1), importer.import(preview(second), null, "Empty", true).failedLines)
        assertTrue(db.playlistDAO().getAllDirectForProfile(profile).isEmpty())
        val result = importer.import(preview(first, first, second), null, "Later", true)
        assertEquals(1, result.added)
        assertEquals(1, result.duplicates)
        assertEquals(listOf(3), result.failedLines)
    }

    @Test fun rejectsCrossProfileDestinationsAndDeletedDestinations() = withDatabase { db ->
        TextPlaylistImporter(db, profile, ::stream).import(preview(first), null, "Mine", true)
        val id = db.playlistDAO().getAllDirectForProfile(profile).single().uid
        assertThrows(IllegalArgumentException::class.java) {
            TextPlaylistImporter(db, "other-profile", ::stream).import(preview(second), id, "", true)
        }
        val importer = TextPlaylistImporter(db, profile, resolve = { url ->
            db.playlistDAO().delete(db.playlistDAO().getPlaylistDirect(id)!!)
            stream(url)
        })
        assertThrows(IllegalArgumentException::class.java) { importer.import(preview(second), id, "", true) }
        assertTrue(db.streamDAO().getStreamDirect(0, second) == null)
    }

    @Test fun cancellationDuringSavingRollsBackAllRowsAndNewPlaylist() = withDatabase { db ->
        var checkingSave = false
        var checks = 0
        val importer = TextPlaylistImporter(
            db,
            profile,
            ::stream,
            checkActive = {
                if (checkingSave && ++checks == 3) throw CancellationException()
            },
            progress = { done, total -> if (done == total) checkingSave = true }
        )
        assertThrows(CancellationException::class.java) { importer.import(preview(first, second), null, "Cancelled", false) }
        assertTrue(db.playlistDAO().getAllDirectForProfile(profile).isEmpty())
        assertTrue(db.streamDAO().getStreamDirect(0, first) == null)
    }

    @Test fun rechecksDuplicatesAddedWhileResolving() = withDatabase { db ->
        TextPlaylistImporter(db, profile, ::stream).import(preview(first), null, "Later", true)
        val id = db.playlistDAO().getAllDirectForProfile(profile).single().uid
        val importer = TextPlaylistImporter(db, profile, resolve = { url ->
            TextPlaylistImporter(db, profile, ::stream).import(preview(url), id, "", true)
            stream(url)
        })
        val result = importer.import(preview(second), id, "", true)
        assertEquals(0, result.added)
        assertEquals(1, result.duplicates)
        assertEquals(listOf(first, second), db.playlistStreamDAO().getOrderedStreamsDirectForProfile(profile, id).map { it.url })
    }
}
