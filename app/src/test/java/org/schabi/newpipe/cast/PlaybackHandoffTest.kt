package org.schabi.newpipe.cast

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackHandoffTest {
    @Test
    fun `provides the current resume position`() {
        var position = 12.5
        val handoff = PlaybackHandoff({ position }) {}

        assertEquals(12.5, handoff.resumePositionSeconds(), 0.0)

        position = 14.0
        assertEquals(14.0, handoff.resumePositionSeconds(), 0.0)
    }

    @Test
    fun `sanitizes invalid resume positions`() {
        listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { position ->
            val handoff = PlaybackHandoff({ position }) {}

            assertEquals(0.0, handoff.resumePositionSeconds(), 0.0)
        }
    }

    @Test
    fun `confirms local handoff only once`() {
        var confirmations = 0
        val handoff = PlaybackHandoff({ 1.0 }) { confirmations++ }

        handoff.confirmRemotePlayback()
        handoff.confirmRemotePlayback()

        assertEquals(1, confirmations)
        assertEquals(0.0, handoff.resumePositionSeconds(), 0.0)
    }

    @Test
    fun `cancel keeps local playback untouched`() {
        var confirmations = 0
        val handoff = PlaybackHandoff({ 1.0 }) { confirmations++ }

        handoff.cancel()
        handoff.confirmRemotePlayback()

        assertEquals(0, confirmations)
        assertEquals(0.0, handoff.resumePositionSeconds(), 0.0)
    }
}
