/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings

import android.app.TimePickerDialog
import android.os.Bundle
import android.text.format.DateFormat
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.snackbar.Snackbar
import java.util.Calendar
import org.schabi.newpipe.R
import org.schabi.newpipe.learning.LearningMode
import org.schabi.newpipe.learning.LearningReminders
import org.schabi.newpipe.local.feed.notifications.NotificationHelper
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.util.PermissionHelper

class LearningSettingsFragment : BasePreferenceFragment() {
    private var profileId = ""

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResourceRegistry()
        profileId = ProfileManager.getActiveProfileId(requireContext())
        val master = requirePreference<SwitchPreferenceCompat>(R.string.learning_mode_key)
        master.setOnPreferenceChangeListener { _, value ->
            defaultPreferences.edit().putBoolean(getString(R.string.learning_mode_key), value as Boolean).apply()
            LearningReminders.initialize(requireContext())
            refreshPreferences()
            true
        }
        requirePreference<SwitchPreferenceCompat>(R.string.learning_profile_included_key).setOnPreferenceChangeListener { _, value ->
            LearningMode.setProfileIncluded(requireContext(), profileId, value as Boolean)
            refreshPreferences()
            true
        }
        requirePreference<SwitchPreferenceCompat>(R.string.learning_reminders_key).setOnPreferenceChangeListener { _, value ->
            LearningReminders.setEnabled(requireContext(), profileId, value as Boolean)
            if (value) PermissionHelper.checkPostNotificationsPermission(requireActivity(), PermissionHelper.POST_NOTIFICATIONS_REQUEST_CODE)
            refreshPreferences()
            true
        }
        requirePreference<Preference>(R.string.learning_reminder_time_key).setOnPreferenceClickListener {
            val selectedProfile = profileId
            val minute = LearningReminders.minuteOfDay(requireContext(), selectedProfile)
            TimePickerDialog(requireContext(), { _, hour, minuteOfHour ->
                LearningReminders.setMinuteOfDay(requireContext(), selectedProfile, hour * 60 + minuteOfHour)
                refreshPreferences()
            }, minute / 60, minute % 60, DateFormat.is24HourFormat(requireContext())).show()
            true
        }
        refreshPreferences()
    }

    override fun onResume() {
        super.onResume()
        profileId = ProfileManager.getActiveProfileId(requireContext())
        refreshPreferences()
        if (LearningReminders.isEnabled(requireContext(), profileId) && !NotificationHelper.areNotificationsEnabledOnDevice(requireContext())) {
            Snackbar.make(listView, R.string.notifications_disabled, Snackbar.LENGTH_LONG)
                .setAction(R.string.settings) { NotificationHelper.openSystemNotificationSettings(requireContext()) }.show()
        }
    }

    private fun refreshPreferences() {
        val context = requireContext()
        val masterEnabled = defaultPreferences.getBoolean(getString(R.string.learning_mode_key), false)
        val included = requirePreference<SwitchPreferenceCompat>(R.string.learning_profile_included_key)
        included.isChecked = LearningMode.isProfileIncluded(context, profileId)
        included.summary = getString(R.string.learning_profile_included_summary, ProfileManager.getDisplayName(context, ProfileManager.getActiveProfile(context)))
        val enabled = masterEnabled && included.isChecked
        listOf(R.string.learning_notes_key, R.string.learning_playlist_progress_key, R.string.learning_count_background_key).forEach {
            requirePreference<Preference>(it).isEnabled = enabled
        }
        val reminder = requirePreference<SwitchPreferenceCompat>(R.string.learning_reminders_key)
        reminder.isEnabled = enabled
        reminder.isChecked = LearningReminders.isEnabled(context, profileId)
        val minute = LearningReminders.minuteOfDay(context, profileId)
        val time = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minute / 60)
            set(Calendar.MINUTE, minute % 60)
        }
        requirePreference<Preference>(R.string.learning_reminder_time_key).apply {
            isEnabled = enabled && reminder.isChecked
            summary = DateFormat.getTimeFormat(context).format(time.time)
        }
    }
}
