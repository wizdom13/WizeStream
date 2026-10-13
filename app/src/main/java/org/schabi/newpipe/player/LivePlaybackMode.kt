/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

import android.content.Context
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R

/** A per-stream override; changing it never changes the default for future streams. */
class LivePlaybackMode(context: Context) {
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
    private val key = context.getString(R.string.default_live_mode_key)
    private var overrideUrl: String? = null
    private var normalOverride = true

    @Synchronized
    fun isNormal(url: String): Boolean = if (url == overrideUrl) {
        normalOverride
    } else {
        preferences.getString(key, "normal") != "full"
    }

    @Synchronized
    fun setNormal(url: String, normal: Boolean) {
        overrideUrl = url
        normalOverride = normal
    }
}
