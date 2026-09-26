package org.schabi.newpipe.local.feed

import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.local.feed.service.FeedLoadService

class FeedFailureDetailsTest {
    @Test
    fun extractsUniqueSubscriptionIdsFromRequestErrors() {
        val errors = listOf(
            FeedLoadService.RequestException(42L, "42:url", IllegalStateException("first")),
            IllegalArgumentException("unrelated"),
            FeedLoadService.RequestException(7L, "7:url", IllegalStateException("second")),
            FeedLoadService.RequestException(42L, "42:url", IllegalStateException("duplicate"))
        )

        assertEquals(listOf(42L, 7L), FeedFailureDetails.subscriptionIds(errors))
    }

    @Test
    fun ignoresNonRequestErrors() {
        assertEquals(
            emptyList<Long>(),
            FeedFailureDetails.subscriptionIds(listOf(IllegalStateException("generic")))
        )
    }
}
