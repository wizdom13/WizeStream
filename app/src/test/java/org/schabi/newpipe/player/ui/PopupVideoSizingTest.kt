package org.schabi.newpipe.player.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PopupVideoSizingTest {
    @Test
    fun portraitVideoFillsItsWindowWithoutSidebars() {
        val size = PopupVideoSizing.fit(320, 1080, 2000, 180, 9f / 16f)
        assertEquals(320, size.width)
        assertEquals(569, size.height)
    }

    @Test
    fun shortDisplayShrinksBothDimensionsInsteadOfCropping() {
        val size = PopupVideoSizing.fit(400, 900, 400, 180, 9f / 16f)
        assertTrue(size.width <= 225)
        assertTrue(size.height <= 400)
        assertTrue(size.width > 0 && size.height > 0)
    }

    @Test
    fun landscapeAndMissingRatioUseTheExistingDefaultWindow() {
        assertEquals(
            PopupVideoSizing.fit(320, 1080, 2000, 180, 16f / 9f),
            PopupVideoSizing.fit(320, 1080, 2000, 180, Float.NaN)
        )
        assertEquals(180, PopupVideoSizing.fit(320, 1080, 2000, 180, 16f / 9f).height)
    }

    @Test
    fun smallestPopupKeepsSeparateTouchRowsWithoutChangingItsPreferredWidth() {
        assertEquals(PopupVideoSizing.Size(150, 96), PopupVideoSizing.fit(150, 1080, 2000, 150, 16f / 9f, 96))
        assertEquals(PopupVideoSizing.Size(180, 101), PopupVideoSizing.fit(180, 1080, 2000, 150, 16f / 9f, 96))
        assertEquals(PopupVideoSizing.Size(150, 267), PopupVideoSizing.fit(150, 1080, 2000, 150, 9f / 16f, 96))
        assertTrue(PopupVideoSizing.fit(150, 150, 80, 150, 16f / 9f, 96).height <= 80)
    }
}
