package org.schabi.newpipe.local.cleanup

import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.schabi.newpipe.R

object ExitCleanupPreferences {
    @JvmStatic
    fun bind(fragment: PreferenceFragmentCompat) {
        val preference = fragment.findPreference<SwitchPreferenceCompat>(ExitCleanup.POSITIONS) ?: return
        preference.setOnPreferenceChangeListener { _, value ->
            if (value != true) {
                true
            } else {
                MaterialAlertDialogBuilder(fragment.requireContext())
                    .setTitle(R.string.exit_cleanup_positions_title)
                    .setMessage(R.string.exit_cleanup_positions_confirm)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.ok) { _, _ -> preference.isChecked = true }
                    .show()
                false
            }
        }
    }
}
