package org.schabi.newpipe.local.bookmark

import android.app.Application
import android.database.AbstractWindowedCursor
import androidx.preference.PreferenceManager
import androidx.room.Room
import androidx.room.RoomDatabase
import io.reactivex.rxjava3.core.Notification
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.processors.UnicastProcessor
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.feed.FeedDatabaseManager
import org.schabi.newpipe.local.feed.service.FeedLoadManager
import org.schabi.newpipe.local.feed.service.FeedUpdateInfo
import org.schabi.newpipe.local.playlist.LocalPlaylistManager
import org.schabi.newpipe.local.playlist.RemotePlaylistManager
import org.schabi.newpipe.util.ServiceHelper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class PlaylistRefreshLoadingTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val name = "playlist-refresh-${UUID.randomUUID()}.db"
    private val queries = Executors.newFixedThreadPool(2)
    private val transactions = Executors.newSingleThreadExecutor()
    private val writer = Executors.newSingleThreadExecutor()
    private val disposables = CompositeDisposable()
    private val releases = mutableListOf<CountDownLatch>()
    private lateinit var database: AppDatabase
    private val databaseField = NewPipeDatabase::class.java.getDeclaredField("databaseInstance").apply { isAccessible = true }
    private var previousDatabase: Any? = null

    @Volatile
    private var onQuery: ((String, List<Any?>) -> Unit)? = null

    @Before
    fun setUp() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        ServiceHelper.setSelectedServiceId(context, 0)
        database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .allowMainThreadQueries()
            .setQueryExecutor(queries)
            .setTransactionExecutor(transactions)
            .setQueryCallback({ sql, args -> onQuery?.invoke(sql, args) }, { it.run() })
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
        releases.forEach { it.countDown() }
        disposables.dispose()
        onQuery = null
        writer.shutdown()
        writer.awaitTermination(5, TimeUnit.SECONDS)
        databaseField.set(null, previousDatabase)
        database.close()
        transactions.shutdown()
        queries.shutdown()
        transactions.awaitTermination(5, TimeUnit.SECONDS)
        queries.awaitTermination(5, TimeUnit.SECONDS)
        context.deleteDatabase(name)
    }

    @Test
    fun opensCachedPlaylistsWhileAFeedChannelWriteAndRefreshAreStillActive() {
        val cover = stream("cover", "https://example.com/cached.jpg")
        playlist("Z local", 2, cover, count = 2)
        playlist("alpha", 0, cover)
        remote("beta", 0)
        playlist("Other profile", 0, cover, profile = OTHER_PROFILE)
        remote("Other remote", 0, OTHER_PROFILE)

        val subscription = SubscriptionEntity(serviceId = 0, url = "https://example.com/channel", name = "Channel")
        subscription.uid = database.subscriptionDAO().insert(subscription)
        val entered = CountDownLatch(1)
        val release = gate()
        val committed = CountDownLatch(1)
        val feed = FeedDatabaseManager(context, PROFILE)
        val manager = FeedLoadManager(context, PROFILE) { _, info, _ ->
            // Use the real feed persistence inside the channel's still-open Room transaction.
            feed.upsertAll(info.uid, info.streams)
            entered.countDown()
            check(release.await(10, TimeUnit.SECONDS))
            val afterCommit: () -> Unit = { committed.countDown() }
            afterCommit
        }
        // doOnSubscribe can precede registration; buffer the update until the subscriber is ready.
        val source = UnicastProcessor.create<Notification<FeedUpdateInfo>>()
        val subscribed = CountDownLatch(1)
        val refresh = manager.storeResults(source.doOnSubscribe { subscribed.countDown() }).test()
        disposables.add(refresh)
        assertTrue(subscribed.await(5, TimeUnit.SECONDS))
        source.onNext(Notification.createOnNext(update(subscription)))
        assertTrue(entered.await(5, TimeUnit.SECONDS))

        val bookmarks = merged().test()
        try {
            bookmarks.awaitCount(1).assertNoErrors()
            assertFalse("The cached list must arrive before the feed write is released", bookmarks.values().isEmpty())
            assertEquals(listOf("alpha", "beta", "Z local"), bookmarks.values().first().map { it.orderingName })
            val local = bookmarks.values().first().filterIsInstance<PlaylistMetadataEntry>()
            assertEquals(listOf(1L, 2L), local.map { it.streamCount })
            assertTrue(local.all { it.thumbnailUrl == "https://example.com/cached.jpg" })
            assertEquals(1L, release.count)
            refresh.assertNotComplete()

            // A new profile/view receives only its own cached data, even during the same write.
            bookmarks.cancel()
            val other = merged(OTHER_PROFILE).test()
            try {
                other.awaitCount(1).assertNoErrors()
                assertEquals(listOf("Other profile", "Other remote"), other.values().first().map { it.orderingName })
            } finally {
                other.cancel()
            }
        } finally {
            release.countDown()
            bookmarks.cancel()
        }
        assertTrue(committed.await(5, TimeUnit.SECONDS))
        refresh.assertNotComplete()
        source.onComplete()
        refresh.awaitDone(5, TimeUnit.SECONDS).assertComplete().assertNoErrors()
        assertEquals(1, bookmarks.values().size)
        assertEquals(1, database.feedDAO().getMemberships(subscription.uid).size)
    }

    @Test
    fun observesPlaylistChangesMadeBetweenTheCachedSnapshotAndObserverRegistration() {
        val cover = stream("cover", "https://example.com/before.jpg")
        val extra = stream("extra", "https://example.com/after.jpg")
        val first = playlist("Before", 0, cover)
        playlist("Second", 1, cover)
        val changed = AtomicBoolean()
        val result = LocalPlaylistManager(database, PROFILE).playlists
            .doOnNext {
                if (changed.compareAndSet(false, true)) {
                    database.runInTransaction {
                        database.playlistDAO().update(first.copy(name = "After", displayIndex = 2, thumbnailStreamId = extra))
                        database.playlistStreamDAO().insert(PlaylistStreamEntity(first.uid, extra, 1))
                    }
                }
            }
            .test()
        try {
            result.awaitCount(2).assertNoErrors()
            assertEquals(2, result.values().size)
            val latest = result.values().last()
            assertEquals(listOf("Second", "After"), latest.map { it.orderingName })
            assertEquals(listOf(1L, 2L), latest.map { it.streamCount })
            assertEquals(listOf("https://example.com/before.jpg", "https://example.com/after.jpg"), latest.map { it.thumbnailUrl })
        } finally {
            result.cancel()
        }
    }

    @Test
    fun cancellationDuringTheInitialReadCannotPublishIntoAReplacedView() {
        val cover = stream("cover", null)
        playlist("Original profile", 0, cover)
        playlist("Replacement profile", 0, cover, profile = OTHER_PROFILE)
        val entered = CountDownLatch(1)
        val release = gate()
        val intercepted = AtomicBoolean()
        onQuery = { sql, args ->
            if (isMetadataQuery(sql) && args.contains(PROFILE) && intercepted.compareAndSet(false, true)) {
                entered.countDown()
                try {
                    check(release.await(10, TimeUnit.SECONDS))
                } catch (_: InterruptedException) {
                    // Cancelling subscribeOn may interrupt the read; still let it return so the
                    // test verifies that a completed snapshot cannot reach the cancelled view.
                }
            }
        }
        val old = LocalPlaylistManager(database, PROFILE).playlists.test()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            old.cancel()
            release.countDown()
            val replacement = LocalPlaylistManager(database, OTHER_PROFILE).playlists.take(1).test()
            replacement.awaitDone(5, TimeUnit.SECONDS).assertComplete().assertNoErrors()
            assertEquals(listOf("Replacement profile"), replacement.values().single().map { it.orderingName })
            old.assertNoValues()
        } finally {
            old.cancel()
            release.countDown()
        }
    }

    @Test
    fun unrelatedFeedWritesDoNotReplaceAnEditedListButRealMetadataChangesStillArrive() {
        val cover = stream("cover", "https://example.com/cover.jpg")
        val first = playlist("First", 0, cover)
        playlist("Second", 1, cover)
        val reads = AtomicInteger()
        val observed = CountDownLatch(1)
        val invalidated = CountDownLatch(1)
        onQuery = { sql, _ ->
            if (isMetadataQuery(sql)) {
                when (reads.incrementAndGet()) {
                    2 -> observed.countDown()
                    3 -> invalidated.countDown()
                }
            }
        }
        val duplicate = CountDownLatch(1)
        val values = AtomicInteger()
        val result = LocalPlaylistManager(database, PROFILE).playlists
            .doOnNext { if (values.incrementAndGet() > 1) duplicate.countDown() }
            .test()
        try {
            result.awaitCount(1).assertNoErrors()
            assertTrue(observed.await(5, TimeUnit.SECONDS))
            // The editor mutates these rows before saving. Deduplication must compare copies of
            // their original values, otherwise an unrelated invalidation resets an in-flight edit.
            result.values().first().first().displayIndex = 99
            val subscription = SubscriptionEntity(serviceId = 0, url = "https://example.com/channel", name = "Channel")
            subscription.uid = database.subscriptionDAO().insert(subscription)
            database.runInTransaction { FeedDatabaseManager(context, PROFILE).upsertAll(subscription.uid, update(subscription).streams) }
            assertTrue(invalidated.await(5, TimeUnit.SECONDS))
            assertFalse("Unchanged playlist metadata must not replace the displayed list", duplicate.await(200, TimeUnit.MILLISECONDS))

            database.playlistDAO().update(first.copy(displayIndex = 99))
            result.awaitCount(2).assertNoErrors()
            assertEquals(2, result.values().size)
            assertEquals(listOf("Second", "First"), result.values().last().map { it.orderingName })
            assertEquals(listOf(1L, 99L), result.values().last().map { it.displayIndex })
        } finally {
            result.cancel()
        }
    }

    @Test
    fun emptyAndRemoteOnlyProfilesProduceACompleteInitialList() {
        remote("Saved remote", 0)
        val saved = merged().take(1).test()
        saved.awaitDone(5, TimeUnit.SECONDS).assertComplete().assertNoErrors()
        assertEquals(listOf("Saved remote"), saved.values().single().map { it.orderingName })
        val empty = merged(OTHER_PROFILE).take(1).test()
        empty.awaitDone(5, TimeUnit.SECONDS).assertComplete().assertNoErrors().assertValue(emptyList())
    }

    @Test
    fun oversizedMetadataKeepsOneCoherentSnapshotAcrossCursorWindows() {
        val count = 320
        val cover = stream("cover", "https://example.com/before.jpg")
        val extra = stream("extra", "https://example.com/after.jpg")
        val names = List(count) { "$it ${"x".repeat(16_384)}" }
        val playlists = database.runInTransaction(
            java.util.concurrent.Callable {
                names.mapIndexed { index, title -> playlist(title, index.toLong(), cover) }
            }
        )
        playlist("Other profile", 0, cover, profile = OTHER_PROFILE)
        database.query("SELECT name FROM playlists WHERE profile_id = ? ORDER BY display_index", arrayOf(PROFILE)).use { cursor ->
            assertEquals(count, cursor.count)
            assertTrue(cursor is AbstractWindowedCursor && cursor.window.numRows < count)
        }
        val entered = CountDownLatch(1)
        val release = gate()
        val write = writer.submit {
            database.runInTransaction {
                playlists.forEach { item ->
                    database.playlistDAO().update(item.copy(name = "After ${item.name}", displayIndex = count - item.displayIndex, thumbnailStreamId = extra))
                    database.playlistStreamDAO().insert(PlaylistStreamEntity(item.uid, extra, 1))
                }
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS))
            }
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        val readStarted = CountDownLatch(1)
        onQuery = { sql, _ -> if (isMetadataQuery(sql)) readStarted.countDown() }
        val emitted = CountDownLatch(1)
        val result = LocalPlaylistManager(database, PROFILE).playlists.doOnNext { emitted.countDown() }.test()
        try {
            assertTrue(readStarted.await(5, TimeUnit.SECONDS))
            assertFalse("A multi-window result must wait for a consistent transaction", emitted.await(200, TimeUnit.MILLISECONDS))
            release.countDown()
            write.get(5, TimeUnit.SECONDS)
            result.awaitCount(1).assertNoErrors()
            val snapshot = result.values().first()
            assertEquals(count, snapshot.size)
            assertTrue(snapshot.all { it.streamCount == 2L && it.thumbnailUrl == "https://example.com/after.jpg" })
            assertEquals((1L..count.toLong()).toList(), snapshot.map { it.displayIndex })
            assertTrue(snapshot.map { it.orderingName } == names.reversed().map { "After $it" })
        } finally {
            release.countDown()
            result.cancel()
        }
    }

    private fun merged(profile: String = PROFILE) = MergedPlaylistManager.getMergedOrderedPlaylists(
        LocalPlaylistManager(database, profile),
        RemotePlaylistManager(database, profile)
    )

    private fun gate() = CountDownLatch(1).also { releases += it }

    private fun isMetadataQuery(sql: String) = sql.contains("streamCount") && sql.contains("FROM playlists")

    private fun stream(id: String, thumbnail: String?) = database.streamDAO().insert(
        StreamEntity(serviceId = 0, url = "https://example.com/$id", title = id, streamType = StreamType.VIDEO_STREAM, duration = 10, uploader = "Channel", thumbnailUrl = thumbnail)
    )

    private fun playlist(title: String, index: Long, cover: Long, count: Int = 1, profile: String = PROFILE): PlaylistEntity {
        val entity = PlaylistEntity(name = title, isThumbnailPermanent = false, thumbnailStreamId = cover, displayIndex = index, profileId = profile)
        entity.uid = database.playlistDAO().insert(entity)
        repeat(count) { database.playlistStreamDAO().insert(PlaylistStreamEntity(entity.uid, cover, it)) }
        return entity
    }

    private fun remote(title: String, index: Long, profile: String = PROFILE) = database.playlistRemoteDAO().insert(
        PlaylistRemoteEntity(serviceId = 0, orderingName = title, url = "https://example.com/$title", thumbnailUrl = null, uploader = "Channel", displayIndex = index, streamCount = 3, profileId = profile)
    )

    private fun update(subscription: SubscriptionEntity) = FeedUpdateInfo(
        uid = subscription.uid,
        notificationMode = subscription.notificationMode,
        notificationKeywords = "",
        name = subscription.name!!,
        avatarUrl = null,
        url = subscription.url!!,
        serviceId = 0,
        youtubeModeMask = SubscriptionEntity.YOUTUBE_MODE_REGULAR,
        description = null,
        subscriberCount = null,
        streams = listOf(
            StreamInfoItem(0, "https://example.com/new-feed-video", "New feed video", StreamType.VIDEO_STREAM).apply {
                uploaderName = "Channel"
                // Ordinary videos without a date are excluded by feed retention. Keep this a real
                // streams/feed write so the test exercises both persistence and invalidation.
                uploadDate = DateWrapper(OffsetDateTime.now())
            }
        ),
        errors = emptyList()
    )

    companion object {
        private const val PROFILE = SubscriptionEntity.DEFAULT_PROFILE_ID
        private const val OTHER_PROFILE = "other-profile"
    }
}
