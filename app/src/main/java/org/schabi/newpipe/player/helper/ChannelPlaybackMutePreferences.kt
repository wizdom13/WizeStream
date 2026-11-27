/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.player.helper

import android.content.Context
import androidx.preference.PreferenceManager

/** Explicit channel mute choices share the existing app-wide service/uploader identity. */
internal class ChannelPlaybackMutePreferences(context: Context) {
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)

    fun key(serviceId: Int, uploaderUrl: String?): String? = if (serviceId < 0) {
        null
    } else {
        ChannelPlaybackProfileManager.profileKey(serviceId, uploaderUrl)?.plus(".muted")
    }

    fun isMuted(key: String): Boolean = try {
        preferences.getBoolean(key, false)
    } catch (_: ClassCastException) {
        false
    }

    fun save(key: String, muted: Boolean) {
        preferences.edit().apply {
            if (muted) putBoolean(key, true) else remove(key)
        }.apply()
    }
}
