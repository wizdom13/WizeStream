package org.schabi.newpipe.local.feed

import org.junit.Assert.assertEquals
import org.junit.Test

class GroupChannelsNavigationPolicyTest {
    @Test
    fun groupChannelsUsesActivityLevelFragmentHost() {
        assertEquals(
            GroupChannelsNavigationHost.ACTIVITY,
            GroupChannelsNavigationHost.forOpenedFeedGroup()
        )
    }
}
