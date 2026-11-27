package org.schabi.newpipe.views

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ListView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.NestedScrollingChild3
import androidx.core.view.NestedScrollingChildHelper
import androidx.core.view.ViewCompat
import com.google.android.material.appbar.AppBarLayout
import org.schabi.newpipe.R

/** Shares touch scrolling with the detail header and keeps the newest chat row in view. */
class LiveChatPanelLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : CoordinatorLayout(context, attrs), NestedScrollingChild3 {
    private val scrollingChild = NestedScrollingChildHelper(this).apply {
        isNestedScrollingEnabled = true
    }
    private val upstreamConsumed = IntArray(2)
    private val compatibilityConsumed = IntArray(2)
    private val windowLocation = IntArray(2)
    private val visibleBounds = Rect()
    private var messages: ListView? = null
    private var originalBottomPadding = 0
    private var observedTree: ViewTreeObserver? = null
    private val viewportListener = ViewTreeObserver.OnPreDrawListener {
        updateMessageViewport()
        // Keep drawing during a header fling; changed padding requests the next layout.
        true
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        messages = findViewById<ListView>(R.id.liveChatMessages).also {
            originalBottomPadding = it.paddingBottom
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        observedTree = viewTreeObserver.also { it.addOnPreDrawListener(viewportListener) }
    }

    override fun onDetachedFromWindow() {
        observedTree?.takeIf { it.isAlive }?.removeOnPreDrawListener(viewportListener)
        observedTree = null
        stopNestedScroll(ViewCompat.TYPE_TOUCH)
        stopNestedScroll(ViewCompat.TYPE_NON_TOUCH)
        super.onDetachedFromWindow()
    }

    private fun updateMessageViewport() {
        val list = messages ?: return
        if (list.height == 0 || !list.getGlobalVisibleRect(visibleBounds)) return
        list.getLocationInWindow(windowLocation)
        val listBottom = windowLocation[1] + list.height
        var visibleBottom = visibleBounds.bottom
        var child: View = list
        while (true) {
            val parent = child.parent as? ViewGroup ?: break
            val params = child.layoutParams as? CoordinatorLayout.LayoutParams
            if (parent is CoordinatorLayout && params?.behavior is AppBarLayout.ScrollingViewBehavior) {
                // ScrollingViewBehavior measures for a collapsed header. Its child can extend
                // below this viewport while a header is expanded. The pager's bottom margin
                // additionally reserves the overlaid detail navigation, which is not a clip.
                parent.getLocationInWindow(windowLocation)
                val parentBottom = windowLocation[1] + parent.height - parent.paddingBottom
                visibleBottom = minOf(visibleBottom, parentBottom - params.bottomMargin)
            }
            child = parent
        }
        val padding = (originalBottomPadding + listBottom - visibleBottom)
            .coerceIn(originalBottomPadding, maxOf(originalBottomPadding, list.height - list.paddingTop))
        if (list.paddingBottom == padding) return
        val count = list.adapter?.count ?: 0
        val following = count > 0 && list.lastVisiblePosition >= count - 2
        list.setPadding(list.paddingLeft, list.paddingTop, list.paddingRight, padding)
        if (following) list.setSelection(count - 1)
    }

    override fun onStartNestedScroll(child: View, target: View, axes: Int, type: Int): Boolean = super.onStartNestedScroll(child, target, axes, type) || (axes and ViewCompat.SCROLL_AXIS_VERTICAL) != 0

    override fun onNestedScrollAccepted(child: View, target: View, axes: Int, type: Int) {
        super.onNestedScrollAccepted(child, target, axes, type)
        startNestedScroll(axes, type)
    }

    override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray, type: Int) {
        super.onNestedPreScroll(target, dx, dy, consumed, type)
        // The chat controls react first in either direction. Only the remainder reaches the
        // outer detail header, so the two app bars never consume the same movement twice.
        upstreamConsumed.fill(0)
        dispatchNestedPreScroll(dx - consumed[0], dy - consumed[1], upstreamConsumed, null, type)
        consumed[0] += upstreamConsumed[0]
        consumed[1] += upstreamConsumed[1]
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int
    ) {
        // CoordinatorLayout 1.1.0 substitutes TYPE_TOUCH in this compatibility overload.
        // Preserve the supplied type when a NestedScrollingChild2 calls through it.
        compatibilityConsumed.fill(0)
        onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, type, compatibilityConsumed)
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int,
        consumed: IntArray
    ) {
        val previousX = consumed[0]
        val previousY = consumed[1]
        super.onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, type, consumed)
        val consumedHereX = consumed[0] - previousX
        val consumedHereY = consumed[1] - previousY
        dispatchNestedScroll(
            dxConsumed + consumedHereX,
            dyConsumed + consumedHereY,
            dxUnconsumed - consumedHereX,
            dyUnconsumed - consumedHereY,
            null,
            type,
            consumed
        )
    }

    override fun onStopNestedScroll(target: View, type: Int) {
        super.onStopNestedScroll(target, type)
        stopNestedScroll(type)
    }

    override fun onNestedPreFling(target: View, velocityX: Float, velocityY: Float): Boolean = super.onNestedPreFling(target, velocityX, velocityY) || dispatchNestedPreFling(velocityX, velocityY)

    override fun onNestedFling(target: View, velocityX: Float, velocityY: Float, consumed: Boolean): Boolean {
        val handledHere = super.onNestedFling(target, velocityX, velocityY, consumed)
        val handledUpstream = dispatchNestedFling(velocityX, velocityY, consumed || handledHere)
        return handledHere || handledUpstream
    }

    override fun canScrollVertically(direction: Int): Boolean = messages?.canScrollVertically(direction) ?: super.canScrollVertically(direction)

    override fun setNestedScrollingEnabled(enabled: Boolean) {
        scrollingChild.isNestedScrollingEnabled = enabled
    }

    override fun isNestedScrollingEnabled(): Boolean = scrollingChild.isNestedScrollingEnabled

    override fun startNestedScroll(axes: Int): Boolean = startNestedScroll(axes, ViewCompat.TYPE_TOUCH)

    override fun startNestedScroll(axes: Int, type: Int): Boolean = scrollingChild.startNestedScroll(axes, type)

    override fun stopNestedScroll() = stopNestedScroll(ViewCompat.TYPE_TOUCH)

    override fun stopNestedScroll(type: Int) = scrollingChild.stopNestedScroll(type)

    override fun hasNestedScrollingParent(): Boolean = hasNestedScrollingParent(ViewCompat.TYPE_TOUCH)

    override fun hasNestedScrollingParent(type: Int): Boolean = scrollingChild.hasNestedScrollingParent(type)

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?
    ): Boolean = dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, ViewCompat.TYPE_TOUCH)

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?,
        type: Int
    ): Boolean = scrollingChild.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, type)

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?,
        type: Int,
        consumed: IntArray
    ) = scrollingChild.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, type, consumed)

    override fun dispatchNestedPreScroll(dx: Int, dy: Int, consumed: IntArray?, offsetInWindow: IntArray?): Boolean = dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, ViewCompat.TYPE_TOUCH)

    override fun dispatchNestedPreScroll(dx: Int, dy: Int, consumed: IntArray?, offsetInWindow: IntArray?, type: Int): Boolean = scrollingChild.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, type)

    override fun dispatchNestedFling(velocityX: Float, velocityY: Float, consumed: Boolean): Boolean = scrollingChild.dispatchNestedFling(velocityX, velocityY, consumed)

    override fun dispatchNestedPreFling(velocityX: Float, velocityY: Float): Boolean = scrollingChild.dispatchNestedPreFling(velocityX, velocityY)
}
