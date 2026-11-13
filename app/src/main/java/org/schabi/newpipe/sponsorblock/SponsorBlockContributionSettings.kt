package org.schabi.newpipe.sponsorblock

import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.schabi.newpipe.R

object SponsorBlockContributionSettings {
    @JvmStatic
    fun bind(fragment: PreferenceFragmentCompat) {
        val context = fragment.requireContext()
        val enabled = fragment.findPreference<SwitchPreferenceCompat>("sponsor_block_contributions") ?: return
        enabled.setOnPreferenceChangeListener { _, value ->
            if (value == false) {
                true
            } else {
                MaterialAlertDialogBuilder(context).setTitle(R.string.sb_contribute_title)
                    .setMessage(R.string.sb_contribute_privacy)
                    .setPositiveButton(R.string.sb_contribute_enable) { _, _ -> enabled.isChecked = true }
                    .setNegativeButton(android.R.string.cancel, null).show()
                false
            }
        }
        fragment.findPreference<EditTextPreference>("sponsor_block_contribution_id")?.apply {
            isPersistent = false
            text = SponsorBlockContributions.identity(context)
            setOnPreferenceChangeListener { _, value ->
                val accepted = SponsorBlockContributions.setIdentity(context, (value as String).trim())
                if (!accepted) Toast.makeText(context, R.string.sb_contribute_invalid_id, Toast.LENGTH_LONG).show()
                accepted
            }
        }
    }
}
