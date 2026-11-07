package org.schabi.newpipe.player

import androidx.media3.exoplayer.Renderer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class SubtitleTimingTest {
    @Test
    fun shiftsTextClockAndSeekInBothDirectionsWithoutChangingRealtime() {
        val timing = SubtitleTiming()
        val renderer = mock(Renderer::class.java)
        val wrapped = timing.wrap(renderer)
        timing.setOffset(250)
        wrapped.render(1_000_000, 500)
        wrapped.resetPosition(1_000_000, true)
        verify(renderer).render(750_000, 500)
        verify(renderer).resetPosition(750_000, true)
        timing.setOffset(-250)
        wrapped.render(1_000_000, 500)
        verify(renderer).render(1_250_000, 500)
        timing.onMediaItemTransition(null, 0)
        assertEquals(0L, timing.offsetMs)
    }

    @Test
    fun boundsOffsetAndAllowsNegativeTextPositionsAtStart() {
        val timing = SubtitleTiming()
        timing.setOffset(Long.MAX_VALUE)
        assertEquals(30000L, timing.offsetMs)
        assertEquals(-30_000_000L, timing.shift(0))
        timing.setOffset(Long.MIN_VALUE)
        assertEquals(-30000L, timing.offsetMs)
    }
}
