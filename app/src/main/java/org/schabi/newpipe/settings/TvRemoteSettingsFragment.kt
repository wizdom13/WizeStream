/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.settings

import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import org.schabi.newpipe.R
import org.schabi.newpipe.util.TvRemoteAction
import org.schabi.newpipe.util.TvRemoteKeys

class TvRemoteSettingsFragment : BasePreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val keys get() = TvRemoteKeys(defaultPreferences)

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResourceRegistry()
        TvRemoteAction.entries.forEach { action ->
            findPreference<Preference>(action.preferenceKey)!!.setOnPreferenceClickListener {
                if (parentFragmentManager.findFragmentByTag(TvRemoteKeyDialog.TAG) == null) {
                    TvRemoteKeyDialog.newInstance(action).show(parentFragmentManager, TvRemoteKeyDialog.TAG)
                }
                true
            }
        }
        findPreference<Preference>("tv_remote_reset")!!.setOnPreferenceClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.remote_reset)
                .setMessage(R.string.remote_reset_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.reset) { _, _ -> keys.reset() }
                .show()
            true
        }
        refreshSummaries()
    }

    override fun onResume() {
        super.onResume()
        defaultPreferences.registerOnSharedPreferenceChangeListener(this)
        refreshSummaries()
    }

    override fun onPause() {
        defaultPreferences.unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }

    override fun onSharedPreferenceChanged(preferences: SharedPreferences?, key: String?) = refreshSummaries()

    private fun refreshSummaries() {
        TvRemoteAction.entries.forEach { action ->
            findPreference<Preference>(action.preferenceKey)!!.summary =
                keys.keyFor(action)?.let(TvRemoteKeys::label) ?: getString(R.string.remote_unassigned)
        }
    }
}
