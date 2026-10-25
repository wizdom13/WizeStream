package org.schabi.newpipe.player.gesture

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.test.core.app.ActivityScenario
import com.google.android.material.bottomsheet.BottomSheetBehavior
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.about.AboutActivity
import org.schabi.newpipe.databinding.FragmentVideoDetailBinding

class PendingPlayerDragDispatchTest {
    @Test
    fun loadingAndAgeRestrictedPagesCollapseThroughRealTouchDispatch() {
        for (error in listOf(false, true)) {
            ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
                val collapsed = CountDownLatch(1)
                scenario.onActivity { activity ->
                    val fixture = Fixture(activity, error)
                    fixture.behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                        override fun onStateChanged(bottomSheet: View, newState: Int) {
                            if (newState == BottomSheetBehavior.STATE_COLLAPSED) collapsed.countDown()
                        }
                        override fun onSlide(bottomSheet: View, slideOffset: Float) = Unit
                    })
                    fixture.drag()
                    assertTrue("Pending page must move the sheet, error=$error", fixture.sheet.top > 0)
                }
                assertTrue("Downward swipe must minimize, error=$error", collapsed.await(5, TimeUnit.SECONDS))
            }
        }
    }

    @Test
    fun materialFallbackMissesTheSamePageWithoutAScrollingChild() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fixture = Fixture(activity, error = true, custom = false)
                fixture.drag()
                assertEquals(0, fixture.sheet.top)
                assertEquals(BottomSheetBehavior.STATE_EXPANDED, fixture.behavior.state)
            }
        }
    }

    @Test
    fun errorActionTapAndHorizontalSwipeDoNotMinimize() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            val clicked = CountDownLatch(1)
            lateinit var fixture: Fixture
            scenario.onActivity { activity ->
                fixture = Fixture(activity, error = true)
                val button = fixture.details.root.findViewById<View>(R.id.error_open_in_browser)
                button.setOnClickListener { clicked.countDown() }
                val point = IntArray(2)
                val origin = IntArray(2)
                button.getLocationOnScreen(point)
                fixture.parent.getLocationOnScreen(origin)
                val x = (point[0] - origin[0] + button.width / 2).toFloat()
                val y = (point[1] - origin[1] + button.height / 2).toFloat()
                fixture.send(MotionEvent.ACTION_DOWN, x, y, 0)
                fixture.send(MotionEvent.ACTION_UP, x, y, 100)
            }
            // View posts its click callback after ACTION_UP; allow the UI loop to run it.
            assertTrue("Error action must remain clickable", clicked.await(5, TimeUnit.SECONDS))
            scenario.onActivity {
                fixture.send(MotionEvent.ACTION_DOWN, 100f, 100f, 200)
                fixture.send(MotionEvent.ACTION_MOVE, 400f, 110f, 300)
                fixture.send(MotionEvent.ACTION_UP, 400f, 110f, 400)
                assertEquals(0, fixture.sheet.top)
                assertEquals(BottomSheetBehavior.STATE_EXPANDED, fixture.behavior.state)
            }
        }
    }

    private class Fixture(activity: AboutActivity, error: Boolean, custom: Boolean = true) {
        val parent = CoordinatorLayout(activity)
        val sheet = FrameLayout(activity)
        val details = FragmentVideoDetailBinding.inflate(activity.layoutInflater, sheet, true)
        val behavior: BottomSheetBehavior<FrameLayout> = if (custom) {
            CustomBottomSheetBehavior(activity, null)
        } else {
            BottomSheetBehavior(activity, null)
        }
        private val started = SystemClock.uptimeMillis()

        init {
            details.root.isClickable = true
            details.overlayLayout.visibility = View.GONE
            details.viewPager.visibility = View.GONE
            details.relatedItemsLayout?.visibility = View.GONE
            details.detailContentRootHiding.visibility = View.GONE
            details.detailThumbnailRootLayout.layoutParams.height = 240
            (details.detailContentRootLayout.layoutParams as ViewGroup.MarginLayoutParams).topMargin = 240
            details.detailTitleRootLayout.isClickable = false
            details.detailVideoTitleView.text = "Pending video"
            details.loadingProgressBar.visibility = if (error) View.GONE else View.VISIBLE
            details.root.findViewById<View>(R.id.error_panel).visibility = if (error) View.VISIBLE else View.GONE
            details.root.findViewById<View>(R.id.error_open_in_browser).visibility = View.VISIBLE
            behavior.peekHeight = 100
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            parent.addView(sheet, CoordinatorLayout.LayoutParams(-1, -1).apply { this.behavior = this@Fixture.behavior })
            // Match the activity's splash sibling: once dismissed it retains full-screen bounds.
            val overlay = View(activity)
            parent.addView(overlay, CoordinatorLayout.LayoutParams(-1, -1))
            activity.setContentView(parent)
            parent.measure(
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY)
            )
            parent.layout(0, 0, 800, 1200)
            overlay.visibility = View.GONE
        }

        fun drag() {
            send(MotionEvent.ACTION_DOWN, 100f, 100f, 0)
            for (step in 1..8) send(MotionEvent.ACTION_MOVE, 100f, 100f + step * 60f, step * 30L)
            send(MotionEvent.ACTION_UP, 100f, 580f, 270)
        }

        fun send(action: Int, x: Float, y: Float, elapsed: Long) {
            val event = MotionEvent.obtain(started, started + elapsed, action, x, y, 0)
            parent.dispatchTouchEvent(event)
            event.recycle()
        }
    }
}
