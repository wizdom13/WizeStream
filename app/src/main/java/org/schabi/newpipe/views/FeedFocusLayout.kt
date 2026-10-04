package org.schabi.newpipe.views

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.recyclerview.widget.RecyclerView
import org.schabi.newpipe.R

/** Enter the feed through its videos; header actions remain reachable with directional navigation. */
class FeedFocusLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : CoordinatorLayout(context, attrs) {
    override fun onRequestFocusInDescendants(direction: Int, previouslyFocusedRect: Rect?): Boolean {
        if (!isInTouchMode) {
            val videos = findViewById<RecyclerView>(R.id.items_list)
            if (videos != null && videos.isShown && videos.childCount > 0 &&
                videos.requestFocus(direction, previouslyFocusedRect)
            ) {
                return true
            }
        }
        return super.onRequestFocusInDescendants(direction, previouslyFocusedRect)
    }
}
