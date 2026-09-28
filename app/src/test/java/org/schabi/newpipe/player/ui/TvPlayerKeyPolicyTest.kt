package org.schabi.newpipe.player.ui

import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvPlayerKeyPolicyTest {
    @Test
    fun leftAndRightAreDirectSeekKeys() {
        assertTrue(TvPlayerKeyPolicy.isSeekKey(KeyEvent.KEYCODE_DPAD_LEFT))
        assertTrue(TvPlayerKeyPolicy.isSeekKey(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertFalse(TvPlayerKeyPolicy.isSeekKey(KeyEvent.KEYCODE_DPAD_UP))
        assertTrue(TvPlayerKeyPolicy.isForwardSeek(KeyEvent.KEYCODE_DPAD_RIGHT))
    }

    @Test
    fun secondBackWithinWindowClosesPlayer() {
        assertTrue(TvPlayerKeyPolicy.shouldCloseOnSecondBack(1_000L, 2_500L))
        assertFalse(TvPlayerKeyPolicy.shouldCloseOnSecondBack(1_000L, 3_500L))
        assertFalse(TvPlayerKeyPolicy.shouldCloseOnSecondBack(0L, 500L))
    }
}
