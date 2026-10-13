package org.schabi.newpipe.views

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import org.schabi.newpipe.R

/** Enter the feed through its videos; header actions remain reachable with directional navigation. */
class FeedFocusLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : CoordinatorLayout(context, attrs) {
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            val videos = findViewById<RecyclerView>(R.id.items_list)
            val focused = videos?.focusedChild
            if (focused != null && !videos.canScrollVertically(-1)) {
                val position = videos.getChildAdapterPosition(focused)
                val layout = videos.layoutManager as? GridLayoutManager
                val firstRow = position >= 0 && if (layout == null) {
                    position == 0
                } else {
                    layout.spanSizeLookup.getSpanGroupIndex(position, layout.spanCount) == 0
                }
                if (firstRow) {
                    findViewById<AppBarLayout>(R.id.feed_header)?.setExpanded(true, false)
                    val refresh = findViewById<View>(R.id.feed_floating_refresh)
                    if (refresh?.isShown == true && refresh.isEnabled && refresh.requestFocus()) {
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

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
