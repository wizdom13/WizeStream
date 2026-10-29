package org.schabi.newpipe.player.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewTreeObserver
import android.widget.TextView
import org.schabi.newpipe.R

/** Feedback belongs to the player surface, never to an application-wide toast window. */
class PlayerQualityOverlay {
    private var label: TextView? = null
    private var root: View? = null
    private val resized = View.OnLayoutChangeListener { view, _, top, _, bottom, _, _, _, _ ->
        if (bottom - top < 120 * view.resources.displayMetrics.density) clear()
    }
    private val detached = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) = Unit
        override fun onViewDetachedFromWindow(view: View) = clear()
    }
    private val visibilityChanged = ViewTreeObserver.OnGlobalLayoutListener {
        val anchor = root
        if (anchor != null && (!anchor.isShown || anchor.findViewById<View>(R.id.surfaceView) == null)) clear()
    }
    private val hide = Runnable { clear() }

    fun show(anchor: View, message: String) {
        clear()
        val target = anchor.findViewById<TextView>(R.id.playerQualityFeedback) ?: return
        val density = anchor.resources.displayMetrics.density
        // Do not cover a minimized player's artwork or controls.
        if (!anchor.isShown || anchor.height < 120 * density || anchor.findViewById<View>(R.id.surfaceView) == null) return
        target.background = GradientDrawable().apply {
            setColor(0xCC202020.toInt())
            cornerRadius = 8 * density
        }
        target.setTextColor(Color.WHITE)
        target.text = message
        target.visibility = View.VISIBLE
        label = target
        root = anchor
        anchor.addOnLayoutChangeListener(resized)
        anchor.addOnAttachStateChangeListener(detached)
        anchor.viewTreeObserver.addOnGlobalLayoutListener(visibilityChanged)
        target.postDelayed(hide, 2_000)
    }

    fun clear() {
        label?.let {
            it.removeCallbacks(hide)
            it.visibility = View.GONE
        }
        root?.removeOnLayoutChangeListener(resized)
        root?.removeOnAttachStateChangeListener(detached)
        root?.viewTreeObserver?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(visibilityChanged)
        root = null
        label = null
    }
}
