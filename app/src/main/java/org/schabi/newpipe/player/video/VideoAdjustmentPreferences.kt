/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.video

import android.content.SharedPreferences

class VideoAdjustmentPreferences(private val preferences: SharedPreferences) {
    fun load(): VideoAdjustmentState = try {
        if (preferences.getBoolean(REMEMBER, false)) {
            VideoAdjustmentState(
                enabled = preferences.getBoolean(ENABLED, false),
                brightness = preferences.getInt(BRIGHTNESS, 0),
                contrast = preferences.getInt(CONTRAST, 0),
                saturation = preferences.getInt(SATURATION, 100),
                remember = true
            ).clamped()
        } else {
            VideoAdjustmentState()
        }
    } catch (_: ClassCastException) {
        // A malformed imported preference must never prevent the player from starting.
        VideoAdjustmentState()
    }

    fun save(state: VideoAdjustmentState) {
        val editor = preferences.edit()
        if (state.remember) {
            editor.putBoolean(REMEMBER, true)
                .putBoolean(ENABLED, state.enabled)
                .putInt(BRIGHTNESS, state.brightness)
                .putInt(CONTRAST, state.contrast)
                .putInt(SATURATION, state.saturation)
        } else {
            // Turning remembering off must also remove any previously saved adjustments.
            listOf(REMEMBER, ENABLED, BRIGHTNESS, CONTRAST, SATURATION).forEach(editor::remove)
        }
        editor.apply()
    }

    private companion object {
        const val REMEMBER = "video_adjustments_remember_v1"
        const val ENABLED = "video_adjustments_enabled_v1"
        const val BRIGHTNESS = "video_adjustments_brightness_v1"
        const val CONTRAST = "video_adjustments_contrast_v1"
        const val SATURATION = "video_adjustments_saturation_v1"
    }
}
