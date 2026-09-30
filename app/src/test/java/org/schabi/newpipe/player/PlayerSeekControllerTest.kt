package org.schabi.newpipe.player

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerSeekControllerTest {
    @Test
    fun seekingBeforeTheLiveDurationArrivesNeverPassesAnUnsetTimestampToTheEngine() {
        assertEquals(0L, PlayerSeekController.resolveSeekPosition(0, C.TIME_UNSET))
        assertEquals(30000L, PlayerSeekController.resolveSeekPosition(30000, C.TIME_UNSET))
        assertEquals(0L, PlayerSeekController.resolveSeekPosition(-10000, C.TIME_UNSET))
    }

    @Test
    fun knownWindowsAllowRewindAndClampAtTheirBounds() {
        assertEquals(0L, PlayerSeekController.resolveSeekPosition(0, 14400000))
        assertEquals(60000L, PlayerSeekController.resolveSeekPosition(60000, 14400000))
        assertEquals(14400000L, PlayerSeekController.resolveSeekPosition(15000000, 14400000))
    }
}
