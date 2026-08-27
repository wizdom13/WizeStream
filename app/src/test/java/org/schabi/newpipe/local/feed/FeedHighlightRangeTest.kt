package org.schabi.newpipe.local.feed

import org.junit.Assert.assertEquals
import org.junit.Test

class FeedHighlightRangeTest {
    @Test
    fun emptyFeedDoesNotProduceAnInvalidRange() {
        assertEquals(0, calculateFeedHighlightRebindCount(1, 0, 0))
    }

    @Test
    fun rebindCountClearsStaleHighlightsAfterTheListShrinks() {
        assertEquals(3, calculateFeedHighlightRebindCount(5, 2, 3))
    }

    @Test
    fun rebindCountIncludesAllNewHighlights() {
        assertEquals(5, calculateFeedHighlightRebindCount(2, 5, 8))
    }

    @Test
    fun unchangedUnhighlightedFeedNeedsNoRebind() {
        assertEquals(0, calculateFeedHighlightRebindCount(0, 0, 8))
    }
}
