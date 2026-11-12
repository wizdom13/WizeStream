package org.schabi.newpipe.player.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SphericalPlaybackStateTest {
    @Test
    fun explicitMetadataActivatesAutoButStereoAndAspectRatiosAreNotGuessed() {
        val state = SphericalPlaybackState()
        state.selectItem("first")
        assertFalse(state.isSpherical())
        state.setContainerHint(byteArrayOf())
        assertFalse(state.isSpherical())
        state.setExtractorHint(true)
        assertTrue(state.isSpherical())
        state.mode = SphericalPlaybackState.Mode.FLAT
        assertFalse(state.isSpherical())
        state.setExtractorHint(false)
        state.setContainerHint(byteArrayOf(1))
        assertFalse(state.isSpherical())
        state.mode = SphericalPlaybackState.Mode.AUTO
        assertTrue(state.isSpherical())
    }

    @Test
    fun manualModeSurvivesQualityAndSurfaceChangesButNotNextVideo() {
        val state = SphericalPlaybackState()
        state.selectItem("video")
        state.mode = SphericalPlaybackState.Mode.EQUIRECTANGULAR
        state.useSensorRotation = true
        state.selectItem("video")
        state.setContainerHint(null)
        assertTrue(state.isSpherical())
        state.selectItem("next")
        assertEquals(SphericalPlaybackState.Mode.AUTO, state.mode)
        assertFalse(state.isSpherical())
        assertTrue(state.useSensorRotation)
    }
}
