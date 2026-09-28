package org.schabi.newpipe.local.subscription

import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.database.feed.model.FeedGroupEntity

class GroupChannelsViewModelTest {
    @Test
    fun allGroupUsesTheAllSubscriptionsSentinel() {
        assertEquals(-1L, FeedGroupEntity.GROUP_ALL_ID)
    }
}
