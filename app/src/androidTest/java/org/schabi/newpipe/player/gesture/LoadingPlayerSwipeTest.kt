package org.schabi.newpipe.player.gesture

import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.test.core.app.ActivityScenario
import com.google.android.material.bottomsheet.BottomSheetBehavior
import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.about.AboutActivity

class LoadingPlayerSwipeTest {
    @Test
    fun loadingContentAllowsDragAndLoadedContentKeepsItsTouchEvents() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                for (loadingVisible in listOf(false, true)) {
                    val parent = CoordinatorLayout(activity)
                    val sheet = FrameLayout(activity)
                    val content = FrameLayout(activity).apply { id = R.id.detail_content_root_layout }
                    val loading = View(activity).apply {
                        id = R.id.loading_progress_bar
                        visibility = if (loadingVisible) View.VISIBLE else View.GONE
                    }
                    content.addView(loading, FrameLayout.LayoutParams(100, 100))
                    sheet.addView(content, FrameLayout.LayoutParams(-1, -1))
                    val behavior = CustomBottomSheetBehavior(activity, null).apply {
                        state = BottomSheetBehavior.STATE_EXPANDED
                    }
                    parent.addView(sheet, CoordinatorLayout.LayoutParams(-1, -1).apply { this.behavior = behavior })
                    activity.setContentView(parent)
                    parent.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY))
                    parent.layout(0, 0, 600, 1000)
                    val location = IntArray(2)
                    content.getLocationOnScreen(location)
                    val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 50f, 50f, 0)
                    down.offsetLocation(location[0].toFloat(), location[1].toFloat())
                    behavior.onInterceptTouchEvent(parent, sheet, down)
                    val move = MotionEvent.obtain(0, 100, MotionEvent.ACTION_MOVE, 50f, 250f, 0)
                    move.offsetLocation(location[0].toFloat(), location[1].toFloat())
                    assertEquals(loadingVisible, behavior.onInterceptTouchEvent(parent, sheet, move))
                    down.recycle()
                    move.recycle()
                }
            }
        }
    }
}
