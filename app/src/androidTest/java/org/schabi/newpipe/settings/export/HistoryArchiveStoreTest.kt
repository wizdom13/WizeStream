package org.schabi.newpipe.settings.export

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.database.AppDatabase

@RunWith(AndroidJUnit4::class)
class HistoryArchiveStoreTest {
    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
    private val archive = HistoryArchive(
        watches = listOf(ArchivedWatch(0, "https://example.com/watch", "Lesson", "VIDEO_STREAM", 120, "Teacher", 123456789, 3)),
        searches = listOf(ArchivedSearch(0, "lesson", 123456790))
    )

    @After
    fun close() = database.close()

    @Test
    fun mergingTwiceIsIdempotentAndWatchHistoryIsProfileScoped() {
        val first = HistoryArchiveStore(database, "first")
        assertEquals(2, first.merge(archive))
        assertEquals(0, first.merge(archive))
        assertEquals(archive, first.export())
        val second = HistoryArchiveStore(database, "second")
        assertEquals(0, second.export().watches.size)
        assertEquals(1, second.export().searches.size)
        assertEquals(1, second.merge(archive))
        assertEquals(archive, first.export())
        assertEquals(archive, second.export())
    }

    @Test
    fun recordingFailureRollsBackTheWholeImport() {
        val store = HistoryArchiveStore(database, "first", recordSearch = { _, _, _ -> error("Journal failed") })
        assertThrows(IllegalStateException::class.java) { store.merge(archive) }
        assertEquals(HistoryArchive(), HistoryArchiveStore(database, "first").export())
    }
}
