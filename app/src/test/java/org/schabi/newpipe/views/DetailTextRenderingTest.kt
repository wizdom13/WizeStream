package org.schabi.newpipe.views

import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class DetailTextRenderingTest {
    @Test
    @Config(sdk = [29])
    fun android10GroupsLabelsAndActionIconsWithoutChangingPlayerOrPager() {
        val context = RuntimeEnvironment.getApplication()
        val page = FrameLayout(context)
        val player = SurfaceView(context)
        val panel = FrameLayout(context)
        val pager = FrameLayout(context)
        val label = TextView(context).apply {
            text = "Video title"
            alpha = 0.75f
        }
        val icon = ImageView(context).apply { visibility = View.GONE }
        panel.addView(label)
        panel.addView(icon)
        page.addView(player)
        page.addView(panel)
        page.addView(pager)
        repeat(3) { DetailTextRendering.apply(panel) }
        assertEquals(View.LAYER_TYPE_SOFTWARE, panel.layerType)
        for (view in listOf(page, player, pager, label, icon)) assertEquals(View.LAYER_TYPE_NONE, view.layerType)
        assertEquals("Video title", label.text.toString())
        assertEquals(0.75f, label.alpha, 0.0f)
        assertEquals(View.GONE, icon.visibility)
    }

    @Test
    @Config(sdk = [29])
    fun refusesToRasterizeAContainerThatIncludesVideoOutput() {
        val context = RuntimeEnvironment.getApplication()
        for (surface in listOf(SurfaceView(context), TextureView(context))) {
            val originalLayer = surface.layerType
            val root = FrameLayout(context)
            val nested = FrameLayout(context)
            root.addView(nested)
            nested.addView(surface)
            DetailTextRendering.apply(root)
            DetailTextRendering.apply(surface)
            assertEquals(View.LAYER_TYPE_NONE, root.layerType)
            assertEquals(originalLayer, surface.layerType)
        }
    }

    @Test
    @Config(sdk = [28, 30])
    fun otherAndroidVersionsKeepNormalRendering() {
        val panel = FrameLayout(RuntimeEnvironment.getApplication())
        DetailTextRendering.apply(panel)
        assertEquals(View.LAYER_TYPE_NONE, panel.layerType)
    }
}
