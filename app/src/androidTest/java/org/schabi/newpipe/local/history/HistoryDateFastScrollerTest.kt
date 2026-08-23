package org.schabi.newpipe.local.history

import android.view.MotionEvent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryDateFastScrollerTest {
    @Test
    fun dragMapsTrackEndpointsToHistoryEndpoints() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val view = HistoryDateFastScroller(instrumentation.targetContext)
            var selectedPosition = -1
            view.setOnPositionChangedListener { selectedPosition = it }
            view.setLabelProvider { position -> "Date $position" }
            view.setItemCount(100)
            view.measure(
                View.MeasureSpec.makeMeasureSpec(48, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, 48, 1000)

            view.onTouchEvent(event(MotionEvent.ACTION_DOWN, 18f))
            view.onTouchEvent(event(MotionEvent.ACTION_MOVE, 982f))

            assertEquals(99, selectedPosition)
            assertTrue(view.contentDescription.toString().contains("Date 99"))

            view.onTouchEvent(event(MotionEvent.ACTION_UP, 982f))
        }
    }

    private fun event(action: Int, y: Float): MotionEvent = MotionEvent.obtain(0L, 0L, action, 24f, y, 0)
}
