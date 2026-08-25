package org.schabi.newpipe.player.ui

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R

@RunWith(AndroidJUnit4::class)
class PlayerSecondaryControlsLayoutTest {
    @Test
    fun captionControlStaysVisibleAtNarrowPhoneWidth() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.LightTheme)
            val root = LayoutInflater.from(context)
                .inflate(R.layout.player, FrameLayout(context), false)

            val secondaryControls =
                root.findViewById<HorizontalScrollView>(R.id.secondaryControls)
            val caption = root.findViewById<View>(R.id.captionTextView)

            secondaryControls.visibility = View.VISIBLE
            caption.visibility = View.VISIBLE

            // Stress the row with every optional secondary action visible. Captions must remain in
            // the initial viewport while later actions are still reachable by horizontal scrolling.
            listOf(
                R.id.playWithKodi,
                R.id.openInBrowser,
                R.id.share,
                R.id.learningNoteButton,
                R.id.sleepTimerButton,
                R.id.equalizerButton,
                R.id.listenModeButton,
                R.id.switchMute,
                R.id.fullScreenButton
            ).forEach { root.findViewById<View>(it).visibility = View.VISIBLE }

            measureAndLayout(root, dp(context, 320), dp(context, 180))

            val content = secondaryControls.getChildAt(0)
            val captionContainer = caption.parent as View
            val captionRightInContent = captionContainer.left + caption.right

            assertEquals(0, secondaryControls.scrollX)
            assertTrue(caption.width >= dp(context, 50))
            assertTrue(captionRightInContent <= secondaryControls.width)
            assertTrue(content.width > secondaryControls.width)
        }
    }

    private fun measureAndLayout(root: View, widthPixels: Int, heightPixels: Int) {
        val width = View.MeasureSpec.makeMeasureSpec(widthPixels, View.MeasureSpec.EXACTLY)
        val height = View.MeasureSpec.makeMeasureSpec(heightPixels, View.MeasureSpec.EXACTLY)
        root.measure(width, height)
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
