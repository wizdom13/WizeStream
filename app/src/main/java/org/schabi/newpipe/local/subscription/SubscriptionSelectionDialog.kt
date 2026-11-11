/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.subscription

import android.content.Context
import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.schabi.newpipe.R

internal object SubscriptionSelectionDialog {
    fun create(context: Context, labels: Array<String>, confirm: (BooleanArray) -> Unit): AlertDialog {
        val selected = BooleanArray(labels.size)
        fun updateButtons(dialog: AlertDialog) {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = selected.any { it }
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setText(
                if (selected.all { it }) R.string.subscription_clear_selection else R.string.subscription_select_all
            )
        }
        val picker = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.subscription_bulk_unsubscribe)
            .setMultiChoiceItems(labels, selected) { dialog, index, checked ->
                selected[index] = checked
                updateButtons(dialog as AlertDialog)
            }
            .setPositiveButton(R.string.unsubscribe) { _, _ -> confirm(selected.copyOf()) }
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.subscription_select_all, null)
            .create()
        picker.setOnShowListener {
            updateButtons(picker)
            // Override the default button action so selecting/clearing keeps the picker open.
            picker.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
                val check = !selected.all { it }
                selected.fill(check)
                selected.indices.forEach { picker.listView.setItemChecked(it, check) }
                updateButtons(picker)
            }
        }
        return picker
    }
}
