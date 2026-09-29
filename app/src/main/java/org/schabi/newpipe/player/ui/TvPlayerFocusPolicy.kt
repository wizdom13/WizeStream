package org.schabi.newpipe.player.ui

import android.view.KeyEvent

object TvPlayerFocusPolicy {
    @JvmStatic
    fun shouldMoveToActions(keyCode: Int, fullscreen: Boolean, tv: Boolean): Boolean =
        fullscreen && tv && keyCode == KeyEvent.KEYCODE_DPAD_UP

    @JvmStatic
    fun commentsWidthDp(tv: Boolean): Int = if (tv) 224 else 320
}
