package org.schabi.newpipe.local.feed

import java.lang.ref.WeakReference
import org.schabi.newpipe.local.feed.item.StreamItem
import org.schabi.newpipe.local.feed.service.FeedEventManager.Event
import org.schabi.newpipe.local.feed.service.FeedEventManager.Event.ErrorResultEvent
import org.schabi.newpipe.local.feed.service.FeedEventManager.Event.ProgressEvent
import org.schabi.newpipe.local.feed.service.FeedEventManager.Event.SuccessResultEvent

/** One announcement shared by all snapshots of a completed refresh, including late ones. */
class FeedRefreshNotice(private val previousItemIds: Set<Long> = emptySet()) {
    private var consumed = false

    fun isNewItem(streamId: Long): Boolean = streamId !in previousItemIds

    fun consume(hasNewItems: Boolean): Boolean {
        if (consumed || !hasNewItems) return false
        consumed = true
        return true
    }
}

internal class FeedRefreshNoticeTracker {
    private var result: SuccessResultEvent? = null
    private var notice: FeedRefreshNotice? = null
    private var refreshing = false
    private var initialItemIds: Set<Long>? = null
    private var lastItemIds: Set<Long> = emptySet()
    private var lastItems: WeakReference<List<StreamItem>>? = null

    fun update(event: Event, items: List<StreamItem>? = null): FeedRefreshNotice? {
        when (event) {
            is ProgressEvent -> {
                if (!refreshing) initialItemIds = lastItemIds
                result = null
                notice = null
            }

            is ErrorResultEvent -> {
                initialItemIds = null
                result = null
                notice = null
            }

            is SuccessResultEvent -> if (result !== event) {
                result = event
                notice = FeedRefreshNotice(initialItemIds ?: lastItemIds)
            }

            else -> if (refreshing) initialItemIds = null // Explicit cancellation.
        }
        refreshing = event is ProgressEvent
        // Progress-only emissions reuse the list. Index IDs only for real snapshots,
        // and avoid retaining complete lists for previously selected groups.
        if (items != null && lastItems?.get() !== items) {
            lastItemIds = items.mapTo(hashSetOf()) { it.streamWithState.stream.uid }
            lastItems = WeakReference(items)
        }
        return notice
    }
}
