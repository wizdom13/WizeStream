package org.schabi.newpipe.local.subscription

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.local.feed.service.FeedUpdateInfo
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.testUtil.TestDatabase

@RunWith(AndroidJUnit4::class)
class SubscriptionHandleStorageTest {
    private lateinit var database: AppDatabase
    private lateinit var manager: SubscriptionManager
    private val profile = ProfileManager.DEFAULT_PROFILE_ID

    @Before
    fun setup() {
        database = TestDatabase.createReplacingNewPipeDatabase()
        manager = SubscriptionManager(ApplicationProvider.getApplicationContext(), profile)
    }

    @After
    fun close() {
        database.close()
    }

    @Test
    fun metadataRefreshReplacesHandleButMissingMetadataPreservesIt() {
        val dao = database.subscriptionDAO()
        val channel = SubscriptionEntity(serviceId = 0, url = "https://www.youtube.com/channel/test", name = "Display", handle = "@Old", profileId = profile)
        channel.uid = dao.insert(channel)
        dao.updateChannelInfoForProfile(profile, 0, channel.url!!, "Display", null, null, 10, "@New").blockingAwait()
        assertEquals("@New", dao.getSubscription(channel.uid).handle)
        dao.updateChannelInfoForProfile(profile, 0, channel.url!!, "Display", null, null, 10).blockingAwait()
        assertEquals("@New", dao.getSubscription(channel.uid).handle)
        manager.updateFromInfo(FeedUpdateInfo(channel.uid, 0, "", "Display", null, channel.url!!, 0, 1, null, null, emptyList(), emptyList()))
        assertEquals("@New", dao.getSubscription(channel.uid).handle)
        manager.updateFromInfo(FeedUpdateInfo(channel.uid, 0, "", "Display", null, channel.url!!, 0, 1, null, null, emptyList(), emptyList(), "@Latest"))
        assertEquals("@Latest", dao.getSubscription(channel.uid).handle)
        assertEquals(1, dao.getSubscriptionsFilteredForProfile(profile, "@LATEST").blockingFirst().size)
        assertEquals(0, dao.getSubscriptionsFilteredForProfile("another-profile", "@LATEST").blockingFirst().size)
    }
}
