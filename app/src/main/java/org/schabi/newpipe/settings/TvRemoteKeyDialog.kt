/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.settings

import android.app.Dialog
import android.os.Bundle
import android.view.KeyEvent
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R
import org.schabi.newpipe.util.TvRemoteAction
import org.schabi.newpipe.util.TvRemoteKeys

class TvRemoteKeyDialog : DialogFragment() {
    private var candidate: Int? = null
    private var pressed: Pair<Int, Int>? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val action = requireNotNull(TvRemoteAction.fromId(requireArguments().getString(ACTION)))
        val keys = TvRemoteKeys(PreferenceManager.getDefaultSharedPreferences(requireContext()))
        candidate = savedInstanceState?.getInt(CANDIDATE, KeyEvent.KEYCODE_UNKNOWN)?.takeIf(TvRemoteKeys::isAssignable)
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(action.title)
            .setMessage(R.string.remote_capture)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.remote_assign) { _, _ -> candidate?.let { keys.assign(action, it) } }
            .apply {
                if (keys.keyFor(action) != null) {
                    setNeutralButton(R.string.remote_remove) { _, _ -> keys.remove(action) }
                }
            }
            .create()

        fun showCandidate() {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = candidate != null
            candidate?.let { code ->
                val conflict = keys.actionFor(code)?.takeIf { it != action }
                dialog.setMessage(
                    if (conflict == null) {
                        getString(R.string.remote_selected, TvRemoteKeys.label(code))
                    } else {
                        getString(R.string.remote_conflict, TvRemoteKeys.label(code), getString(conflict.title))
                    }
                )
            }
        }
        dialog.setOnShowListener { showCandidate() }
        dialog.setOnKeyListener { _, code, event ->
            // Let the dialog handle D-pad, confirmation, Back and volume normally.
            if (!TvRemoteKeys.isAssignable(code)) return@setOnKeyListener false
            val key = event.deviceId to code
            when (event.action) {
                KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) pressed = key

                KeyEvent.ACTION_UP -> {
                    if (pressed == key && !event.isCanceled) {
                        candidate = code
                        showCandidate()
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).requestFocus()
                    }
                    pressed = null
                }
            }
            true
        }
        return dialog
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(CANDIDATE, candidate ?: KeyEvent.KEYCODE_UNKNOWN)
        super.onSaveInstanceState(outState)
    }

    companion object {
        const val TAG = "tv_remote_key_capture"
        private const val ACTION = "action"
        private const val CANDIDATE = "candidate"

        fun newInstance(action: TvRemoteAction) = TvRemoteKeyDialog().apply {
            arguments = Bundle().apply { putString(ACTION, action.id) }
        }
    }
}
