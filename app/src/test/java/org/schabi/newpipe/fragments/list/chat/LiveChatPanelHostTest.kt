package org.schabi.newpipe.fragments.list.chat

import android.app.Activity
import android.app.Application
import android.graphics.Rect
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.viewpager.widget.PagerAdapter
import com.google.android.material.appbar.AppBarLayout
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.FragmentVideoDetailBinding
import org.schabi.newpipe.util.ExtractorHelper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 35], application = Application::class, qualifiers = "w400dp-h800dp-port-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class LiveChatPanelHostTest {
    @Test
    fun initialAndIncomingRowsStayVisibleWithoutMovingExpandedDetailOrChatHeaders() {
        fixture().use { panel ->
            assertTrue("The actual detail header must be collapsible", panel.outerHeader!!.totalScrollRange > 0)
            assertEquals(0 to 0, panel.offsets())
            val offsets = panel.offsets()
            panel.followLatest()
            panel.assertLatestRowVisible("Initial messages under expanded headers")
            assertEquals("Initial following must not move either header", offsets, panel.offsets())

            panel.adapter.add("A newly received live message")
            panel.followLatest()
            panel.assertLatestRowVisible("Incoming messages under expanded headers")
            assertEquals("Incoming messages must not move either header", offsets, panel.offsets())
        }
    }

    @Test
    fun standaloneLatestRowStaysInsideThePanelWithExpandedAndCollapsedControls() {
        fixture(detailHost = false).use { panel ->
            for (collapsed in listOf(false, true)) {
                if (collapsed) panel.scroll(panel.header.totalScrollRange)
                val offset = panel.offsets()
                assertEquals(if (collapsed) -panel.header.totalScrollRange else 0, offset.second)
                panel.adapter.add("Newest standalone message, collapsed=$collapsed")
                panel.followLatest()
                panel.assertLatestRowVisible("Standalone panel, collapsed=$collapsed")
                assertEquals("Automatic following must preserve the controls", offset, panel.offsets())
            }
        }
    }

    @Test
    fun userScrollingAndHostResizingPreserveAVisibleMessageViewport() {
        fixture().use { panel ->
            val outer = requireNotNull(panel.outerHeader)
            panel.scroll(outer.totalScrollRange + panel.header.totalScrollRange)
            assertEquals("An upward message gesture hides the controls", -panel.header.totalScrollRange, panel.offsets().second)
            assertEquals("Remaining upward movement must reach the detail header", -outer.totalScrollRange, panel.offsets().first)
            panel.followLatest()
            panel.assertLatestRowVisible("After upward nested scrolling")

            panel.scroll(-panel.header.totalScrollRange)
            assertEquals("Reversing the gesture immediately reveals the controls", 0, panel.offsets().second)
            panel.followLatest()
            panel.assertLatestRowVisible("After reverse nested scrolling")

            for (expanded in listOf(true, false)) {
                outer.setExpanded(expanded, false)
                panel.layout()
                val expected = if (expanded) 0 else -outer.totalScrollRange
                assertEquals("The actual outer header must reach the requested state", expected, panel.offsets().first)
                for (height in listOf(640, 760)) {
                    val offsets = panel.offsets()
                    panel.heightDp = height
                    panel.layout()
                    panel.followLatest()
                    panel.assertLatestRowVisible("Host height=$height, outer expanded=$expanded")
                    assertEquals("Resizing and following must preserve both header offsets", offsets, panel.offsets())
                }
            }
        }
    }

    @Test
    @Config(qualifiers = "w900dp-h600dp-land-mdpi")
    fun tabletLandscapeHostAlsoKeepsInitialAndIncomingRowsInsideItsViewport() {
        fixture(widthDp = 900, heightDp = 600).use { panel ->
            assertTrue("Exercise the actual tablet landscape detail layout", panel.tabletHost)
            val offsets = panel.offsets()
            panel.followLatest()
            panel.assertLatestRowVisible("Tablet initial messages")
            panel.adapter.add("Newest tablet message")
            panel.followLatest()
            panel.assertLatestRowVisible("Tablet incoming messages")
            assertEquals("Tablet automatic following must preserve both headers", offsets, panel.offsets())
        }
    }

    private fun fixture(detailHost: Boolean = true, widthDp: Int = 400, heightDp: Int = 800): Panel {
        val controller = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val activity = controller.get().apply { setTheme(R.style.LightTheme) }
        val inflater = LayoutInflater.from(activity)
        val panel = inflater.inflate(R.layout.live_chat_panel, null) as CoordinatorLayout
        val binding = if (detailHost) FragmentVideoDetailBinding.inflate(inflater) else null
        val root = binding?.root ?: panel
        val list = panel.findViewById<ListView>(R.id.liveChatMessages)
        val adapter = ArrayAdapter(activity, android.R.layout.simple_list_item_1, MutableList(80) { "Live message $it" })
        list.adapter = adapter
        if (binding != null) {
            binding.detailThumbnailImageView.layoutParams.height = dp(root, 200)
            // VideoDetailFragment applies this offset to place metadata below the phone player.
            if (binding.relatedItemsLayout == null) {
                (binding.detailContentRootLayout.layoutParams as ViewGroup.MarginLayoutParams).topMargin = dp(root, 200)
            }
            binding.detailContentRootHiding.visibility = View.VISIBLE
            binding.detailVideoTitleView.text = "Live stream fixture"
            // Loaded streams with no extra metadata do not reserve the empty metadata panel.
            val metadataTasks = CompositeDisposable()
            ExtractorHelper.showMetaInfoInTextView(
                emptyList(),
                binding.detailMetaInfoTextView,
                binding.detailMetaInfoSeparator,
                metadataTasks
            )
            metadataTasks.dispose()
            binding.detailNavigation.visibility = View.VISIBLE
            binding.overlayLayout.visibility = View.GONE
            binding.viewPager.adapter = object : PagerAdapter() {
                override fun getCount() = 1
                override fun isViewFromObject(view: View, item: Any) = view === item
                override fun instantiateItem(container: ViewGroup, position: Int): Any = panel.also { container.addView(it) }
                override fun destroyItem(container: ViewGroup, position: Int, item: Any) = container.removeView(item as View)
            }
        }
        activity.setContentView(root)
        return Panel(controller, root, panel, list, adapter, binding, widthDp, heightDp).also { it.layout() }
    }

    private class Panel(
        private val controller: ActivityController<Activity>,
        private val root: ViewGroup,
        private val panel: CoordinatorLayout,
        private val list: ListView,
        val adapter: ArrayAdapter<String>,
        private val binding: FragmentVideoDetailBinding?,
        private val widthDp: Int,
        var heightDp: Int
    ) : AutoCloseable {
        val header: AppBarLayout = panel.findViewById(R.id.liveChatHeader)
        val outerHeader: AppBarLayout? get() = binding?.appBarLayout
        val tabletHost: Boolean get() = binding?.relatedItemsLayout != null

        fun offsets() = (outerHeader?.let { behavior(it).topAndBottomOffset } ?: 0) to behavior(header).topAndBottomOffset

        fun layout() {
            // Activity traversals and the explicit fixture layout must agree on the viewport.
            // Leaving MATCH_PARENT here would restore the window height between resize passes.
            root.layoutParams = root.layoutParams.apply {
                width = dp(root, widthDp)
                height = dp(root, heightDp)
            }
            // Allow viewport/padding listeners to settle without dispatching a user scroll.
            repeat(4) {
                shadowOf(Looper.getMainLooper()).idle()
                root.measure(
                    View.MeasureSpec.makeMeasureSpec(dp(root, widthDp), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(dp(root, heightDp), View.MeasureSpec.EXACTLY)
                )
                root.layout(0, 0, root.measuredWidth, root.measuredHeight)
                root.viewTreeObserver.dispatchOnPreDraw()
            }
        }

        fun followLatest() {
            list.setSelection(adapter.count - 1)
            layout()
        }

        fun scroll(distance: Int) {
            assertTrue("The real ListView must start its nested scroll", list.startNestedScroll(View.SCROLL_AXIS_VERTICAL))
            try {
                val consumed = IntArray(2)
                list.dispatchNestedPreScroll(0, distance, consumed, null)
                val requestedRange = if (distance >= 0) 0..distance else distance..0
                assertTrue("Nested headers must not consume the same movement twice", consumed[1] in requestedRange)
            } finally {
                list.stopNestedScroll()
            }
            layout()
        }

        fun assertLatestRowVisible(description: String) {
            val childIndex = adapter.count - 1 - list.firstVisiblePosition
            assertTrue("$description: latest row must be laid out", childIndex in 0 until list.childCount)
            val row = list.getChildAt(childIndex)
            val rowBounds = bounds(row)
            val viewport = Rect(0, 0, root.width, root.height)
            var ancestor: View? = list
            while (ancestor != null && ancestor !== root) {
                val clip = bounds(ancestor)
                if (ancestor is ViewGroup && ancestor.clipToPadding) {
                    clip.left += ancestor.paddingLeft
                    clip.top += ancestor.paddingTop
                    clip.right -= ancestor.paddingRight
                    clip.bottom -= ancestor.paddingBottom
                }
                assertTrue("$description: chat must retain visible space", viewport.intersect(clip))
                ancestor = ancestor.parent as? View
            }
            // Phone detail navigation overlays the outer Coordinator; global clipping alone
            // cannot see that sibling. Tablet navigation lives above the pager in the app bar.
            binding?.detailNavigation?.let { navigation ->
                val params = navigation.layoutParams as? CoordinatorLayout.LayoutParams
                if (params != null && (params.gravity and Gravity.VERTICAL_GRAVITY_MASK) == Gravity.BOTTOM) {
                    viewport.bottom = minOf(viewport.bottom, bounds(navigation).top)
                }
            }
            assertTrue("$description: the visible message viewport must be positive: $viewport", viewport.height() > 0)
            assertTrue("$description: newest row top $rowBounds must be inside $viewport", rowBounds.top >= viewport.top)
            assertTrue("$description: newest row bottom $rowBounds must be above navigation/clip $viewport", rowBounds.bottom <= viewport.bottom)
        }

        private fun bounds(view: View) = Rect().also {
            view.getDrawingRect(it)
            root.offsetDescendantRectToMyCoords(view, it)
        }

        override fun close() {
            controller.pause().stop().destroy()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    companion object {
        private fun dp(view: View, value: Int) = (value * view.resources.displayMetrics.density).toInt()
        private fun behavior(header: AppBarLayout) = (header.layoutParams as CoordinatorLayout.LayoutParams).behavior as AppBarLayout.Behavior
    }
}
