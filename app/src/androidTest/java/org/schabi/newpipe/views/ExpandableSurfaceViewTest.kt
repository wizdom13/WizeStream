package org.schabi.newpipe.views

import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExpandableSurfaceViewTest {
    @Test
    fun clearingPreviousAspectRatioFillsTheNewVideoContainer() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val surface = ExpandableSurfaceView(instrumentation.targetContext, null)
            surface.setHeights(180, 180)
            surface.setAspectRatio(4f / 3f)

            measure(surface, 320, 180)
            assertEquals(240, surface.measuredWidth)

            surface.clearAspectRatio()
            measure(surface, 320, 180)

            assertEquals(0f, surface.videoAspectRatio, 0f)
            assertEquals(320, surface.measuredWidth)
            assertEquals(180, surface.measuredHeight)
        }
    }

    private fun measure(view: View, width: Int, height: Int) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
    }
}
