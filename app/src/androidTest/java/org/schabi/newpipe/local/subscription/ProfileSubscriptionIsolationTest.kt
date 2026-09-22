package org.schabi.newpipe.local.subscription

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.testUtil.TestDatabase

@RunWith(AndroidJUnit4::class)
class ProfileSubscriptionIsolationTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = TestDatabase.createReplacingNewPipeDatabase()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun sameChannelCanExistIndependentlyInTwoProfiles() {
        val profileA = "profile-a"
        val profileB = "profile-b"
        val url = "https://example.com/channel/shared"
        val managerA = SubscriptionManager(context, profileA)
        val managerB = SubscriptionManager(context, profileB)

        managerA.insertSubscription(
            SubscriptionEntity(serviceId = 0, url = url, name = "Work channel")
        )
        managerB.insertSubscription(
            SubscriptionEntity(serviceId = 0, url = url, name = "Personal channel")
        )

        val subscriptionsA = managerA.subscriptions().blockingFirst()
        val subscriptionsB = managerB.subscriptions().blockingFirst()
        assertEquals(1, subscriptionsA.size)
        assertEquals(1, subscriptionsB.size)
        assertEquals("Work channel", subscriptionsA.single().name)
        assertEquals("Personal channel", subscriptionsB.single().name)
        assertEquals(profileA, subscriptionsA.single().profileId)
        assertEquals(profileB, subscriptionsB.single().profileId)
        assertEquals(
            1,
            managerA.getSubscriptionFlowable(0, url).blockingFirst().size
        )
        assertEquals(
            1,
            managerB.getSubscriptionFlowable(0, url).blockingFirst().size
        )

        managerA.deleteSubscription(0, url).blockingAwait()
        assertTrue(managerA.subscriptions().blockingFirst().isEmpty())
        assertEquals(1, managerB.subscriptions().blockingFirst().size)
    }

    @Test
    fun channelGroupsRejectMembershipsFromAnotherProfile() {
        val profileA = "profile-a"
        val profileB = "profile-b"
        val subscriptionDao = database.subscriptionDAO()
        val groupDao = database.feedGroupDAO()

        val subscriptionA = SubscriptionEntity(
            serviceId = 0,
            url = "https://example.com/channel/a",
            name = "A",
            profileId = profileA
        )
        subscriptionA.uid = subscriptionDao.insert(subscriptionA)
        val subscriptionB = SubscriptionEntity(
            serviceId = 0,
            url = "https://example.com/channel/b",
            name = "B",
            profileId = profileB
        )
        subscriptionB.uid = subscriptionDao.insert(subscriptionB)

        val groupAId = groupDao.insert(
            FeedGroupEntity(
                uid = 0,
                name = "Work",
                icon = FeedGroupIcon.WORK,
                profileId = profileA
            )
        )
        val groupBId = groupDao.insert(
            FeedGroupEntity(
                uid = 0,
                name = "Personal",
                icon = FeedGroupIcon.PERSON,
                profileId = profileB
            )
        )

        groupDao.updateSubscriptionsForGroupForProfile(
            profileA,
            groupAId,
            listOf(subscriptionB.uid)
        )
        assertTrue(
            groupDao.getSubscriptionIdsForDirectForProfile(profileA, groupAId).isEmpty()
        )

        groupDao.updateSubscriptionsForGroupForProfile(
            profileA,
            groupAId,
            listOf(subscriptionA.uid)
        )
        assertEquals(
            listOf(subscriptionA.uid),
            groupDao.getSubscriptionIdsForDirectForProfile(profileA, groupAId)
        )
        assertTrue(
            groupDao.getSubscriptionIdsForDirectForProfile(profileB, groupBId).isEmpty()
        )

        val secondGroupAId = groupDao.insert(
            FeedGroupEntity(
                uid = 0,
                name = "Study",
                icon = FeedGroupIcon.STUDY,
                profileId = profileA
            )
        )
        groupDao.setGroupsForSubscriptionForProfile(
            profileA,
            subscriptionA.uid,
            listOf(groupAId, secondGroupAId, groupBId)
        )
        assertEquals(
            listOf(groupAId, secondGroupAId),
            groupDao.getGroupIdsForSubscriptionForProfile(
                profileA,
                subscriptionA.uid
            ).blockingFirst()
        )
        assertTrue(
            groupDao.getGroupIdsForSubscriptionForProfile(
                profileB,
                subscriptionB.uid
            ).blockingFirst().isEmpty()
        )

        groupDao.setGroupsForSubscriptionForProfile(
            profileA,
            subscriptionA.uid,
            emptyList()
        )
        assertTrue(
            groupDao.getGroupIdsForSubscriptionForProfile(
                profileA,
                subscriptionA.uid
            ).blockingFirst().isEmpty()
        )
    }
}
