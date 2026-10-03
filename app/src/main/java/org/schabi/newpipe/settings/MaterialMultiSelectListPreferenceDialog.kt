package org.schabi.newpipe.settings

import android.app.Dialog
import android.os.Bundle
import androidx.preference.MultiSelectListPreferenceDialogFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Retains AndroidX choice state, validation, and persistence with a Material dialog. */
class MaterialMultiSelectListPreferenceDialog : MultiSelectListPreferenceDialogFragmentCompat() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val pref = preference
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(pref.dialogTitle)
            .setIcon(pref.dialogIcon)
            .setPositiveButton(pref.positiveButtonText, this)
            .setNegativeButton(pref.negativeButtonText, this)
        val content = onCreateDialogView(requireContext())
        if (content == null) {
            builder.setMessage(pref.dialogMessage)
        } else {
            onBindDialogView(content)
            builder.setView(content)
        }
        onPrepareDialogBuilder(builder)
        return builder.create()
    }
}
