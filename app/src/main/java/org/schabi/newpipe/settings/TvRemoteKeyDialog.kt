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
    private lateinit var action: TvRemoteAction
    private lateinit var keys: TvRemoteKeys

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        action = requireNotNull(TvRemoteAction.fromId(requireArguments().getString(ACTION)))
        keys = TvRemoteKeys(PreferenceManager.getDefaultSharedPreferences(requireContext()))
        candidate = savedInstanceState?.getInt(CANDIDATE, KeyEvent.KEYCODE_UNKNOWN)?.takeIf(TvRemoteKeys::isAssignable)
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(action.title)
            .setMessage(R.string.remote_capture)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.remote_assign) { _, _ -> candidate?.let { keys.assign(action, it) } }
            .setNeutralButton(R.string.remote_direction, null)
            .create()

        dialog.setOnKeyListener { _, code, event ->
            // Let the dialog handle D-pad, confirmation, Back and volume normally.
            if (TvRemoteKeys.isDirection(code) || !TvRemoteKeys.isAssignable(code)) return@setOnKeyListener false
            val key = event.deviceId to code
            when (event.action) {
                KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) pressed = key

                KeyEvent.ACTION_UP -> {
                    if (pressed == key && !event.isCanceled) {
                        candidate = code
                        showCandidate(dialog)
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).requestFocus()
                    }
                    pressed = null
                }
            }
            true
        }
        return dialog
    }

    override fun onStart() {
        super.onStart()
        // Initialize synchronously once the buttons exist, before the dialog accepts input.
        val dialog = requireDialog() as AlertDialog
        showCandidate(dialog)
        val extra = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
        extra.setText(if (keys.keyFor(action) == null) R.string.remote_direction else R.string.remote_remove)
        extra.setOnClickListener {
            if (keys.keyFor(action) != null) {
                keys.remove(action)
                dismiss()
                return@setOnClickListener
            }
            val directions = listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)
            AlertDialog.Builder(requireContext()).setTitle(R.string.remote_direction_scope)
                .setItems((directions.map(TvRemoteKeys::label) + getString(R.string.remote_remove)).toTypedArray()) { _, index ->
                    if (index == directions.size) {
                        keys.remove(action)
                        dismiss()
                    } else {
                        candidate = directions[index]
                        showCandidate(dialog)
                    }
                }.setNegativeButton(R.string.cancel, null).show()
        }
    }

    private fun showCandidate(dialog: AlertDialog) {
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
