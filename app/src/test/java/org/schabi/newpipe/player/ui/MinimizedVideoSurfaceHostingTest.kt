package org.schabi.newpipe.player.ui

import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.views.ExpandableSurfaceView

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MinimizedVideoSurfaceHostingTest {
    @Test
    fun reparentsOnlyWhenNeededAndRestoresOriginalPlacement() {
        val context = RuntimeEnvironment.getApplication()
        val original = FrameLayout(context)
        original.addView(FrameLayout(context))
        val host = FrameLayout(context)
        val surface = ExpandableSurfaceView(context, null)
        val params = FrameLayout.LayoutParams(600, 400)
        original.addView(surface, params)
        val originalIndex = original.indexOfChild(surface)

        val minimized = MinimizedVideoSurface()
        assertTrue(minimized.show(surface, host))
        assertTrue(minimized.isHosting())
        assertSame(host, surface.parent)
        // Repeating the same request must not reparent again: the fragment uses this result to
        // avoid rebinding the decoder output on every playback update.
        assertFalse(minimized.show(surface, host))
        assertEquals(1, host.childCount)

        assertTrue(minimized.restore())
        assertFalse(minimized.isHosting())
        assertSame(original, surface.parent)
        assertSame(params, surface.layoutParams)
        assertEquals(originalIndex, original.indexOfChild(surface))
        assertFalse(minimized.restore())
    }

    @Test
    fun refusesToHostASurfaceWithoutParent() {
        val context = RuntimeEnvironment.getApplication()
        val host = FrameLayout(context)
        val surface = ExpandableSurfaceView(context, null)
        val minimized = MinimizedVideoSurface()

        assertFalse(minimized.show(surface, host))
        assertFalse(minimized.isHosting())
        assertNull(surface.parent)
        assertEquals(0, host.childCount)
    }
}
