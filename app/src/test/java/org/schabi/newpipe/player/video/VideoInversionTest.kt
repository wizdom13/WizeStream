package org.schabi.newpipe.player.video

import androidx.media3.effect.RgbMatrix
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class VideoInversionTest {
    @Test
    fun inversionSwapsBlackAndWhiteAndLeavesHdrUntouched() {
        val matrix = SdrVideoEffects.create(VideoAdjustmentState(inverted = true)).last() as RgbMatrix
        val sdr = matrix.getMatrix(0, false)
        fun transform(color: FloatArray) = FloatArray(4) { row -> (0..3).sumOf { column -> (sdr[column * 4 + row] * color[column]).toDouble() }.toFloat() }
        assertArrayEquals(floatArrayOf(1f, 1f, 1f, 1f), transform(floatArrayOf(0f, 0f, 0f, 1f)), 0f)
        assertArrayEquals(floatArrayOf(0f, 0f, 0f, 1f), transform(floatArrayOf(1f, 1f, 1f, 1f)), 0f)
        assertArrayEquals(floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f), matrix.getMatrix(0, true), 0f)
        val reset = VideoAdjustmentState(enabled = true, remember = true, inverted = true).reset()
        assertFalse(reset.inverted)
        assertTrue(reset.enabled)
        assertTrue(reset.remember)
    }
}
