/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.recovery

import android.content.SharedPreferences
import android.os.SystemClock
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import org.schabi.newpipe.local.history.IncognitoMode
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.profiles.ProfileManager

/** A queue keeps its original owner and permanently loses persistence eligibility on privacy changes. */
internal class QueueRecoveryController(
    private val player: Player,
    private val save: (String, QueueSnapshot, AtomicBoolean, Long) -> Unit = { owner, snapshot, eligible, epoch ->
        QueueRecovery.save(player.context, owner, snapshot, eligible, epoch)
    }
) {
    private var queue: PlayQueue? = null
    private var session = ""
    private var owner = ""
    private var eligible = AtomicBoolean(false)
    private var lastSave = 0L
    private var epoch = 0L
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == ProfileManager.ACTIVE_PROFILE_ID_PREFERENCE_KEY || !allowed()) eligible.set(false)
    }

    init {
        player.prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    private fun allowed() = QueueRecovery.enabled(player.context) && !IncognitoMode.isEnabled(player.context)

    fun bind(value: PlayQueue) {
        if (queue === value) return
        queue = value
        owner = ProfileManager.getActiveProfileId(player.context)
        session = UUID.randomUUID().toString()
        eligible = AtomicBoolean(allowed())
        epoch = QueueRecovery.generation.get()
        lastSave = 0
    }

    fun checkpoint(force: Boolean = false) {
        val current = queue ?: return
        if (!allowed() || owner != ProfileManager.getActiveProfileId(player.context)) eligible.set(false)
        if (!eligible.get() || epoch != QueueRecovery.generation.get() || player.playQueue !== current) return
        val elapsed = SystemClock.elapsedRealtime()
        if (!force && elapsed - lastSave < 15_000) return
        val item = current.item ?: return
        val position = if (!player.exoPlayerIsNull() && player.currentItem === item &&
            !player.exoPlayer.currentTimeline.isEmpty && player.exoPlayer.currentMediaItemIndex == current.index
        ) {
            player.exoPlayer.contentPosition
        } else {
            item.recoveryPosition
        }
        val snapshot = QueueSnapshot.capture(current, session, position, System.currentTimeMillis()) ?: return
        lastSave = elapsed
        save(owner, snapshot, eligible, epoch)
    }

    fun close() {
        checkpoint(true)
        player.prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
