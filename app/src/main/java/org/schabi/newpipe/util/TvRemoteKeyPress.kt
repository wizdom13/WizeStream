/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.util

import android.view.KeyEvent

/** Own both halves of an assigned press so focused widgets/media sessions cannot also handle it. */
class TvRemoteKeyPress {
    private val pressed = mutableMapOf<Pair<Int, Int>, TvRemoteAction>()

    fun dispatch(
        keyCode: Int,
        deviceId: Int,
        eventAction: Int,
        repeatCount: Int,
        canceled: Boolean,
        eligible: Boolean,
        resolve: (Int) -> TvRemoteAction?,
        perform: (TvRemoteAction) -> Unit
    ): Boolean {
        val key = deviceId to keyCode
        if (eventAction == KeyEvent.ACTION_UP) {
            val action = pressed.remove(key) ?: return false
            if (eligible && !canceled && resolve(keyCode) == action) perform(action)
            return true
        }
        if (eventAction != KeyEvent.ACTION_DOWN) return false
        if (key in pressed) return true
        if (!eligible || repeatCount != 0) return false
        val action = resolve(keyCode) ?: return false
        pressed[key] = action
        return true
    }

    fun clear() = pressed.clear()
}
