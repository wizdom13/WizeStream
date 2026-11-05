package org.schabi.newpipe.local.feed.quickadd

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.playlist.dao.QuickAddStreamKey
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamType

@RunWith(AndroidJUnit4::class)
class QuickAddStoreTest {
    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
    private val owner = "00000000-0000-0000-0000-000000000000"
    private val other = "00000000-0000-0000-0000-000000000001"
    private val store = QuickAddStore(database, owner)
    private val video = StreamEntity(serviceId = 0, url = "https://example.com/video", title = "Video", streamType = StreamType.VIDEO_STREAM, duration = 60, uploader = "Channel")

    @After fun close() = database.close()

    private fun playlist(profile: String = owner): Long = database.playlistDAO().insert(
        PlaylistEntity(name = "Watch later", isThumbnailPermanent = false, thumbnailStreamId = -1, displayIndex = 0, profileId = profile)
    )

    @Test
    fun concurrentAddsInsertOnlyOneOccurrence() {
        val id = playlist()
        val executor = Executors.newFixedThreadPool(4)
        try {
            val results = executor.invokeAll((1..12).map { Callable { store.add(id, video) } })
            assertEquals(1, results.count { it.get(10, TimeUnit.SECONDS) != null })
            assertEquals(1, database.playlistStreamDAO().getQuickAddEntries(id).size)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun duplicateIsUnchangedAndUndoRestoresEmptyPlaylist() {
        val id = playlist()
        val added = requireNotNull(store.add(id, video))
        assertNull(store.add(id, video))
        assertEquals(listOf(QuickAddStreamKey(0, video.url)), database.playlistStreamDAO().observeQuickAddStreams(owner, id).blockingFirst())
        assertEquals(added.entry.streamUid, database.playlistDAO().getPlaylistDirect(id)!!.thumbnailStreamId)
        assertTrue(store.undo(added))
        assertTrue(database.playlistStreamDAO().getQuickAddEntries(id).isEmpty())
        assertEquals(-1L, database.playlistDAO().getPlaylistDirect(id)!!.thumbnailStreamId)
        assertFalse(store.undo(added))
    }

    @Test
    fun ownershipAndDeletedTargetsAreCheckedAtWriteTime() {
        val foreign = playlist(other)
        assertTrue(runCatching { store.add(foreign, video) }.isFailure)
        assertTrue(database.playlistStreamDAO().getQuickAddEntries(foreign).isEmpty())
        val id = playlist()
        val added = requireNotNull(store.add(id, video))
        assertFalse(QuickAddStore(database, other).undo(added))
        assertTrue(database.playlistStreamDAO().observeQuickAddStreams(other, id).blockingFirst().isEmpty())
        database.playlistDAO().deletePlaylist(id)
        assertFalse(store.undo(added))
        assertTrue(runCatching { store.add(id, video) }.isFailure)
        assertNotEquals(QuickAddPreference.targetKey(owner), QuickAddPreference.targetKey(other))
    }

    @Test
    fun undoDeclinesAfterPlaylistChangesAndNeverRemovesExistingVideos() {
        val id = playlist()
        val first = requireNotNull(store.add(id, video))
        val second = requireNotNull(store.add(id, video.copy(url = "https://example.com/second")))
        assertFalse(store.undo(first))
        assertEquals(2, database.playlistStreamDAO().getQuickAddEntries(id).size)
        assertTrue(store.undo(second))
        assertEquals(listOf(first.entry), database.playlistStreamDAO().getQuickAddEntries(id))
    }

    @Test
    fun sameUrlOnDifferentServicesIsNotADuplicate() {
        val id = playlist()
        store.add(id, video)
        store.add(id, video.copy(serviceId = 1))
        assertEquals(2, database.playlistStreamDAO().getQuickAddEntries(id).size)
    }
}
