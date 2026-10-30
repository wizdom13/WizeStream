/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.recovery

import android.text.format.DateUtils
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.schabi.newpipe.R
import org.schabi.newpipe.local.history.IncognitoMode
import org.schabi.newpipe.player.PlayerIntentType
import org.schabi.newpipe.player.PlayerService
import org.schabi.newpipe.profiles.KidsMode
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.util.NavigationHelper

object QueueRecoveryUi {
    private fun available(activity: AppCompatActivity) = QueueRecovery.enabled(activity) &&
        !IncognitoMode.isEnabled(activity) && !KidsMode.requiresParentAccess(activity)

    @JvmStatic
    fun prepareMenu(activity: AppCompatActivity, menu: Menu) {
        val item = menu.findItem(R.id.action_recover_queue) ?: menu.add(
            Menu.NONE,
            R.id.action_recover_queue,
            Menu.NONE,
            R.string.queue_recovery_title
        )
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        item.isVisible = available(activity)
    }

    @JvmStatic
    fun show(activity: AppCompatActivity) {
        if (!available(activity)) return
        val owner = ProfileManager.getActiveProfileId(activity)
        val epoch = QueueRecovery.generation.get()
        fun valid() = !activity.isFinishing && !activity.isDestroyed && available(activity) &&
            epoch == QueueRecovery.generation.get() && owner == ProfileManager.getActiveProfileId(activity)
        QueueRecovery.load(activity.applicationContext, owner) { snapshots ->
            if (!valid()) return@load
            if (snapshots.isEmpty()) {
                Toast.makeText(activity, R.string.queue_recovery_empty, Toast.LENGTH_LONG).show()
                return@load
            }
            val labels = snapshots.mapIndexed { index, snapshot ->
                activity.getString(if (index == 0) R.string.queue_recovery_recent else R.string.queue_recovery_previous) + "\n" + activity.getString(
                    R.string.queue_recovery_entry,
                    snapshot.entries[snapshot.index].title,
                    snapshot.index + 1,
                    snapshot.entries.size,
                    DateUtils.getRelativeTimeSpanString(snapshot.savedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                )
            }.toTypedArray()
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.queue_recovery_title)
                .setItems(labels) { _, index ->
                    MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.queue_recovery_title)
                        .setMessage(R.string.queue_recovery_confirm)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.queue_recovery_restore) { _, _ ->
                            if (valid()) {
                                val intent = NavigationHelper.getPlayerIntent(
                                    activity,
                                    PlayerService::class.java,
                                    snapshots[index].queue(),
                                    PlayerIntentType.AllOthers
                                ).putExtra(QueueRecovery.RESTORE_EXTRA, true)
                                ContextCompat.startForegroundService(activity, intent)
                            }
                        }.show()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.queue_recovery_disable) { _, _ ->
                    if (valid()) {
                        PreferenceManager.getDefaultSharedPreferences(activity).edit()
                            .putBoolean(QueueRecovery.ENABLED_KEY, false).apply()
                    }
                    activity.invalidateOptionsMenu()
                }.show()
        }
    }
}
