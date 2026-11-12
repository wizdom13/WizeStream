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
        val previousParams = view.layoutParams
        val previousIndex = previousParent.indexOfChild(view)
        previousParent.removeView(view)

        // A surface callback can synchronously reattach the view while removeView() is running.
        // Respect that new parent instead of adding the same child to a second ViewGroup.
        val parentAfterRemoval = view.parent
        if (parentAfterRemoval != null && parentAfterRemoval !== host) return false

        view.setMiniPlayerMode(true)
        if (view.parent == null) {
            host.addView(
                view,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
                )
            )
        }
        if (view.parent !== host) return false

        surface = view
        parent = previousParent
        params = previousParams
        index = previousIndex
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
        val originalParent = parent

        if (view.parent !== originalParent) {
            (view.parent as? ViewGroup)?.removeView(view)
        }
        view.setMiniPlayerMode(false)

        // removeView() may synchronously restore SurfaceView ownership. Only add the view when it
        // is still detached, and never add it again when it already reached the original parent.
        if (view.parent == null) {
            originalParent?.addView(view, index.coerceAtMost(originalParent.childCount), params)
        }
        val restored = view.parent === originalParent

        surface = null
        parent = null
        params = null
        return restored
    }
}
