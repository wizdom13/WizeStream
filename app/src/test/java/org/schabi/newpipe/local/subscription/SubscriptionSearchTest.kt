package org.schabi.newpipe.local.subscription

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.database.subscription.SubscriptionEntity

class SubscriptionSearchTest {
    private val channel = SubscriptionEntity(name = "Travel Adventures", handle = "@Äbc_Trips")

    @Test
    fun matchesNamesAndUnicodeHandlesWithOptionalAt() {
        listOf("travel", "ADVENTURES", "@äBC", "äbc", "  @ÄBC_TRIPS ", "", "@").forEach {
            assertTrue(it, SubscriptionSearch.matches(it, channel))
        }
        assertFalse(SubscriptionSearch.matches("other", channel))
        assertFalse(SubscriptionSearch.matches("%", channel))
    }

    @Test
    fun existingSubscriptionsWithoutHandlesStillMatchNames() {
        assertTrue(SubscriptionSearch.matches("old", SubscriptionEntity(name = "Old channel")))
        assertFalse(SubscriptionSearch.matches("handle", SubscriptionEntity(name = "Old channel")))
        assertFalse(SubscriptionSearch.matches("test", SubscriptionEntity()))
    }
}
