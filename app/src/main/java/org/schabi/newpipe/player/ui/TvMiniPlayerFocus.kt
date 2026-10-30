package org.schabi.newpipe.player.ui

import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetBehavior

/** Expose only the visible part of the player to remote navigation. */
object TvMiniPlayerFocus {
    @JvmStatic
    fun apply(root: ViewGroup, details: View, overlay: ViewGroup, state: Int) {
        val collapsed = state == BottomSheetBehavior.STATE_COLLAPSED
        val expanded = state == BottomSheetBehavior.STATE_EXPANDED
        root.descendantFocusability = focusability(collapsed || expanded)
        // Wide layouts place the related-items pane beside details in a shared container.
        var content = details as ViewGroup
        while (content.parent is ViewGroup && content.parent !== root) {
            content = content.parent as ViewGroup
        }
        content.descendantFocusability = focusability(expanded)
        overlay.descendantFocusability = focusability(collapsed)
    }

    private fun focusability(enabled: Boolean): Int = if (enabled) ViewGroup.FOCUS_AFTER_DESCENDANTS else ViewGroup.FOCUS_BLOCK_DESCENDANTS
}
