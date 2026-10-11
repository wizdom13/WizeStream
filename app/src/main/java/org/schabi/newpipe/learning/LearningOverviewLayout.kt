package org.schabi.newpipe.learning

import android.view.ViewGroup
import android.widget.LinearLayout

/** Keeps study statistics and learning progress readable in the current window. */
object LearningOverviewLayout {
    fun apply(container: LinearLayout, widthDp: Int) {
        val wide = widthDp >= 840
        container.orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        val spacing = (16 * container.resources.displayMetrics.density).toInt()
        for (index in 0 until container.childCount) {
            val child = container.getChildAt(index)
            val params = child.layoutParams as LinearLayout.LayoutParams
            params.width = if (wide) 0 else ViewGroup.LayoutParams.MATCH_PARENT
            params.weight = if (wide) 1f else 0f
            params.marginEnd = if (wide && index == 0) spacing else 0
            child.layoutParams = params
        }
    }
}
