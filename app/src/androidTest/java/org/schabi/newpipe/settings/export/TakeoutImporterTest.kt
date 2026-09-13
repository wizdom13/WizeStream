package org.schabi.newpipe.settings.export

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity
import org.schabi.newpipe.database.subscription.SubscriptionEntity

class TakeoutImporterTest {
    @Test
    fun repeatedImportsPreservePlaylistOrderAndWatchDatesWithoutDuplicates() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val first = TakeoutVideo("https://www.youtube.com/watch?v=abcdefghijk", "First")
            val second = TakeoutVideo("https://www.youtube.com/watch?v=lmnopqrstuv", "Second")
            val data = TakeoutData(listOf(TakeoutPlaylist("Learning", listOf(second, first))), listOf(TakeoutWatch(first, 1700000000123)), 0)
            var journaled = 0
            val importer = TakeoutImporter(db) { _, _, _ -> journaled++ }
            assertEquals(TakeoutImportResult(1, 2, 1, 0), importer.import(data))
            assertEquals(TakeoutImportResult(0, 0, 0, 3), importer.import(data))
            val playlist = db.playlistDAO().getAllDirect().single()
            assertEquals(listOf(second.url, first.url), db.playlistStreamDAO().getOrderedStreamsDirect(playlist.uid).map { it.url })
            assertEquals(1700000000123, db.streamHistoryDAO().getAllDirect().single().accessDate.toInstant().toEpochMilli())
            assertEquals(1, journaled)
        } finally {
            db.close()
        }
    }

    @Test
    fun failedHistoryWriteRollsBackPlaylistsAndStreamsAsWell() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val video = TakeoutVideo("https://www.youtube.com/watch?v=abcdefghijk", "First")
            val data = TakeoutData(listOf(TakeoutPlaylist("Learning", listOf(video))), listOf(TakeoutWatch(video, 1700000000123)), 0)
            val importer = TakeoutImporter(db) { _, _, _ -> throw IllegalStateException("Simulated storage failure") }
            assertThrows(IllegalStateException::class.java) { importer.import(data) }
            assertEquals(0, db.playlistDAO().getAllDirect().size)
            assertEquals(0, db.streamHistoryDAO().getAllDirect().size)
            assertEquals(0, db.streamDAO().getAll().blockingFirst().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun preservesEmptyAndSameNamedPlaylistsRepeatedVideosAndExistingChannelSettings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val first = TakeoutVideo("https://www.youtube.com/watch?v=abcdefghijk", "First")
            val second = TakeoutVideo("https://www.youtube.com/watch?v=lmnopqrstuv", "Second")
            val channel = "https://www.youtube.com/channel/UCabcdefghijklmnopqrstuv"
            val known = SubscriptionEntity(serviceId = 0, url = channel, name = "My channel name", notificationMode = 1, notificationKeywords = "science", youtubeModeMask = 3)
            known.uid = db.subscriptionDAO().insert(known)
            val data = TakeoutData(
                listOf(
                    TakeoutPlaylist("Learning", listOf(second, first, second), "https://www.youtube.com/playlist?list=PLabcdefghijk"),
                    TakeoutPlaylist("Learning", listOf(first)),
                    TakeoutPlaylist("Empty", emptyList())
                ),
                emptyList(),
                0,
                listOf(TakeoutSubscription(channel, "Exported name"), TakeoutSubscription("https://www.youtube.com/channel/UClmnopqrstuvabcdefghijk", "New channel")),
                listOf(TakeoutSearch("space", 1700000000123), TakeoutSearch("space", 1700000001123))
            )
            val searchJournal = mutableListOf<Long>()
            val subscriptionJournal = mutableListOf<String?>()
            val importer = TakeoutImporter(db, { _, time -> searchJournal.add(time) }, { subscriptionJournal.add(it.url) }) { _, _, _ -> }
            assertEquals(TakeoutImportResult(3, 4, 0, 1, 1, 1, 2), importer.import(data))
            assertEquals(TakeoutImportResult(0, 0, 0, 8), importer.import(data.copy(playlists = data.playlists.reversed())))
            val playlists = db.playlistDAO().getAllDirect().associateBy { it.name }
            assertEquals(3, playlists.size)
            assertEquals(listOf(second.url, first.url, second.url), db.playlistStreamDAO().getOrderedStreamsDirect(playlists.getValue("Learning (Takeout)").uid).map { it.url })
            assertEquals(listOf(first.url), db.playlistStreamDAO().getOrderedStreamsDirect(playlists.getValue("Learning (Takeout 2)").uid).map { it.url })
            assertEquals(-1L, playlists.getValue("Empty (Takeout)").thumbnailStreamId)
            assertEquals(1, db.playlistRemoteDAO().getAllDirect().size)
            assertEquals(known, db.subscriptionDAO().getSubscriptionDirect(0, channel))
            assertEquals(listOf(1700000000123, 1700000001123), db.searchHistoryDAO().getAllDirect().map { it.creationDate!!.toInstant().toEpochMilli() })
            assertEquals(listOf(1700000000123, 1700000001123), searchJournal)
            assertEquals(1, subscriptionJournal.size)
            // Preserve existing sparse positions and append after the maximum index, not row count.
            val empty = playlists.getValue("Empty (Takeout)")
            val streamId = db.streamDAO().getStreamDirect(0, first.url)!!.uid
            db.playlistStreamDAO().insert(PlaylistStreamEntity(empty.uid, streamId, 8))
            importer.import(TakeoutData(listOf(TakeoutPlaylist("Empty", listOf(first, second))), emptyList(), 0))
            assertEquals(9, db.playlistStreamDAO().getMaximumIndexDirect(empty.uid))
            assertEquals(listOf(first.url, second.url), db.playlistStreamDAO().getOrderedStreamsDirect(empty.uid).map { it.url })
        } finally {
            db.close()
        }
    }

    @Test
    fun failedSearchWriteRollsBackSubscriptionsBookmarksAndPlaylists() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val data = TakeoutData(
                listOf(TakeoutPlaylist("Empty", emptyList(), "https://www.youtube.com/playlist?list=PLabcdefghijk")),
                emptyList(),
                0,
                listOf(TakeoutSubscription("https://www.youtube.com/channel/UCabcdefghijklmnopqrstuv", "Science")),
                listOf(TakeoutSearch("space", 1700000000123))
            )
            val importer = TakeoutImporter(db, { _, _ -> throw IllegalStateException("Simulated storage failure") }) { _, _, _ -> }
            assertThrows(IllegalStateException::class.java) { importer.import(data) }
            assertEquals(0, db.playlistDAO().getAllDirect().size)
            assertEquals(0, db.playlistRemoteDAO().getAllDirect().size)
            assertEquals(0, db.subscriptionDAO().getAllDirect().size)
            assertEquals(0, db.searchHistoryDAO().getAllDirect().size)
        } finally {
            db.close()
        }
    }
}
