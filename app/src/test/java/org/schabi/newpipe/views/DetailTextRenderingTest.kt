package org.schabi.newpipe.views

import android.view.View
import android.widget.FrameLayout
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
    fun android10UsesSoftwareOnlyForTextAndPreservesContent() {
        val context = RuntimeEnvironment.getApplication()
        val root = FrameLayout(context)
        val label = TextView(context).apply { text = "Video title" }
        val image = View(context)
        root.addView(label)
        root.addView(image)
        repeat(3) { DetailTextRendering.apply(root) }
        assertEquals(View.LAYER_TYPE_SOFTWARE, label.layerType)
        assertEquals(View.LAYER_TYPE_NONE, root.layerType)
        assertEquals(View.LAYER_TYPE_NONE, image.layerType)
        assertEquals("Video title", label.text.toString())
    }

    @Test
    @Config(sdk = [28, 30])
    fun otherAndroidVersionsKeepNormalRendering() {
        val label = TextView(RuntimeEnvironment.getApplication())
        DetailTextRendering.apply(label)
        assertEquals(View.LAYER_TYPE_NONE, label.layerType)
    }
}
