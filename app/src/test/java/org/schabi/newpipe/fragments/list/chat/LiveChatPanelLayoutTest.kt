package org.schabi.newpipe.fragments.list.chat

import android.app.Application
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.appbar.AppBarLayout
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.schabi.newpipe.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 35], application = Application::class, qualifiers = "w400dp-h800dp-mdpi")
class LiveChatPanelLayoutTest {
    @Test
    fun messageListNestedScrollingHidesControlsAndRevealsThemOnReverseScroll() {
        val panel = panel()
        val range = panel.header.totalScrollRange
        assertTrue("The controls must have a scroll range", range > 0)
        assertTrue(panel.list.isNestedScrollingEnabled)

        assertTrue("The ListView must reach its CoordinatorLayout", panel.list.startNestedScroll(View.SCROLL_AXIS_VERTICAL))
        try {
            val up = IntArray(2)
            assertTrue(panel.list.dispatchNestedPreScroll(0, range, up, null))
            assertEquals(range, up[1])
            assertEquals(-range, panel.behavior.topAndBottomOffset)
            assertTrue("The control panel must scroll completely above the messages", panel.header.bottom <= 0)

            val down = IntArray(2)
            assertTrue(panel.list.dispatchNestedPreScroll(0, -range, down, null))
            assertEquals(-range, down[1])
            assertEquals(0, panel.behavior.topAndBottomOffset)
            assertEquals(0, panel.header.top)
        } finally {
            panel.list.stopNestedScroll()
        }
    }

    @Test
    fun incomingRowsAndProgrammaticSelectionDoNotMoveExpandedOrCollapsedControls() {
        val panel = panel()
        for (collapsed in listOf(false, true)) {
            if (collapsed) {
                assertTrue(panel.list.startNestedScroll(View.SCROLL_AXIS_VERTICAL))
                try {
                    panel.list.dispatchNestedPreScroll(0, panel.header.totalScrollRange, IntArray(2), null)
                } finally {
                    panel.list.stopNestedScroll()
                }
            }
            val previousOffset = panel.behavior.topAndBottomOffset
            assertEquals(if (collapsed) -panel.header.totalScrollRange else 0, previousOffset)

            panel.adapter.add("A newly received message")
            panel.adapter.notifyDataSetChanged()
            panel.list.setSelection(panel.adapter.count - 1)
            panel.layout()

            // Touch mode can scroll to a row without making it the keyboard selection.
            assertEquals(panel.adapter.count - 1, panel.list.lastVisiblePosition)
            assertEquals("Automatic following must not act like a user gesture", previousOffset, panel.behavior.topAndBottomOffset)
            assertFalse(panel.list.hasNestedScrollingParent())
        }
    }

    @Test
    fun compactControlsFitNarrowShortPanelsWithLargeTextAndBothLayoutDirections() {
        for (locale in listOf(Locale.ENGLISH, Locale("ar"))) {
            for (fontScale in listOf(1f, 1.8f)) {
                val panel = panel(locale, fontScale, heightDp = 240)
                panel.root.findViewById<TextView>(R.id.liveChatStatus).setText(R.string.live_chat_unavailable)
                panel.root.findViewById<TextView>(R.id.liveChatRecordingStatus).apply {
                    setText(R.string.live_chat_capture_saving)
                    visibility = View.VISIBLE
                }
                for (retryVisible in listOf(false, true)) {
                    panel.root.findViewById<View>(R.id.liveChatRetry).visibility = if (retryVisible) View.VISIBLE else View.GONE
                    panel.layout()
                    val description = "locale=$locale fontScale=$fontScale retry=$retryVisible"
                    val capture = panel.bounds(R.id.liveChatCapture)
                    val more = panel.bounds(R.id.liveChatMore)
                    val status = panel.bounds(R.id.liveChatStatus)
                    val inset = panel.dp(16)
                    val target = panel.dp(48)

                    for (control in listOf(capture, more)) {
                        assertTrue("$description: control touch width", control.width() >= target)
                        assertTrue("$description: control touch height", control.height() >= target)
                        assertTrue("$description: start inset", control.left >= inset)
                        assertTrue("$description: end inset", control.right <= panel.root.width - inset)
                        assertTrue("$description: control remains inside the header", control.bottom <= panel.header.bottom)
                    }
                    val gap = if (capture.left < more.left) more.left - capture.right else capture.left - more.right
                    assertTrue("$description: buttons must remain separated", gap >= panel.dp(8))
                    assertFalse("$description: status and capture must not overlap", Rect.intersects(status, capture))
                    assertFalse("$description: status and overflow must not overlap", Rect.intersects(status, more))
                    assertEquals(locale.language == "ar", more.left < capture.left)

                    if (retryVisible) {
                        val retry = panel.bounds(R.id.liveChatRetry)
                        assertTrue("$description: retry touch height", retry.height() >= target)
                        assertTrue("$description: retry start inset", retry.left >= inset)
                        assertTrue("$description: retry end inset", retry.right <= panel.root.width - inset)
                        assertFalse("$description: retry must not overlap capture", Rect.intersects(retry, capture))
                    }
                    val visibleMessages = Rect(0, 0, panel.root.width, panel.root.height)
                    assertTrue("$description: messages must retain visible space", visibleMessages.intersect(panel.bounds(R.id.liveChatMessages)))
                    assertTrue("$description: message viewport height", visibleMessages.height() > 0)
                }
            }
        }
    }

    private fun panel(locale: Locale = Locale.ENGLISH, fontScale: Float = 1f, heightDp: Int = 480): Panel {
        val application = RuntimeEnvironment.getApplication()
        val configuration = Configuration(application.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
            this.fontScale = fontScale
        }
        val context = ContextThemeWrapper(application.createConfigurationContext(configuration), R.style.LightTheme)
        val root = LayoutInflater.from(context).inflate(R.layout.live_chat_panel, null) as CoordinatorLayout
        root.layoutDirection = if (locale.language == "ar") View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        val list = root.findViewById<ListView>(R.id.liveChatMessages)
        val adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, MutableList(80) { "Message $it" })
        list.adapter = adapter
        return Panel(root, root.findViewById(R.id.liveChatHeader), list, adapter, heightDp).also { it.layout() }
    }

    private class Panel(
        val root: CoordinatorLayout,
        val header: AppBarLayout,
        val list: ListView,
        val adapter: ArrayAdapter<String>,
        private val heightDp: Int
    ) {
        val behavior: AppBarLayout.Behavior
            get() = (header.layoutParams as CoordinatorLayout.LayoutParams).behavior as AppBarLayout.Behavior

        fun dp(value: Int) = (value * root.resources.displayMetrics.density).toInt()

        fun layout() {
            shadowOf(Looper.getMainLooper()).idle()
            root.measure(
                View.MeasureSpec.makeMeasureSpec(dp(320), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(dp(heightDp), View.MeasureSpec.EXACTLY)
            )
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
            shadowOf(Looper.getMainLooper()).idle()
        }

        fun bounds(id: Int): Rect = Rect().also {
            val view = root.findViewById<View>(id)
            view.getDrawingRect(it)
            root.offsetDescendantRectToMyCoords(view, it)
        }
    }
}
