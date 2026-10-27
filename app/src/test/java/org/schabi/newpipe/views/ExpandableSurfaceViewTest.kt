package org.schabi.newpipe.views

import android.view.View.MeasureSpec
import androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
import androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ExpandableSurfaceViewTest {
    @Test
    fun shortToLandscapeVideoFitsAfterWindowRotatesBeforeHeightUpdate() {
        val surface = surface()
        surface.setHeights(1920, 1920)
        surface.setAspectRatio(9f / 16f)
        layout(surface, 1080, 1920)
        surface.setUserTransform(2f, 100f, 100f)
        surface.clearAspectRatio()
        surface.setAspectRatio(16f / 9f)
        // Insets reduce the new window height; the old portrait height has not been refreshed.
        layout(surface, 1920, 1000)
        assertEquals(1000, surface.measuredHeight)
        assertEquals(1777, surface.measuredWidth)
        assertEquals(1f, surface.scaleX, 0f)
        assertEquals(1f, surface.scaleY, 0f)
        assertEquals(0f, surface.translationX, 0f)
        assertEquals(0f, surface.translationY, 0f)
    }

    @Test
    fun landscapeVideoFitsWhileWindowRemainsPortrait() {
        val surface = surface()
        surface.setHeights(1920, 1920)
        surface.setAspectRatio(9f / 16f)
        layout(surface, 1080, 1920)
        surface.clearAspectRatio()
        surface.setAspectRatio(16f / 9f)
        layout(surface, 1080, 1920)
        assertEquals(1080, surface.measuredWidth)
        assertEquals(607, surface.measuredHeight)
    }

    @Test
    fun validVideoSizeFitsBeforeCachedHeightsAreAvailable() {
        val surface = surface()
        surface.setAspectRatio(16f / 9f)
        layout(surface, 1080, 1920)
        assertEquals(607, surface.measuredHeight)
    }

    @Test
    fun fitAfterZoomDoesNotKeepTheOldScale() {
        val surface = surface()
        surface.setHeights(800, 800)
        surface.setResizeMode(RESIZE_MODE_ZOOM)
        surface.setAspectRatio(9f / 16f)
        layout(surface, 1000, 800)
        assertTrue(surface.scaleY > 1f)
        surface.setResizeMode(RESIZE_MODE_FIT)
        layout(surface, 1000, 800)
        assertEquals(1f, surface.scaleX, 0f)
        assertEquals(1f, surface.scaleY, 0f)
        assertEquals(450, surface.measuredWidth)
        assertEquals(800, surface.measuredHeight)
    }

    @Test
    fun miniPlayerIgnoresCachedHeightsAndUserZoom() {
        val surface = surface()
        surface.setHeights(1920, 1920)
        surface.setAspectRatio(16f / 9f)
        surface.setUserTransform(3f, 50f, 50f)
        surface.setMiniPlayerMode(true)
        layout(surface, 320, 180)
        assertEquals(320, surface.measuredWidth)
        assertEquals(180, surface.measuredHeight)
        assertEquals(1f, surface.scaleX, 0f)
    }

    private fun surface() = ExpandableSurfaceView(RuntimeEnvironment.getApplication(), null)

    private fun layout(surface: ExpandableSurfaceView, width: Int, height: Int) {
        surface.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        )
        surface.layout(0, 0, surface.measuredWidth, surface.measuredHeight)
    }
}
