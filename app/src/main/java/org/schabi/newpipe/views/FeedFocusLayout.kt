package org.schabi.newpipe.views

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
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
            if (videos != null && videos.isShown) {
                // RecyclerView itself can accept focus on older Android versions. Request a
                // visible row explicitly so entering the page selects an actionable video.
                val indices = if (direction == View.FOCUS_BACKWARD) {
                    videos.childCount - 1 downTo 0
                } else {
                    0 until videos.childCount
                }
                for (index in indices) {
                    val row = videos.getChildAt(index)
                    if (row.isShown && row.requestFocus(direction, previouslyFocusedRect)) return true
                }
            }
        }
        return super.onRequestFocusInDescendants(direction, previouslyFocusedRect)
    }
}
