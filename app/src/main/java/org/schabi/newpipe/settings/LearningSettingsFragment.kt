/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import org.schabi.newpipe.R

class LearningSettingsFragment : BasePreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResourceRegistry()
        val master = requirePreference<SwitchPreferenceCompat>(R.string.learning_mode_key)
        val notes = requirePreference<Preference>(R.string.learning_notes_key)
        val progress = requirePreference<Preference>(R.string.learning_playlist_progress_key)
        val background = requirePreference<Preference>(R.string.learning_count_background_key)

        fun updateDependents(enabled: Boolean) {
            notes.isEnabled = enabled
            progress.isEnabled = enabled
            background.isEnabled = enabled
        }
        updateDependents(master.isChecked)
        master.setOnPreferenceChangeListener { _, value ->
            updateDependents(value as Boolean)
            true
        }
    }
}
