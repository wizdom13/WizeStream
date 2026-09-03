package org.schabi.newpipe.local.feed

import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.util.StreamListFilter

class FeedFilterPersistenceTest {
    @Test
    fun filterPreferenceIsScopedPerFeedGroup() {
        assertEquals("feed_stream_filter_-1", FeedFragment.streamFilterPreferenceKey(-1L))
        assertEquals("feed_stream_filter_42", FeedFragment.streamFilterPreferenceKey(42L))
    }

    @Test
    fun storedFilterRestoresAndUnknownValuesFallBackSafely() {
        assertEquals(StreamListFilter.LIVE, FeedFragment.restoreStreamFilter("LIVE"))
        assertEquals(StreamListFilter.SHORTS, FeedFragment.restoreStreamFilter("SHORTS"))
        assertEquals(StreamListFilter.NONE, FeedFragment.restoreStreamFilter("REMOVED_FILTER"))
        assertEquals(StreamListFilter.NONE, FeedFragment.restoreStreamFilter(null))
    }
}
