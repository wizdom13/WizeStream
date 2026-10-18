package org.schabi.newpipe.local.subscription

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.libp2p.core.crypto.KeyType
import io.libp2p.core.crypto.generateKeyPair
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.sync.DeviceIdentity
import org.schabi.newpipe.sync.RoomSubscriptionSyncStore
import org.schabi.newpipe.util.ServiceHelper

@RunWith(AndroidJUnit4::class)
class SubscriptionRefreshTransactionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: AppDatabase
    private lateinit var manager: SubscriptionManager
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
        ServiceHelper.setSelectedServiceId(context, 0)
        manager = SubscriptionManager(context, SubscriptionEntity.DEFAULT_PROFILE_ID)
    }

    @After
    fun tearDown() {
        databaseField.set(null, previousDatabase)
        syncStoreField.set(null, previousSyncStore)
        database.close()
    }

    @Test
    fun failedFeedWriteRollsBackNewSubscriptionAndDoesNotJournalIt() {
        rejectFeedWrites()
        expectRefreshFailure()
        assertTrue(database.subscriptionDAO().getAllDirect().isEmpty())
        assertTrue(database.subscriptionSyncDAO().getAllRecords().isEmpty())
        assertFalse(database.inTransaction())
        allowFeedWrites()
        manager.upsertAll(listOf(channel("Recovered") to null))
        assertEquals("Recovered", stored().name)
        assertEquals(1, database.subscriptionSyncDAO().getAllRecords().size)
        assertForeignKeysValid()
    }

    @Test
    fun failedRefreshKeepsExistingMetadataSettingsAndModeMembership() {
        val existing = SubscriptionEntity(
            serviceId = 0,
            url = URL,
            name = "Original",
            notificationMode = 1,
            notificationKeywords = "lessons",
            youtubeModeMask = SubscriptionEntity.YOUTUBE_MODE_MUSIC
        )
        database.subscriptionDAO().insert(existing)
        rejectFeedWrites()
        expectRefreshFailure()
        assertEquals("Original", stored().name)
        assertEquals(SubscriptionEntity.YOUTUBE_MODE_MUSIC, stored().youtubeModeMask)
        assertEquals("lessons", stored().notificationKeywords)
        assertTrue(database.subscriptionSyncDAO().getAllRecords().isEmpty())
        allowFeedWrites()
        manager.upsertAll(listOf(channel("Updated") to null))
        assertEquals("Updated", stored().name)
        assertEquals(1, stored().notificationMode)
        assertEquals("lessons", stored().notificationKeywords)
        assertEquals(SubscriptionEntity.YOUTUBE_MODE_ALL, stored().youtubeModeMask)
        database.query("SELECT COUNT(*) FROM feed_last_updated", null).use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
        assertForeignKeysValid()
    }

    private fun rejectFeedWrites() {
        database.openHelper.writableDatabase.execSQL(
            "CREATE TEMP TRIGGER reject_feed_update BEFORE INSERT ON feed_last_updated " +
                "BEGIN SELECT RAISE(ABORT, 'feed write fixture'); END"
        )
    }

    private fun allowFeedWrites() {
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_feed_update")
    }

    private fun expectRefreshFailure() {
        try {
            manager.upsertAll(listOf(channel("Should roll back") to null))
            fail("The feed write must fail")
        } catch (expected: SQLiteException) {
            assertTrue(expected.message.orEmpty().contains("feed write fixture"))
        }
    }

    private fun stored(): SubscriptionEntity {
        val entity = database.subscriptionDAO().getSubscriptionDirect(0, URL)
        assertNotNull(entity)
        return requireNotNull(entity)
    }

    private fun channel(name: String): ChannelInfo {
        val link = ListLinkHandler(URL, URL, "fixture", emptyList(), emptyList())
        return ChannelInfo(0, "fixture", URL, URL, name, link).apply {
            description = "Offline channel fixture"
            subscriberCount = 100
        }
    }

    private fun assertForeignKeysValid() {
        database.query("PRAGMA foreign_key_check", null).use {
            assertFalse(it.moveToFirst())
        }
    }

    companion object {
        private const val URL = "https://www.youtube.com/channel/transaction-fixture"
    }
}
