package org.schabi.newpipe.fragments.list.search

import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

class SearchResultOrderingTest {
    @Test
    fun laterPagesAreSortedWithEarlierResultsAndDuplicatesRemoved() {
        val short = video("short", 60)
        val long = video("long", 20 * 3600)
        val longest = video("longest", 48 * 3600)
        val pages = listOf(short, long, video("long", 20 * 3600), longest)
        assertEquals(listOf(longest, long, short), SearchResultOrdering.arrange(pages, 1, 0, 0))
        assertEquals(listOf(short, long, longest), SearchResultOrdering.arrange(pages, 2, 0, 0))
        assertEquals(listOf(short, long, longest), SearchResultOrdering.arrange(pages, 0, 0, 0))
    }

    @Test
    fun fifteenToFiftyHoursIncludesBoundariesAndExcludesUnknownAndLive() {
        val fifteen = video("15", 15 * 3600)
        val fifty = video("50", 50 * 3600)
        val items = listOf(video("short", 60), fifteen, fifty, video("too long", 51 * 3600), video("unknown", -1), video("live", 20 * 3600, StreamType.LIVE_STREAM))
        assertEquals(listOf(fifty, fifteen), SearchResultOrdering.arrange(items, 1, 15 * 3600, 50 * 3600))
    }

    @Test
    fun equalDurationsAreStableAndUnknownItemsStayAtEndInEitherOrder() {
        val unknown = video("unknown", -1)
        val first = video("first", 120)
        val second = video("second", 120)
        assertEquals(listOf(first, second, unknown), SearchResultOrdering.arrange(listOf(unknown, first, second), 1, 0, 0))
        assertEquals(listOf(first, second, unknown), SearchResultOrdering.arrange(listOf(unknown, first, second), 2, 0, 0))
    }

    @Test
    fun decimalHoursAndUnboundedRangeAreSupported() {
        assertEquals(5400L, SearchResultOrdering.hoursToSeconds("1.5"))
        assertEquals(0L, SearchResultOrdering.hoursToSeconds(""))
        val long = video("long", 30 * 3600)
        assertEquals(listOf(long), SearchResultOrdering.arrange(listOf(video("short", 60), long), 0, 15 * 3600, 0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsReversedRange() {
        SearchResultOrdering.arrange(emptyList(), 1, 50 * 3600, 15 * 3600)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonFiniteDurationInput() {
        SearchResultOrdering.hoursToSeconds("NaN")
    }

    @Test
    fun sameUrlInDifferentServicesRemainsDistinct() {
        val first = video("same", 60)
        val second = StreamInfoItem(1, "same", "same", StreamType.VIDEO_STREAM).apply { duration = 60 }
        assertEquals(2, SearchResultOrdering.arrange(listOf(first, second), 0, 0, 0).size)
    }

    private fun video(url: String, duration: Long, type: StreamType = StreamType.VIDEO_STREAM): InfoItem = StreamInfoItem(0, url, url, type).apply { this.duration = duration }
}
