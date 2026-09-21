package org.schabi.newpipe.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.local.subscription.FeedGroupIcon
import org.schabi.newpipe.testUtil.TestDatabase

@RunWith(AndroidJUnit4::class)
class SelectFeedGroupFragmentProfileIsolationTest {
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
    fun selectorOnlyReturnsGroupsFromRequestedProfile() {
        val profileA = "profile-a"
        val profileB = "profile-b"
        database.feedGroupDAO().insert(
            FeedGroupEntity(
                uid = 0,
                name = "Work",
                icon = FeedGroupIcon.WORK,
                profileId = profileA
            )
        )
        database.feedGroupDAO().insert(
            FeedGroupEntity(
                uid = 0,
                name = "Personal",
                icon = FeedGroupIcon.PERSON,
                profileId = profileB
            )
        )

        val groupsA = SelectFeedGroupFragment
            .feedGroupsForProfile(database, profileA)
            .blockingFirst()
        val groupsB = SelectFeedGroupFragment
            .feedGroupsForProfile(database, profileB)
            .blockingFirst()

        assertEquals(listOf("Work"), groupsA.map(FeedGroupEntity::name))
        assertEquals(listOf("Personal"), groupsB.map(FeedGroupEntity::name))
    }
}
