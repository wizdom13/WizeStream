package org.schabi.newpipe.local.subscription

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Transfers a visible channel or separator between the browsing and editing layouts. */
internal data class SubscriptionScrollAnchor(val key: String, val offset: Int) {
    companion object {
        fun capture(recycler: RecyclerView, keyAt: (Int) -> String?): SubscriptionScrollAnchor? {
            val manager = recycler.layoutManager as? LinearLayoutManager ?: return null
            val first = manager.findFirstVisibleItemPosition()
            val last = manager.findLastVisibleItemPosition()
            if (first == RecyclerView.NO_POSITION || last == RecyclerView.NO_POSITION) return null
            for (position in first..last) {
                val key = keyAt(position) ?: continue
                val view = manager.findViewByPosition(position) ?: continue
                return SubscriptionScrollAnchor(key, manager.getDecoratedTop(view) - recycler.paddingTop)
            }
            return null
        }
    }
}
