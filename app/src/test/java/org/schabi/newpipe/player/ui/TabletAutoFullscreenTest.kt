package org.schabi.newpipe.player.ui

import android.content.res.Configuration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabletAutoFullscreenTest {
    private val landscape = Configuration.ORIENTATION_LANDSCAPE
    private val portrait = Configuration.ORIENTATION_PORTRAIT

    @Test
    fun `tablet requires opt in while phone behavior stays unchanged`() {
        val policy = TabletAutoFullscreen()
        assertFalse(policy.isAllowed(true, false, landscape))
        assertTrue(policy.isAllowed(true, true, landscape))
        assertTrue(policy.isAllowed(false, false, landscape))
    }

    @Test
    fun `manual override survives callbacks until rotation or another video`() {
        val policy = TabletAutoFullscreen()
        policy.onManualChange(landscape)
        repeat(3) { assertFalse(policy.isAllowed(true, true, landscape)) }
        assertTrue(policy.isAllowed(true, true, portrait))
        assertTrue(policy.isAllowed(true, true, landscape))
        policy.onManualChange(landscape)
        policy.onNewVideo()
        assertTrue(policy.isAllowed(true, true, landscape))
    }

    @Test
    fun `opt in keeps portrait and square videos out of automatic fullscreen`() {
        for (orientation in FullscreenOrientationPolicy.VideoContentOrientation.values()) {
            val result = FullscreenOrientationPolicy.resolveFullscreenState(
                landscape,
                false,
                orientation,
                true,
                0,
                FullscreenOrientationPolicy.KEEP_FULLSCREEN_STATE
            )
            assertTrue(
                (result == FullscreenOrientationPolicy.ENTER_FULLSCREEN) ==
                    (orientation == FullscreenOrientationPolicy.VideoContentOrientation.LANDSCAPE)
            )
        }
    }
}
