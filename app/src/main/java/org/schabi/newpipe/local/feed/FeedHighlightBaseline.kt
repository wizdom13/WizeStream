package org.schabi.newpipe.local.feed

import java.time.OffsetDateTime

/** Keeps newly fetched items highlighted until the next refresh starts. */
internal class FeedHighlightBaseline {
    private var initialized = false
    private var refreshing = false
    private var previousUpdate: OffsetDateTime? = null
    private var baseline: OffsetDateTime? = null

    fun update(oldestUpdate: OffsetDateTime?, isRefreshing: Boolean): OffsetDateTime? {
        if (isRefreshing && !refreshing) {
            // Metadata can advance before the throttled item snapshot arrives.
            // Prefer the last observed timestamp from before this refresh.
            baseline = if (initialized) previousUpdate else oldestUpdate
        }
        previousUpdate = oldestUpdate
        refreshing = isRefreshing
        initialized = true
        return baseline
    }
}
