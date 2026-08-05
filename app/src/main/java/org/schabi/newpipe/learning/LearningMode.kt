/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.learning

import android.content.Context
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R

object LearningMode {
    @JvmStatic
    fun isEnabled(context: Context): Boolean = preference(
        context,
        R.string.learning_mode_key,
        false
    )

    @JvmStatic
    fun areNotesEnabled(context: Context): Boolean = isEnabled(context) && preference(
        context,
        R.string.learning_notes_key,
        true
    )

    @JvmStatic
    fun isPlaylistProgressEnabled(context: Context): Boolean = isEnabled(context) && preference(
        context,
        R.string.learning_playlist_progress_key,
        true
    )

    @JvmStatic
    fun isNotesSyncEnabled(context: Context): Boolean = areNotesEnabled(context) && preference(
        context,
        R.string.device_sync_learning_notes_key,
        false
    )

    @JvmStatic
    fun shouldCountBackgroundPlayback(context: Context): Boolean = isEnabled(context) && preference(
        context,
        R.string.learning_count_background_key,
        true
    )

    private fun preference(context: Context, key: Int, defaultValue: Boolean): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            .getBoolean(context.getString(key), defaultValue)
    }
}
