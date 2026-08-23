package org.schabi.newpipe.views

import android.view.KeyEvent
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SwipeControllableViewPagerTest {
    @Test
    fun disabledPagerYieldsHorizontalNavigationToItsParent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val pager = SwipeControllableViewPager(instrumentation.targetContext)
            pager.setSwipeEnabled(false)

            assertFalse(pager.isSwipeEnabled)
            assertFalse(pager.canScrollHorizontally(-1))
            assertFalse(pager.canScrollHorizontally(1))
            assertFalse(
                pager.executeKeyEvent(
                    KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)
                )
            )

            val event = MotionEvent.obtain(
                0L,
                0L,
                MotionEvent.ACTION_MOVE,
                20f,
                20f,
                0
            )
            try {
                assertFalse(pager.onInterceptTouchEvent(event))
                assertFalse(pager.onTouchEvent(event))
            } finally {
                event.recycle()
            }

            pager.setSwipeEnabled(true)
            assertTrue(pager.isSwipeEnabled)
        }
    }
}
