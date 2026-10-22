package org.schabi.newpipe.player.ui

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.PlayerBinding

class ChapterPanelLayoutTest {
    @Test
    fun chaptersLeaveVideoVisibleAndQueueRestoresFullSizeAfterRotation() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.DarkTheme)
            val binding = PlayerBinding.inflate(LayoutInflater.from(context))
            val panel = binding.itemsListPanel
            val density = context.resources.displayMetrics.density
            panel.visibility = View.VISIBLE
            for (direction in listOf(View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL)) {
                binding.root.layoutDirection = direction
                for ((widthDp, heightDp) in listOf(360 to 640, 840 to 640, 640 to 360, 360 to 200)) {
                    val width = (widthDp * density).toInt()
                    val height = (heightDp * density).toInt()
                    ChapterPanelLayout.apply(panel, width, height, true)
                    measure(binding.root, width, height)
                    val params = panel.layoutParams as android.widget.RelativeLayout.LayoutParams
                    if (widthDp >= 600) {
                        assertTrue(panel.width < width / 2)
                        assertEquals(width - params.rightMargin, panel.right)
                    } else {
                        assertTrue(panel.height < height * 0.7f)
                        assertEquals(height - params.bottomMargin, panel.bottom)
                    }
                    ChapterPanelLayout.apply(panel, width, height, false)
                    measure(binding.root, width, height)
                    assertEquals(width - params.leftMargin - params.rightMargin, panel.width)
                    assertEquals(height - params.topMargin - params.bottomMargin, panel.height)
                }
            }
        }
    }

    private fun measure(view: View, width: Int, height: Int) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
    }
}
