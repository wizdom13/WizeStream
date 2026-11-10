package org.schabi.newpipe.local.feed

import java.lang.ref.WeakReference
import org.schabi.newpipe.local.feed.item.StreamItem
import org.schabi.newpipe.local.feed.service.FeedEventManager.Event

/** A successful refresh can reach the UI before its last database snapshot. */
class FeedRefreshCompletion internal constructor(originalIds: Set<Long>) {
    private var pendingIds: Set<Long>? = originalIds

    internal fun announceIfNew(items: List<StreamItem>): Boolean {
        val originalIds = pendingIds ?: return false
        if (items.none { it.isHighlighted && it.getId() !in originalIds }) return false
        pendingIds = null
        return true
    }
}

/** Updated on the main thread alongside the feed state delivered to the view. */
internal class FeedRefreshCompletions {
    private var latestItems: WeakReference<List<StreamItem>>? = null
    private var latestIds: Set<Long>? = null
    private var refreshStartIds: Set<Long>? = null
    private var previousEvent: Event = Event.IdleEvent
    private var completion: FeedRefreshCompletion? = null

    fun update(event: Event, items: List<StreamItem>?): FeedRefreshCompletion? {
        when (event) {
            is Event.ProgressEvent -> {
                if (previousEvent !is Event.ProgressEvent) refreshStartIds = latestIds
                completion = null
            }

            is Event.SuccessResultEvent -> if (event !== previousEvent) {
                val originalIds = if (previousEvent is Event.ProgressEvent) refreshStartIds else latestIds
                completion = originalIds?.let(::FeedRefreshCompletion)
                refreshStartIds = null
            }

            is Event.ErrorResultEvent -> {
                completion = null
                refreshStartIds = null
            }

            Event.IdleEvent -> refreshStartIds = null
        }
        previousEvent = event
        if (items != null && latestItems?.get() !== items) {
            latestItems = WeakReference(items)
            latestIds = items.mapTo(hashSetOf(), StreamItem::getId)
        }
        return completion
    }
}
