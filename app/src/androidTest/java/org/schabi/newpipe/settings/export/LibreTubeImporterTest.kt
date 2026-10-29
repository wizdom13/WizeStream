package org.schabi.newpipe.settings.export

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.stream.model.StreamStateEntity
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.profiles.ProfileManager

class LibreTubeImporterTest {
    private val profile = ProfileManager.DEFAULT_PROFILE_ID
    private val first = LibreTubeVideo("abcdefghijk", "First")
    private val second = LibreTubeVideo("lmnopqrstuv", "Second")
    private val channel = LibreTubeChannel("UCabcdefghijklmnopqrstuv", "Channel")
    private fun backup() = LibreTubeBackup(
        listOf(channel),
        listOf(LibreTubeGroup("Science", listOf(channel.id), 0)),
        listOf(first),
        mapOf(first.id to 12000L),
        listOf(LibreTubePlaylist("Videos", listOf(second, first, second))),
        listOf(LibreTubeBookmark("PLabcdefghijk", "Bookmark", 4)),
        0
    )
    private fun database() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()

    private fun withDatabase(block: (AppDatabase) -> Unit) {
        val db = database()
        try {
            block(db)
        } finally {
            db.close()
        }
    }

    @Test fun allCategoriesAreIdempotentAndKeepExistingDataAndPlaylistOccurrences() {
        withDatabase { db ->
            db.subscriptionDAO().insert(SubscriptionEntity(serviceId = 0, url = channel.url, name = "My custom name", notificationMode = 1, profileId = profile))
            val importer = LibreTubeImporter(db, profile)
            importer.import(backup(), LibreTubeCategory.entries.toSet(), 1700000000000)
            val streamId = db.streamDAO().getStreamDirect(0, first.url)!!.uid
            db.streamStateDAO().upsert(StreamStateEntity(streamId, 5000, profile))
            assertEquals(0, importer.import(backup(), LibreTubeCategory.entries.toSet(), 1800000000000).added)
            val subscription = db.subscriptionDAO().getSubscriptionDirectForProfile(profile, 0, channel.url)!!
            assertEquals("My custom name", subscription.name)
            assertEquals(1, subscription.notificationMode)
            val group = db.feedGroupDAO().getAllDirectForProfile(profile).single()
            assertEquals(listOf(subscription.uid), db.feedGroupDAO().getSubscriptionIdsFor(group.uid).blockingFirst())
            assertEquals(5000L, db.streamStateDAO().getAllDirectForProfile(profile).single().progressMillis)
            assertEquals(1700000000000L, db.streamHistoryDAO().getAllDirectForProfile(profile).single().accessDate.toInstant().toEpochMilli())
            val playlist = db.playlistDAO().getAllDirectForProfile(profile).single()
            assertEquals(listOf(second.url, first.url, second.url), db.playlistStreamDAO().getOrderedStreamsDirectForProfile(profile, playlist.uid).map { it.url })
            assertEquals(1, db.playlistRemoteDAO().getAllDirectForProfile(profile).size)
        }
    }

    @Test fun selectionsAndProfilesAreIsolatedAndFailuresRollBack() {
        withDatabase { db ->
            LibreTubeImporter(db, profile).import(backup(), setOf(LibreTubeCategory.SUBSCRIPTIONS), 1700000000000)
            assertTrue(db.playlistDAO().getAllDirectForProfile(profile).isEmpty())
            assertTrue(db.streamHistoryDAO().getAllDirectForProfile(profile).isEmpty())
            val other = "11111111-1111-1111-1111-111111111111"
            val failing = LibreTubeImporter(db, other, recordWatch = { _, _ -> error("Disk failure") })
            assertThrows(IllegalStateException::class.java) { failing.import(backup(), LibreTubeCategory.entries.toSet(), 1700000000000) }
            assertTrue(db.subscriptionDAO().getSubscriptionDirectForProfile(other, 0, channel.url) == null)
            assertTrue(db.feedGroupDAO().getAllDirectForProfile(other).isEmpty())
            assertEquals(1, db.feedGroupDAO().getAllDirectForProfile(profile).size)
            LibreTubeImporter(db, other).import(backup(), LibreTubeCategory.entries.toSet(), 1700000000000)
            assertEquals(1, db.playlistDAO().getAllDirectForProfile(other).size)
            assertTrue(db.playlistDAO().getAllDirectForProfile(profile).isEmpty())
        }
    }

    @Test fun emptyAndSameNamedPlaylistsRemainDistinctWithoutTouchingChangedLists() {
        withDatabase { db ->
            val data = backup().copy(playlists = listOf(LibreTubePlaylist("Same", emptyList()), LibreTubePlaylist("Same", listOf(first)), LibreTubePlaylist("Same", listOf(first))))
            val importer = LibreTubeImporter(db, profile)
            importer.import(data, setOf(LibreTubeCategory.PLAYLISTS), 1)
            assertEquals(3, db.playlistDAO().getAllDirectForProfile(profile).size)
            assertEquals(0, importer.import(data, setOf(LibreTubeCategory.PLAYLISTS), 2).added)
            importer.import(data.copy(playlists = listOf(LibreTubePlaylist("Same", listOf(second)))), setOf(LibreTubeCategory.PLAYLISTS), 3)
            assertEquals(4, db.playlistDAO().getAllDirectForProfile(profile).size)
        }
    }
}
