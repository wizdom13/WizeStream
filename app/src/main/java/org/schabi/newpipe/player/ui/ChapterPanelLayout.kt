package org.schabi.newpipe.player.ui

import android.view.View
import android.widget.RelativeLayout
import kotlin.math.roundToInt

/** Limits chapter coverage while retaining the queue's full-size layout. */
object ChapterPanelLayout {
    @JvmStatic
    fun apply(panel: View, width: Int, height: Int, chapters: Boolean) {
        if (width <= 0 || height <= 0) return
        val density = panel.resources.displayMetrics.density
        val params = panel.layoutParams as RelativeLayout.LayoutParams
        val wide = width / density >= 600
        val horizontalMargins = params.leftMargin + params.rightMargin
        val verticalMargins = params.topMargin + params.bottomMargin
        params.width = if (chapters && wide) {
            minOf((width * 0.42f).roundToInt(), (360 * density).roundToInt())
        } else {
            (width - horizontalMargins).coerceAtLeast(1)
        }
        params.height = if (chapters && !wide) {
            (height * 0.6f).roundToInt().coerceAtLeast(1)
        } else {
            (height - verticalMargins).coerceAtLeast(1)
        }
        params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, if (chapters && wide) RelativeLayout.TRUE else 0)
        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, if (chapters && !wide) RelativeLayout.TRUE else 0)
        panel.layoutParams = params
    }
}
