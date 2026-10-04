package org.schabi.newpipe.player.ui

import android.view.KeyEvent

object TvPlayerKeyPolicy {
    const val DOUBLE_BACK_WINDOW_MS = 2_000L

    @JvmStatic
    fun isSeekKey(keyCode: Int): Boolean = keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
        keyCode == KeyEvent.KEYCODE_DPAD_RIGHT

    @JvmStatic
    fun shouldSeek(keyCode: Int, controlsVisible: Boolean, listOpen: Boolean): Boolean = isSeekKey(keyCode) && !controlsVisible && !listOpen

    @JvmStatic
    fun isForwardSeek(keyCode: Int): Boolean = keyCode == KeyEvent.KEYCODE_DPAD_RIGHT

    @JvmStatic
    fun shouldCloseOnSecondBack(lastBackAt: Long, now: Long): Boolean = lastBackAt > 0 &&
        now - lastBackAt <= DOUBLE_BACK_WINDOW_MS
}
