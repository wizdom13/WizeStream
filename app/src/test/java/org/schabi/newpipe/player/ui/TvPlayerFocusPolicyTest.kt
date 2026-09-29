package org.schabi.newpipe.player.ui

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvPlayerFocusPolicyTest {
    @Test
    fun dpadUpMovesFromPlaybackAreaToActionsOnTvFullscreen() {
        assertTrue(
            TvPlayerFocusPolicy.shouldMoveToActions(
                KeyEvent.KEYCODE_DPAD_UP,
                fullscreen = true,
                tv = true
            )
        )
        assertFalse(
            TvPlayerFocusPolicy.shouldMoveToActions(
                KeyEvent.KEYCODE_DPAD_UP,
                fullscreen = false,
                tv = true
            )
        )
    }

    @Test
    fun commentsPanelIsCompactOnTv() {
        assertEquals(224, TvPlayerFocusPolicy.commentsWidthDp(tv = true))
        assertEquals(320, TvPlayerFocusPolicy.commentsWidthDp(tv = false))
    }
}
