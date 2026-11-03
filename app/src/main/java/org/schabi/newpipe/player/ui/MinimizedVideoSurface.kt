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

    /**
     * Moves [view] into [host].
     *
     * @return true when this call reparented the surface, false when it was already hosted there
     *         or had no parent to detach. Callers can use the result to rebind the decoder output
     *         only after an actual move, instead of on every refresh.
     */
    fun show(view: ExpandableSurfaceView, host: FrameLayout): Boolean {
        if (surface === view && view.parent === host) return false
        restore()
        val previousParent = view.parent as? ViewGroup ?: return false
        surface = view
        parent = previousParent
        params = view.layoutParams
        index = previousParent.indexOfChild(view)
        previousParent.removeView(view)
        view.setMiniPlayerMode(true)
        host.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER))
        return true
    }

    /** @return true when the surface is currently attached to a mini-player host. */
    fun isHosting(): Boolean = surface?.parent != null

    /**
     * Moves the surface back to its original parent.
     *
     * @return true when a hosted surface was moved back, false when there was nothing to restore.
     */
    fun restore(): Boolean {
        val view = surface ?: return false
        (view.parent as? ViewGroup)?.removeView(view)
        view.setMiniPlayerMode(false)
        parent?.let { it.addView(view, index.coerceAtMost(it.childCount), params) }
        surface = null
        parent = null
        params = null
        return true
    }
}
