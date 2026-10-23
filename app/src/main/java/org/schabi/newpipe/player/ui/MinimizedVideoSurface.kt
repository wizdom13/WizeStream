package org.schabi.newpipe.player.ui

import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import org.schabi.newpipe.views.ExpandableSurfaceView

/** Moves the existing decoder surface; the player, queue and playback position stay intact. */
class MinimizedVideoSurface {
    private var surface: ExpandableSurfaceView? = null
    private var parent: ViewGroup? = null
    private var params: ViewGroup.LayoutParams? = null
    private var index = 0

    fun show(view: ExpandableSurfaceView, host: FrameLayout) {
        if (surface === view && view.parent === host) return
        restore()
        val previousParent = view.parent as? ViewGroup ?: return
        surface = view
        parent = previousParent
        params = view.layoutParams
        index = previousParent.indexOfChild(view)
        previousParent.removeView(view)
        view.setMiniPlayerMode(true)
        host.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER))
    }

    fun restore() {
        val view = surface ?: return
        (view.parent as? ViewGroup)?.removeView(view)
        view.setMiniPlayerMode(false)
        parent?.let { it.addView(view, index.coerceAtMost(it.childCount), params) }
        surface = null
        parent = null
        params = null
    }
}
