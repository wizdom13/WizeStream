/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

import android.util.Log

/** Owns play, pause, and queue-navigation transport actions. */
internal class PlayerTransportController(
    private val player: Player,
    private val sleepTimerController: SleepTimerPlaybackController
) {
    fun play() {
        if (Player.DEBUG) {
            Log.d(Player.TAG, "play() called")
        }
        val audioReactor = player.audioReactor ?: return
        val playQueue = player.playQueue ?: return
        if (player.exoPlayerIsNull()) {
            return
        }

        if (!player.isMuted) {
            audioReactor.requestAudioFocus()
        }

        if (player.currentState == Player.STATE_COMPLETED) {
            if (playQueue.index == 0) {
                player.seekToDefault()
            } else {
                playQueue.index = 0
            }
        }

        if (player.isStopped) {
            player.setRecovery()
            player.reloadPlayQueueManager()
        }

        player.exoPlayer.play()
        player.saveStreamProgressState()
    }

    fun pause() {
        if (Player.DEBUG) {
            Log.d(Player.TAG, "pause() called")
        }
        val audioReactor = player.audioReactor ?: return
        if (player.exoPlayerIsNull()) {
            return
        }

        audioReactor.abandonAudioFocus()
        player.exoPlayer.pause()
        player.saveStreamProgressState()
    }

    fun playPause() {
        if (Player.DEBUG) {
            Log.d(Player.TAG, "onPlayPause() called")
        }
        if (player.playWhenReady && player.currentState != Player.STATE_COMPLETED) {
            pause()
        } else {
            play()
        }
    }

    fun playPrevious() {
        if (Player.DEBUG) {
            Log.d(Player.TAG, "onPlayPrevious() called")
        }
        val playQueue = player.playQueue ?: return
        if (player.exoPlayerIsNull()) {
            return
        }

        if (player.exoPlayer.currentPosition > Player.PLAY_PREV_ACTIVATION_LIMIT_MILLIS ||
            playQueue.index == 0
        ) {
            player.seekToDefault()
            playQueue.offsetIndex(0)
        } else {
            player.saveStreamProgressState()
            playQueue.offsetIndex(-1)
        }
        sleepTimerController.onCurrentItemChanged()
        player.triggerProgressUpdate()
    }

    fun playNext() {
        if (Player.DEBUG) {
            Log.d(Player.TAG, "onPlayNext() called")
        }
        val playQueue = player.playQueue ?: return

        player.saveStreamProgressState()
        playQueue.offsetIndex(1)
        sleepTimerController.onCurrentItemChanged()
        player.triggerProgressUpdate()
    }
}
