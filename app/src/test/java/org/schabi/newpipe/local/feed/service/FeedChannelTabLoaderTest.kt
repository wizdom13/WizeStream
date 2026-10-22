package org.schabi.newpipe.local.feed.service

import java.io.IOException
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.channel.ChannelTabInfo
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.search.filter.FilterItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

class FeedChannelTabLoaderTest {
    private val now = OffsetDateTime.parse("2026-10-22T00:00:00Z")
    private val videos = tab("videos")
    private val shorts = tab("shorts")

    @Test
    fun failedContinuationKeepsFirstPageAndStillLoadsOtherTabs() {
        val upload = item("upload")
        val short = item("short")
        val failure = IOException("continuation unavailable")
        val errors = mutableListOf<Throwable>()
        val result = FeedChannelTabLoader.load(
            listOf(videos, shorts),
            { tab -> first(tab, if (tab == videos) upload else short, if (tab == videos) Page("next") else null) },
            { _, _ -> throw failure },
            { false },
            now.minusWeeks(13),
            errors
        )
        assertEquals(listOf(upload, short), result)
        assertEquals(listOf(failure), errors)
    }

    @Test
    fun failedFirstTabDoesNotHideLaterUploads() {
        val upload = item("upload")
        val errors = mutableListOf<Throwable>()
        val failure = IOException("shorts unavailable")
        val result = FeedChannelTabLoader.load(
            listOf(shorts, videos),
            { tab -> if (tab == shorts) throw failure else first(tab, upload) },
            { _, _ -> error("Unexpected continuation") },
            { false },
            now.minusWeeks(13),
            errors
        )
        assertEquals(listOf(upload), result)
        assertEquals(listOf(failure), errors)
    }

    @Test
    fun successfulPagesKeepExtractionWarningsAndRespectCancellation() {
        val first = item("first")
        val second = item("second")
        val warning = IOException("one malformed item")
        var cancelled = false
        val errors = mutableListOf<Throwable>()
        val result = FeedChannelTabLoader.load(
            listOf(videos, shorts),
            { tab -> first(tab, first, Page("next")) },
            { _, _ ->
                cancelled = true
                InfoItemsPage<InfoItem>(listOf(second), Page("another"), listOf(warning))
            },
            { cancelled },
            now.minusWeeks(13),
            errors
        )
        assertEquals(listOf(first, second), result)
        assertEquals(listOf(warning), errors)
    }

    private fun tab(name: String) = ListLinkHandler(name, name, name, listOf(FilterItem(0, name)), emptyList())

    private fun item(id: String) = StreamInfoItem(0, id, id, StreamType.VIDEO_STREAM).apply {
        uploadDate = DateWrapper(now)
    }

    private fun first(tab: ListLinkHandler, item: StreamInfoItem, page: Page? = null) = ChannelTabInfo(0, tab).apply {
        relatedItems = listOf(item)
        nextPage = page
    }
}
