package org.schabi.newpipe.player.ui

import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.FragmentVideoDetailBinding
import org.schabi.newpipe.views.ExpandableSurfaceView

@RunWith(AndroidJUnit4::class)
class MinimizedVideoSurfaceTest {
    @Test
    fun phoneAndWideLandscapeLayoutsProvideTheVideoHost() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val base = ApplicationProvider.getApplicationContext<Context>()
            listOf(360 to Configuration.ORIENTATION_PORTRAIT, 1000 to Configuration.ORIENTATION_LANDSCAPE).forEach { (width, orientation) ->
                val configuration = Configuration(base.resources.configuration).apply {
                    screenWidthDp = width
                    screenHeightDp = 640
                    this.orientation = orientation
                }
                val themed = ContextThemeWrapper(base.createConfigurationContext(configuration), R.style.DarkTheme)
                val binding = FragmentVideoDetailBinding.inflate(LayoutInflater.from(themed))
                assertNotNull(binding.overlayVideo)
                assertSame(binding.overlayLayout, binding.overlayVideo.parent)
            }
        }
    }

    @Test
    fun minimizesTheSameSurfaceAndRestoresOriginalParentAndZoom() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val original = FrameLayout(context)
            val host = FrameLayout(context)
            val surface = ExpandableSurfaceView(context, null)
            val originalParams = FrameLayout.LayoutParams(600, 400)
            original.addView(surface, originalParams)
            surface.setAspectRatio(9f / 16f)
            surface.setHeights(400, 800)
            surface.setUserTransform(2f, 0f, 0f)
            val minimized = MinimizedVideoSurface()
            repeat(3) {
                minimized.show(surface, host)
                minimized.show(surface, host)
                assertSame(host, surface.parent)
                assertEquals(1, host.childCount)
                surface.measure(View.MeasureSpec.makeMeasureSpec(106, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(60, View.MeasureSpec.EXACTLY))
                surface.layout(0, 0, surface.measuredWidth, surface.measuredHeight)
                assertEquals(33, surface.measuredWidth)
                assertEquals(60, surface.measuredHeight)
                assertEquals(1f, surface.scaleX, 0f)
                minimized.restore()
                assertSame(original, surface.parent)
                assertSame(originalParams, surface.layoutParams)
                assertEquals(2f, surface.userZoomScale, 0f)
            }
        }
    }
}
