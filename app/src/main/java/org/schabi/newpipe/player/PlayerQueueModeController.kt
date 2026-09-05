/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

import android.util.Log
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Player.RepeatMode

/** Owns repeat and shuffle commands and synchronizes queue-mode changes with player surfaces. */
internal class PlayerQueueModeController(
    private val player: Player,
    private val sleepTimerController: SleepTimerPlaybackController
) {
    @RepeatMode
    fun getRepeatMode(): Int =
        if (player.exoPlayerIsNull()) REPEAT_MODE_OFF else player.exoPlayer.repeatMode

    fun cycleNextRepeatMode() {
        if (player.exoPlayerIsNull()) {
            return
        }
        val repeatMode = when (player.exoPlayer.repeatMode) {
            REPEAT_MODE_OFF -> REPEAT_MODE_ONE
            REPEAT_MODE_ONE -> REPEAT_MODE_ALL
            else -> REPEAT_MODE_OFF
        }
        player.exoPlayer.repeatMode = repeatMode
    }

    fun onRepeatModeChanged(@RepeatMode repeatMode: Int) {
        if (Player.DEBUG) {
            Log.d(
                Player.TAG,
                "ExoPlayer - onRepeatModeChanged() called with: repeatMode = [$repeatMode]"
            )
        }
        player.UIs().call { ui -> ui.onRepeatModeChanged(repeatMode) }
        player.notifyPlaybackUpdateToListeners()
    }

    fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        if (Player.DEBUG) {
            Log.d(
                Player.TAG,
                "ExoPlayer - onShuffleModeEnabledChanged() called with: mode = " +
                    "[$shuffleModeEnabled]"
            )
        }

        player.playQueue?.let { queue ->
            if (shuffleModeEnabled && !queue.isShuffled) {
                queue.shuffle()
            } else if (!shuffleModeEnabled && queue.isShuffled) {
                queue.unshuffle()
            }
            sleepTimerController.onShuffleModeChanged()
        }

        player.UIs().call { ui -> ui.onShuffleModeEnabledChanged(shuffleModeEnabled) }
        player.notifyPlaybackUpdateToListeners()
    }

    fun toggleShuffleModeEnabled() {
        if (!player.exoPlayerIsNull()) {
            player.exoPlayer.shuffleModeEnabled = !player.exoPlayer.shuffleModeEnabled
        }
    }
}
