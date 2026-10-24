package org.schabi.newpipe.local.subscription

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.libp2p.core.crypto.KeyType
import io.libp2p.core.crypto.generateKeyPair
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.sync.DeviceIdentity
import org.schabi.newpipe.sync.RoomSubscriptionSyncStore

@RunWith(AndroidJUnit4::class)
class SubscriptionBulkDeleteTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: AppDatabase
    private var previousDatabase: Any? = null
    private var previousSyncStore: Any? = null
    private val databaseField = NewPipeDatabase::class.java
        .getDeclaredField("databaseInstance").apply { isAccessible = true }
    private val syncStoreField = RoomSubscriptionSyncStore::class.java
        .getDeclaredField("instance").apply { isAccessible = true }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        previousDatabase = databaseField.get(null)
        previousSyncStore = syncStoreField.get(null)
        databaseField.set(null, database)
        val peerId = DeviceIdentity(generateKeyPair(KeyType.ED25519).first).peerId.toBase58()
        syncStoreField.set(null, RoomSubscriptionSyncStore(database, peerId))
    }

    @After
    fun tearDown() {
        databaseField.set(null, previousDatabase)
        syncStoreField.set(null, previousSyncStore)
        database.close()
    }

    @Test
    fun deletesOnlySelectedProfileRowsIncludingMusicAndJournalsEachOnce() {
        val manager = SubscriptionManager(context, "11111111-1111-4111-8111-111111111111")
        val first = insert("11111111-1111-4111-8111-111111111111", "first", SubscriptionEntity.YOUTUBE_MODE_ALL)
        val music = insert("11111111-1111-4111-8111-111111111111", "music", SubscriptionEntity.YOUTUBE_MODE_MUSIC)
        val keep = insert("11111111-1111-4111-8111-111111111111", "keep")
        val otherProfile = insert("22222222-2222-4222-8222-222222222222", "first")

        manager.deleteSubscriptions(listOf(first, music, first, otherProfile, Long.MAX_VALUE))
            .blockingAwait()

        assertEquals(listOf(keep), manager.subscriptions().blockingFirst().map { it.uid })
        assertEquals(
            listOf(otherProfile),
            SubscriptionManager(context, "22222222-2222-4222-8222-222222222222").subscriptions().blockingFirst().map { it.uid }
        )
        val sync = database.subscriptionSyncDAO()
        val changes = sync.getChangeOrigins().flatMap { sync.getChangesAfter(it, 0, 100) }
        assertEquals(2, changes.size)
        assertTrue(changes.all { it.profileId == "11111111-1111-4111-8111-111111111111" && it.changeType == "DELETE" })
        manager.deleteSubscriptions(listOf(first, music)).blockingAwait()
        assertEquals(2, sync.getChangeOrigins().sumOf { sync.getChangesAfter(it, 0, 100).size })
    }

    @Test
    fun failedBatchRollsBackAllDeletionsWithoutPublishingSyncChanges() {
        val first = insert("11111111-1111-4111-8111-111111111111", "first")
        val second = insert("11111111-1111-4111-8111-111111111111", "second")
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_bulk_delete BEFORE DELETE ON subscriptions " +
                "WHEN OLD.uid = $second BEGIN SELECT RAISE(ABORT, 'test failure'); END"
        )
        SubscriptionManager(context, "11111111-1111-4111-8111-111111111111").deleteSubscriptions(listOf(first, second))
            .test().await().assertError { true }
        assertEquals(2, database.subscriptionDAO().getAllForProfile("11111111-1111-4111-8111-111111111111").blockingFirst().size)
        assertTrue(database.subscriptionSyncDAO().getAllRecords().isEmpty())
    }

    private fun insert(
        profile: String,
        name: String,
        modes: Int = SubscriptionEntity.YOUTUBE_MODE_REGULAR
    ): Long = database.subscriptionDAO().insert(
        SubscriptionEntity(
            profileId = profile,
            serviceId = 0,
            url = "https://example.com/$name",
            name = name,
            youtubeModeMask = modes
        )
    )
}
