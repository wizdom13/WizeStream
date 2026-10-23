package org.schabi.newpipe.player.ui

import android.view.ViewGroup
import org.schabi.newpipe.databinding.FragmentVideoDetailBinding

/** Keeps D-pad navigation out of the underlying detail page during TV fullscreen playback. */
class TvFullscreenFocusGuard(binding: FragmentVideoDetailBinding) {
    private val groups = listOfNotNull(
        binding.detailContentRootLayout,
        binding.viewPager,
        binding.relatedItemsLayout,
        binding.overlayLayout
    )
    private var saved: List<FocusState>? = null

    fun update(fullscreen: Boolean, tv: Boolean) {
        if (fullscreen && tv) {
            if (saved != null) return
            saved = groups.map { FocusState(it, it.descendantFocusability, it.isFocusable, it.isFocusableInTouchMode) }
            groups.forEach {
                it.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                it.isFocusable = false
                it.clearFocus()
            }
        } else {
            saved?.forEach {
                it.view.descendantFocusability = it.descendants
                it.view.isFocusable = it.focusable
                it.view.isFocusableInTouchMode = it.touchFocusable
            }
            saved = null
        }
    }

    private data class FocusState(
        val view: ViewGroup,
        val descendants: Int,
        val focusable: Boolean,
        val touchFocusable: Boolean
    )
}
