package org.schabi.newpipe.player.video

import androidx.media3.effect.GlEffect
import androidx.media3.effect.RgbMatrix
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SdrVideoEffectsTest {
    private val identity = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)

    @Test
    fun hdrNeverCallsAnSdrMatrixEvenWhenTheStreamChangesColorTransfer() {
        val adjusted = identity.copyOf().apply { this[12] = 0.25f }
        var calls = 0
        val matrix = SdrVideoEffects.SdrMatrix { _, useHdr ->
            check(!useHdr)
            calls++
            adjusted
        }
        assertArrayEquals(adjusted, matrix.getMatrix(0, false), 0f)
        assertArrayEquals(identity, matrix.getMatrix(1000, true), 0f)
        assertArrayEquals(adjusted, matrix.getMatrix(2000, false), 0f)
        assertTrue(calls == 2)
    }

    @Test
    fun hdrSelectsAnIdentityProgramInsteadOfTheUnsupportedHslShader() {
        val sdr = GlEffect { _, _ -> error("Must not create an SDR shader for HDR") }
        val effect = SdrVideoEffects.SdrEffect(sdr)
        assertSame(sdr, effect.forInput(false))
        val hdr = effect.forInput(true)
        assertTrue(hdr is RgbMatrix)
        assertArrayEquals(identity, (hdr as RgbMatrix).getMatrix(0, true), 0f)
    }
}
