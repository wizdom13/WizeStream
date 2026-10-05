package org.schabi.newpipe.player

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelSkipPolicyTest {
    @Test
    fun freshStartSkipsIntroButRestoredPlaybackDoesNot() {
        assertEquals(ChannelSkipPolicy.Action.INTRO, action(0))
        assertEquals(ChannelSkipPolicy.Action.INTRO, action(9_999))
        assertEquals(ChannelSkipPolicy.Action.NONE, action(10_000))
        assertEquals(ChannelSkipPolicy.Action.NONE, action(0, freshStart = false))
    }

    @Test
    fun outroCompletesAtItsBoundaryEvenAfterRestoringPlayback() {
        assertEquals(ChannelSkipPolicy.Action.NONE, action(89_999))
        assertEquals(ChannelSkipPolicy.Action.OUTRO, action(90_000, freshStart = false))
        assertEquals(ChannelSkipPolicy.Action.OUTRO, action(99_999))
        assertEquals(ChannelSkipPolicy.Action.NONE, action(100_000))
    }

    @Test
    fun manualSeekingOverridesBothBoundaries() {
        assertEquals(ChannelSkipPolicy.Action.NONE, action(0, manuallyOverridden = true))
        assertEquals(ChannelSkipPolicy.Action.NONE, action(90_000, manuallyOverridden = true))
    }

    @Test
    fun disabledUnknownAndOverlappingDurationsNeverSkip() {
        assertEquals(ChannelSkipPolicy.Action.NONE, ChannelSkipPolicy.decide(100, 0, 0, 0, true, false))
        for (duration in listOf(-1L, 0L, 19L, 20L)) {
            assertEquals(ChannelSkipPolicy.Action.NONE, ChannelSkipPolicy.decide(duration, 0, 10, 10, true, false))
        }
    }

    private fun action(position: Long, freshStart: Boolean = true, manuallyOverridden: Boolean = false) = ChannelSkipPolicy.decide(100_000, position, 10_000, 10_000, freshStart, manuallyOverridden)
}
