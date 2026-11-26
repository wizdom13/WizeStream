/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.schabi.newpipe.local.history

import android.app.Application
import android.database.AbstractWindowedCursor
import android.database.Cursor
import android.database.CursorWrapper
import android.os.CancellationSignal
import androidx.preference.PreferenceManager
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.history.model.StreamHistoryEntity
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity
import org.schabi.newpipe.database.stream.StreamStatisticsEntry
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.stream.model.StreamStateEntity
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.feed.quickadd.QuickAddStore
import org.schabi.newpipe.recommendations.RecommendationWorkDispatcher
import org.schabi.newpipe.sync.HistorySyncRecorder

/** Exercises Room's generated mapper and the real Android SQLiteCursor window-refill path. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class HistoryQuerySnapshotTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val name = "history-snapshot-${UUID.randomUUID()}.db"
    private val queries = Executors.newFixedThreadPool(2)
    private val transactions = Executors.newSingleThreadExecutor()
    private val writer = Executors.newSingleThreadExecutor()
    private val releases = mutableListOf<CountDownLatch>()
    private val beforeRefill = AtomicReference<((AbstractWindowedCursor, Boolean) -> Unit)?>(null)
    private val databaseField = NewPipeDatabase::class.java.getDeclaredField("databaseInstance").apply { isAccessible = true }
    private var previousDatabase: Any? = null
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .openHelperFactory(RefillHookFactory(beforeRefill))
            .allowMainThreadQueries()
            .setQueryExecutor(queries)
            .setTransactionExecutor(transactions)
            .build()
        previousDatabase = databaseField.get(null)
        databaseField.set(null, database)
        database.query("PRAGMA journal_mode", null).use {
            assertTrue(it.moveToFirst())
            assertEquals("wal", it.getString(0))
        }
    }

    @After
    fun tearDown() {
        beforeRefill.set(null)
        releases.forEach(CountDownLatch::countDown)
        val writerStopped = stop(writer)
        val recommendationsSettled = RecommendationWorkDispatcher.awaitIdleForTesting()
        databaseField.set(null, previousDatabase)
        database.close()
        val transactionsStopped = stop(transactions)
        val queriesStopped = stop(queries)
        context.deleteDatabase(name)
        assertTrue("The concurrent mutation must finish before database cleanup", writerStopped)
        assertTrue("Recommendation side effects must finish before database cleanup", recommendationsSettled)
        assertTrue("Room's transaction executor must stop", transactionsStopped)
        assertTrue("Room's query executor must stop", queriesStopped)
    }

    @Test
    fun largeHistoryRemainsConsistentWhenMarkingUnwatchedAndQuickAddingDuringRead() {
        val seed = seedHistory()
        // Device Sync identity uses AndroidKeyStore, which is unavailable on this JVM. Replace
        // only that constructor dependency; the history manager, DAOs and transactions are real.
        val recorder = mock(HistorySyncRecorder::class.java)
        val manager = mockStatic(HistorySyncRecorder::class.java) { recorder }.use {
            HistoryRecordManager(context, PROFILE)
        }
        val victim = seed.streams.last()
        val startMutation = CountDownLatch(1).also(releases::add)
        val mutationStarted = CountDownLatch(1)
        val mutationCommitted = CountDownLatch(1)
        val crossedWindow = AtomicBoolean()
        val mutation = writer.submit {
            check(startMutation.await(10, SECONDS))
            mutationStarted.countDown()
            manager.markAsUnwatched(victim.toStreamInfoItem()).blockingAwait()
            val addition = QuickAddStore(database, PROFILE).add(seed.playlistId, victim)
            assertEquals(victim.uid, addition!!.entry.streamUid)
            mutationCommitted.countDown()
        }
        beforeRefill.set { cursor, readerHoldsTransaction ->
            assertEquals(seed.streams.size, cursor.count)
            assertTrue("The real query must span multiple CursorWindows", cursor.window.numRows in 1 until cursor.count)
            crossedWindow.set(true)
            startMutation.countDown()
            assertTrue(mutationStarted.await(5, SECONDS))
            // The unprotected query must refill after the deletion commits. A transactional
            // reader may hold the legacy framework writer lock, so finish that snapshot first
            // and await the mutation outside the cursor. Transaction state only schedules the
            // interleaving; all assertions below concern the returned data and invalidation.
            if (!readerHoldsTransaction) {
                assertTrue("The concurrent history deletion must commit", mutationCommitted.await(10, SECONDS))
            }
        }
        val result = manager.streamStatistics.test()
        try {
            result.awaitCount(1).assertNoErrors()
            assertTrue("The initial history snapshot must arrive", result.values().isNotEmpty())
            assertTrue("The coordinated cursor refill must have occurred", crossedWindow.get())
            val expected = seed.streams.mapIndexed { index, stream ->
                HistoryRow(stream.uid, stream.title, progress(index), WATCHED_AT.minusSeconds(index.toLong()), 2)
            }
            assertEquals("One emission must contain one complete history snapshot", expected, result.values().first().map(::row))

            mutation.get(10, SECONDS)
            result.awaitCount(2).assertNoErrors()
            assertTrue("The observer must receive the write invalidation", result.values().size >= 2)
            assertEquals("Room must publish the subsequent unwatch invalidation", expected.dropLast(1), result.values().last().map(::row))
            assertNull(database.streamStateDAO().getStateDirectForProfile(PROFILE, victim.uid))
            assertEquals(
                listOf(seed.streams.first().uid, victim.uid),
                database.playlistStreamDAO().getOrderedStreamsOfForProfile(PROFILE, seed.playlistId)
                    .blockingFirst().map { it.streamEntity.uid }
            )
            assertEquals(
                listOf(HistoryRow(victim.uid, victim.title, OTHER_PROGRESS, WATCHED_AT, 7)),
                database.streamHistoryDAO().getStatisticsForProfile(OTHER_PROFILE).blockingFirst().map(::row)
            )
        } finally {
            startMutation.countDown()
            beforeRefill.set(null)
            result.cancel()
        }
    }

    private fun seedHistory(): Seed = database.runInTransaction(
        Callable {
            // Each individual row fits comfortably; the aggregate exceeds a real 2 MiB window.
            val artwork = "https://example.com/art/" + "x".repeat(4096)
            val streams = List(1000) { index ->
                StreamEntity(
                    serviceId = 0,
                    url = "https://example.com/video/$index",
                    title = "Video $index",
                    streamType = StreamType.VIDEO_STREAM,
                    duration = 600,
                    uploader = "Channel",
                    thumbnailUrl = artwork
                ).also { stream ->
                    stream.uid = database.streamDAO().insert(stream)
                    database.streamHistoryDAO().insert(StreamHistoryEntity(stream.uid, WATCHED_AT.minusSeconds(index.toLong()), 2, PROFILE))
                    database.streamStateDAO().insert(StreamStateEntity(stream.uid, progress(index), PROFILE))
                }
            }
            val victim = streams.last()
            database.streamHistoryDAO().insert(StreamHistoryEntity(victim.uid, WATCHED_AT, 7, OTHER_PROFILE))
            database.streamStateDAO().insert(StreamStateEntity(victim.uid, OTHER_PROGRESS, OTHER_PROFILE))
            val playlist = PlaylistEntity(name = "Quick add", isThumbnailPermanent = false, thumbnailStreamId = streams.first().uid, displayIndex = 0, profileId = PROFILE)
            playlist.uid = database.playlistDAO().insert(playlist)
            database.playlistStreamDAO().insert(PlaylistStreamEntity(playlist.uid, streams.first().uid, 0))
            Seed(streams, playlist.uid)
        }
    )

    private fun row(entry: StreamStatisticsEntry) = HistoryRow(entry.streamId, entry.streamEntity.title, entry.progressMillis, entry.latestAccessDate, entry.watchCount)

    private fun progress(index: Int) = 60_000L + index

    private fun stop(executor: ExecutorService): Boolean {
        executor.shutdown()
        if (executor.awaitTermination(10, SECONDS)) return true
        executor.shutdownNow()
        return executor.awaitTermination(5, SECONDS)
    }

    private data class Seed(val streams: List<StreamEntity>, val playlistId: Long)

    private data class HistoryRow(val id: Long, val title: String, val progress: Long, val latestAccess: OffsetDateTime, val watches: Long)

    /** Adds a scheduling hook around the real framework cursor; it does not fake any rows. */
    private class RefillHookFactory(
        private val hook: AtomicReference<((AbstractWindowedCursor, Boolean) -> Unit)?>
    ) : SupportSQLiteOpenHelper.Factory {
        override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
            val delegate = FrameworkSQLiteOpenHelperFactory().create(configuration)
            return object : SupportSQLiteOpenHelper by delegate {
                override val writableDatabase: SupportSQLiteDatabase get() = wrap(delegate.writableDatabase)
                override val readableDatabase: SupportSQLiteDatabase get() = wrap(delegate.readableDatabase)
            }
        }

        private fun wrap(delegate: SupportSQLiteDatabase): SupportSQLiteDatabase = object : SupportSQLiteDatabase by delegate {
            override fun query(query: SupportSQLiteQuery): Cursor = watch(query.sql, delegate.query(query))

            override fun query(query: SupportSQLiteQuery, cancellationSignal: CancellationSignal?): Cursor = watch(query.sql, delegate.query(query, cancellationSignal))

            private fun watch(sql: String, cursor: Cursor): Cursor {
                if (!sql.contains("MAX(access_date)") || !sql.contains("FROM stream_history")) return cursor
                return object : CursorWrapper(cursor) {
                    override fun moveToNext(): Boolean {
                        val windowed = cursor as? AbstractWindowedCursor
                        val window = windowed?.window
                        if (windowed != null && window != null && cursor.position >= 0 &&
                            cursor.position + 1 == window.startPosition + window.numRows &&
                            cursor.position + 1 < cursor.count
                        ) {
                            hook.getAndSet(null)?.invoke(windowed, delegate.inTransaction())
                        }
                        return super.moveToNext()
                    }
                }
            }
        }
    }

    companion object {
        private const val PROFILE = StreamHistoryEntity.DEFAULT_PROFILE_ID
        private const val OTHER_PROFILE = "other-profile"
        private const val OTHER_PROGRESS = 123_456L
        private val WATCHED_AT = OffsetDateTime.parse("2026-11-01T12:00:00Z")
    }
}
