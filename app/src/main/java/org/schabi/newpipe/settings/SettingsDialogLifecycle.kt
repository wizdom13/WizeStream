package org.schabi.newpipe.settings

import android.content.DialogInterface
import androidx.fragment.app.FragmentManager
import androidx.preference.PreferenceDialogFragmentCompat

/** Preference dialogs cannot outlive the preference fragment that owns their target. */
object SettingsDialogLifecycle {
    @JvmStatic
    fun dismissBeforeNavigationRebuild(manager: FragmentManager) {
        manager.fragments.filterIsInstance<PreferenceDialogFragmentCompat>().forEach { dialog ->
            // Treat the layout change as cancellation, including an unconfirmed text/list edit.
            dialog.dialog?.let { dialog.onClick(it, DialogInterface.BUTTON_NEGATIVE) }
            dialog.dismissNow()
        }
    }
}
