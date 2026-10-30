/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.download.offline

import android.text.InputType
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.local.history.IncognitoMode
import org.schabi.newpipe.profiles.KidsMode
import org.schabi.newpipe.profiles.ProfileManager
import us.shandian.giga.service.DownloadManager

object OfflineLibraryDialog {
    @JvmStatic fun show(activity: AppCompatActivity, manager: DownloadManager, onChanged: Runnable) {
        if (KidsMode.requiresParentAccess(activity) || IncognitoMode.isEnabled(activity)) return
        val profile = ProfileManager.getActiveProfileId(activity)
        val app = activity.applicationContext
        fun valid() = !activity.isFinishing && !activity.isDestroyed && profile == ProfileManager.getActiveProfileId(app) &&
            !KidsMode.requiresParentAccess(app) && !IncognitoMode.isEnabled(app)
        val work = CompositeDisposable()
        val children = mutableListOf<AlertDialog>()
        val existing = OfflineLibrary.rules(app, profile)
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        layout.addView(TextView(activity).apply { setText(R.string.offline_library_help) })
        val enabled = MaterialCheckBox(activity).apply {
            setText(R.string.offline_library_enabled)
            isChecked = existing.enabled
        }
        layout.addView(enabled)
        fun number(hint: Int, value: Long) = TextInputEditText(activity).apply {
            setHint(hint)
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(value.toString())
            layout.addView(this)
        }
        val latest = number(R.string.offline_library_latest, existing.latest.toLong())
        val budget = number(R.string.offline_library_budget, existing.budgetBytes / GIB)
        val watched = MaterialCheckBox(activity).apply {
            setText(R.string.offline_library_watched)
            isChecked = existing.deleteWatched
        }
        layout.addView(watched)
        val protect = MaterialButton(activity).apply { setText(R.string.offline_library_protect) }
        layout.addView(protect)
        fun error() {
            if (valid()) Toast.makeText(activity, R.string.general_error, Toast.LENGTH_LONG).show()
        }
        fun load(action: (List<OfflineLibrary.Entry>) -> Unit) {
            work.add(
                Single.fromCallable { OfflineLibrary.entries(app, manager, profile) }.subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread()).subscribe({ if (valid()) action(it) }, { error() })
            )
        }
        protect.setOnClickListener {
            load { entries ->
                if (entries.isEmpty()) {
                    Toast.makeText(activity, R.string.offline_library_empty, Toast.LENGTH_LONG).show()
                    return@load
                }
                val keep = entries.map { it.item.keep }.toBooleanArray()
                val chooser = MaterialAlertDialogBuilder(activity).setTitle(R.string.offline_library_protect)
                    .setMultiChoiceItems(entries.map { it.mission.displayName }.toTypedArray(), keep) { _, index, checked -> keep[index] = checked }
                    .setPositiveButton(R.string.save) { _, _ ->
                        if (valid()) entries.forEachIndexed { index, entry -> OfflineLibrary.protect(app, entry, keep[index]) }
                    }.setNegativeButton(R.string.cancel, null).create()
                children.add(chooser)
                chooser.show()
            }
        }
        val dialog = MaterialAlertDialogBuilder(activity).setTitle(R.string.offline_library_title)
            .setView(ScrollView(activity).apply { addView(layout) })
            .setPositiveButton(R.string.save, null).setNeutralButton(R.string.offline_library_preview, null)
            .setNegativeButton(R.string.close, null).create()
        val observer = object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                dialog.dismiss()
            }
        }
        activity.lifecycle.addObserver(observer)
        dialog.setOnDismissListener {
            work.clear()
            children.forEach { it.dismiss() }
            activity.lifecycle.removeObserver(observer)
        }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (!valid()) return@setOnClickListener
                val count = latest.text?.toString()?.toIntOrNull()
                val gb = budget.text?.toString()?.toLongOrNull()
                if (count == null || count !in 0..1000 || gb == null || gb !in 0..1024) {
                    latest.error = activity.getString(R.string.offline_library_invalid)
                    return@setOnClickListener
                }
                val rules = RetentionRules(enabled.isChecked, count, gb * GIB, watched.isChecked)
                fun save() {
                    if (valid()) {
                        OfflineLibrary.setRules(app, profile, rules)
                        Toast.makeText(activity, R.string.offline_library_saved, Toast.LENGTH_SHORT).show()
                    }
                }
                if (!rules.enabled) {
                    save()
                } else {
                    val confirmation = MaterialAlertDialogBuilder(activity).setTitle(R.string.offline_library_enabled)
                        .setMessage(R.string.offline_library_confirm_enable).setPositiveButton(R.string.save) { _, _ -> save() }
                        .setNegativeButton(R.string.cancel, null).create()
                    children.add(confirmation)
                    confirmation.show()
                }
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                load { entries ->
                    val candidates = OfflineRetentionPolicy.candidates(entries.map { it.item }, OfflineLibrary.rules(app, profile))
                    if (candidates.isEmpty()) {
                        Toast.makeText(activity, R.string.offline_library_no_cleanup, Toast.LENGTH_LONG).show()
                        return@load
                    }
                    val text = entries.filter { it.item.id in candidates }.joinToString("\n") { it.mission.displayName }
                    val confirmation = MaterialAlertDialogBuilder(activity).setTitle(activity.getString(R.string.offline_library_delete_count, candidates.size))
                        .setMessage(text).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.delete) { _, _ ->
                            if (!valid()) return@setPositiveButton
                            work.add(
                                Single.fromCallable { OfflineLibrary.cleanupSelected(app, manager, candidates) }
                                    .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe({ count ->
                                        if (valid()) {
                                            Toast.makeText(activity, activity.getString(R.string.offline_library_deleted, count), Toast.LENGTH_LONG).show()
                                            onChanged.run()
                                        }
                                    }, { error() })
                            )
                        }.create()
                    children.add(confirmation)
                    confirmation.show()
                }
            }
        }
        dialog.show()
    }

    private const val GIB = 1024L * 1024 * 1024
}
