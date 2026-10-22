package org.schabi.newpipe.local.feed.service

import java.time.OffsetDateTime
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.channel.ChannelTabInfo
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/** Keep usable uploads when another tab or a later continuation fails. */
internal object FeedChannelTabLoader {
    fun load(
        tabs: List<ListLinkHandler>,
        fetchFirst: (ListLinkHandler) -> ChannelTabInfo,
        fetchMore: (ListLinkHandler, Page) -> InfoItemsPage<InfoItem>,
        isCancelled: () -> Boolean,
        oldestAllowedDate: OffsetDateTime,
        errors: MutableList<Throwable>
    ): List<InfoItem> {
        val collected = mutableListOf<InfoItem>()
        for (tab in tabs) {
            if (isCancelled()) break
            try {
                val first = fetchFirst(tab)
                errors.addAll(first.errors)
                collected.addAll(first.relatedItems)
                var items = first.relatedItems
                var nextPage = first.nextPage
                var pagesLoaded = 0
                while (!isCancelled() && FeedExtractionPlanner.shouldLoadFeedContinuation(
                        pageItemCount = items.size,
                        sourceUploadDates = items.filterIsInstance<StreamInfoItem>()
                            .mapNotNull { it.uploadDate?.offsetDateTime() },
                        hasNextPage = Page.isValid(nextPage),
                        continuationPagesLoaded = pagesLoaded,
                        oldestAllowedDate = oldestAllowedDate
                    )
                ) {
                    val page = fetchMore(tab, nextPage ?: break)
                    errors.addAll(page.errors)
                    collected.addAll(page.items)
                    items = page.items
                    nextPage = page.nextPage
                    pagesLoaded++
                }
            } catch (error: Exception) {
                errors.add(error)
            }
        }
        return collected
    }
}
