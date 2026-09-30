/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.util

import android.content.SharedPreferences
import android.view.KeyEvent

class TvRemoteKeys(private val preferences: SharedPreferences) {
    fun keyFor(action: TvRemoteAction): Int? = try {
        preferences.getInt(action.preferenceKey, KeyEvent.KEYCODE_UNKNOWN).takeIf(::isAssignable)
    } catch (_: ClassCastException) {
        // Ignore a malformed imported preference rather than breaking all hardware key handling.
        null
    }

    fun actionFor(keyCode: Int): TvRemoteAction? = TvRemoteAction.entries.firstOrNull { keyFor(it) == keyCode }

    /** Reassignment is atomic: one button can only invoke one action. */
    fun assign(action: TvRemoteAction, keyCode: Int) {
        require(isAssignable(keyCode))
        preferences.edit().apply {
            TvRemoteAction.entries.filter { keyFor(it) == keyCode }.forEach { remove(it.preferenceKey) }
            putInt(action.preferenceKey, keyCode)
        }.apply()
    }

    fun remove(action: TvRemoteAction) = preferences.edit().remove(action.preferenceKey).apply()

    fun reset() = preferences.edit().apply {
        TvRemoteAction.entries.forEach { remove(it.preferenceKey) }
    }.apply()

    companion object {
        // Keep focus, text editing, volume and system controls available even with custom bindings.
        fun isAssignable(code: Int): Boolean = code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 ||
            code in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z ||
            code in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12 ||
            code in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 ||
            code in KeyEvent.KEYCODE_PROG_RED..KeyEvent.KEYCODE_PROG_BLUE || code in otherAssignableKeys

        private val otherAssignableKeys = setOf(
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_GUIDE,
            KeyEvent.KEYCODE_SEARCH, KeyEvent.KEYCODE_BOOKMARK, KeyEvent.KEYCODE_CAPTIONS,
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP,
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_MEDIA_RECORD, KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
            KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD, KeyEvent.KEYCODE_MEDIA_STEP_FORWARD,
            KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD
        )

        fun label(code: Int): String = KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_").replace('_', ' ')
    }
}
