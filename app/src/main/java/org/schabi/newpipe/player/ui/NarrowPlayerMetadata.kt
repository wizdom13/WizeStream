package org.schabi.newpipe.player.ui

import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import org.schabi.newpipe.databinding.PlayerBinding

/** Gives fullscreen metadata its own row when controls would consume a narrow window. */
internal class NarrowPlayerMetadata(private val binding: PlayerBinding) {
    private var scroll: HorizontalScrollView? = null
    private var metadataPosition: Position? = null
    private var controlsPosition: Position? = null
    private var metadataControlsParams: ViewGroup.LayoutParams? = null

    fun update(fullscreen: Boolean) {
        val width = binding.root.width.takeIf { it > 0 }?.let {
            it / binding.root.resources.displayMetrics.density
        } ?: binding.root.resources.configuration.screenWidthDp.toFloat()
        if (!fullscreen || width >= 600) {
            restore()
            return
        }
        if (scroll != null) return
        val focused = binding.root.findFocus()
        metadataPosition = position(binding.metadataView)
        controlsPosition = position(binding.primaryControls)
        metadataControlsParams = binding.metadataControls.layoutParams
        (binding.metadataView.parent as ViewGroup).removeView(binding.metadataView)
        val controls = requireNotNull(controlsPosition)
        controls.parent.removeView(binding.primaryControls)
        controls.parent.addView(
            binding.metadataView,
            controls.index,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        val actions = HorizontalScrollView(binding.root.context).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
        }
        actions.addView(
            binding.primaryControls,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        controls.parent.addView(
            actions,
            controls.index + 1,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        binding.metadataControls.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        scroll = actions
        focused?.requestFocus()
    }

    fun restore() {
        val actions = scroll ?: return
        val focused = binding.root.findFocus()
        val metadata = requireNotNull(metadataPosition)
        val controls = requireNotNull(controlsPosition)
        actions.removeView(binding.primaryControls)
        controls.parent.removeView(actions)
        controls.parent.removeView(binding.metadataView)
        controls.parent.addView(binding.primaryControls, controls.index, controls.params)
        metadata.parent.addView(binding.metadataView, metadata.index, metadata.params)
        binding.metadataControls.layoutParams = metadataControlsParams
        scroll = null
        metadataPosition = null
        controlsPosition = null
        metadataControlsParams = null
        focused?.requestFocus()
    }

    private fun position(view: View): Position {
        val parent = view.parent as ViewGroup
        return Position(parent, parent.indexOfChild(view), view.layoutParams)
    }

    private data class Position(val parent: ViewGroup, val index: Int, val params: ViewGroup.LayoutParams)
}
