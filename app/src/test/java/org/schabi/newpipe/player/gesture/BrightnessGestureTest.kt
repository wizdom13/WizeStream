package org.schabi.newpipe.player.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrightnessGestureTest {
    @Test
    fun `curve is monotonic bounded and reversible including endpoints`() {
        var previous = -1f
        for (index in 0..1000) {
            val position = index / 1000f
            val brightness = BrightnessGesture.toBrightness(position, true)
            assertTrue(brightness in 0f..1f && brightness >= previous)
            assertEquals(position, BrightnessGesture.toPosition(brightness, true), 0.00001f)
            assertEquals(position, BrightnessGesture.toBrightness(position, false), 0f)
            previous = brightness
        }
        assertEquals(0f, BrightnessGesture.toBrightness(0f, true), 0f)
        assertEquals(1f, BrightnessGesture.toBrightness(1f, true), 0f)
        assertEquals(1f / 12f, BrightnessGesture.toBrightness(0.5f, true), 0.00001f)
    }

    @Test
    fun `dark range gains precision and fractional movements are retained`() {
        val linear = BrightnessGesture.adjust(0.01f, 1f, 100, false)
        val perceptual = BrightnessGesture.adjust(0.01f, 1f, 100, true)
        assertTrue(perceptual > 0.01f && perceptual < linear)
        var brightness = 0.01f
        repeat(10) { brightness = BrightnessGesture.adjust(brightness, 0.1f, 100, true) }
        assertEquals(perceptual, brightness, 0.00001f)
    }

    @Test
    fun `system sentinel uses fallback while actual overrides are kept`() {
        assertEquals(0.4f, BrightnessGesture.initialBrightness(-1f) { 0.4f }, 0f)
        assertEquals(0f, BrightnessGesture.initialBrightness(0f) { error("Unexpected system read") }, 0f)
        assertEquals(0.7f, BrightnessGesture.initialBrightness(0.7f) { error("Unexpected system read") }, 0f)
        for (mode in listOf(false, true)) {
            assertEquals(0.7f, BrightnessGesture.adjust(0.7f, 0f, 100, mode), 0f)
            assertEquals(0.7f, BrightnessGesture.adjust(0.7f, 1f, 0, mode), 0f)
            assertEquals(0f, BrightnessGesture.adjust(0.7f, -1000f, 100, mode), 0f)
            assertEquals(1f, BrightnessGesture.adjust(0.7f, 1000f, 100, mode), 0f)
        }
    }
}
