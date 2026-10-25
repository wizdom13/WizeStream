package org.schabi.newpipe.profiles

import android.content.Context
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R

object KidsModeDialogs {
    @JvmStatic
    fun requestAccess(context: Context, onGranted: Runnable, onCancelled: Runnable): AlertDialog? {
        if (!KidsMode.requiresParentAccess(context)) {
            onGranted.run()
            return null
        }
        return prompt(context, false, onGranted, onCancelled)
    }

    @JvmStatic
    fun configure(context: Context, enabled: Boolean, onChanged: Runnable) {
        val profileId = ProfileManager.getActiveProfileId(context)
        prompt(context, !KidsMode.hasPin(context), {
            if (KidsMode.setEnabled(context, profileId, enabled)) onChanged.run()
        }, {})
    }

    @JvmStatic
    fun changePin(context: Context) {
        if (KidsMode.hasPin(context)) {
            prompt(context, false, { prompt(context, true, {}, {}) }, {})
        } else {
            prompt(context, true, {}, {})
        }
    }

    private fun prompt(
        context: Context,
        create: Boolean,
        onGranted: Runnable,
        onCancelled: Runnable
    ): AlertDialog {
        val input = pinInput(context, R.string.kids_mode_pin)
        val confirmation = pinInput(context, R.string.kids_mode_pin_confirm)
        val fields = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, 0, padding, 0)
            addView(input)
            if (create) addView(confirmation)
        }
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(if (create) R.string.kids_mode_set_pin else R.string.kids_mode_enter_pin)
            .setMessage(if (create) R.string.kids_mode_pin_setup_help else R.string.kids_mode_pin_help)
            .setView(fields)
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel) { _, _ -> onCancelled.run() }
            .setOnCancelListener { onCancelled.run() }
            .create()
        var work: Disposable? = null
        val owner = context as? LifecycleOwner
        val lifecycleObserver = object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                dialog.dismiss()
            }
        }
        owner?.lifecycle?.addObserver(lifecycleObserver)
        dialog.setOnDismissListener {
            work?.dispose()
            owner?.lifecycle?.removeObserver(lifecycleObserver)
        }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val pin = input.text.toString()
                if (!KidsMode.isValidPin(pin)) {
                    input.error = context.getString(R.string.kids_mode_pin_invalid)
                    return@setOnClickListener
                }
                if (create && pin != confirmation.text.toString()) {
                    confirmation.error = context.getString(R.string.kids_mode_pin_mismatch)
                    return@setOnClickListener
                }
                val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                button.isEnabled = false
                work = Single.fromCallable {
                    if (create) KidsMode.setPin(context, pin) else KidsMode.verifyPin(context, pin)
                }.subscribeOn(Schedulers.computation())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ valid ->
                        button.isEnabled = true
                        if (valid) {
                            KidsMode.grantParentAccess()
                            input.text.clear()
                            confirmation.text.clear()
                            dialog.dismiss()
                            onGranted.run()
                        } else {
                            input.error = context.getString(
                                if (KidsMode.retryAfterMillis(context) > 0) R.string.kids_mode_pin_wait else R.string.kids_mode_pin_wrong
                            )
                        }
                    }, {
                        button.isEnabled = true
                        input.error = context.getString(R.string.kids_mode_pin_error)
                    })
            }
        }
        dialog.show()
        input.requestFocus()
        return dialog
    }

    private fun pinInput(context: Context, hint: Int) = EditText(context).apply {
        setHint(hint)
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        filters = arrayOf(InputFilter.LengthFilter(8))
        isSingleLine = true
        isSaveEnabled = false
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }
    }
}
