/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

import android.util.Log
import androidx.media3.common.Player as Media3Player
import androidx.media3.exoplayer.source.MediaSource
import org.schabi.newpipe.player.ui.PlayerUi

/** Owns Media3 playback-state interpretation and WizeStream state transitions. */
internal class PlayerStateController(
    private val player: Player,
    private val historyController: PlayerHistoryController,
    private val sleepTimerController: SleepTimerPlaybackController,
    private val sponsorBlockController: SponsorBlockPlaybackController,
    private val progressController: PlayerProgressController,
    private val eventDispatcher: PlayerEventDispatcher
) {
    var currentState: Int = Player.STATE_PREFLIGHT
        private set

    var isPrepared: Boolean = false
        private set

    fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (Player.DEBUG) {
            Log.d(
                Player.TAG,
                "ExoPlayer - onPlayWhenReadyChanged() called with: " +
                    "playWhenReady = [$playWhenReady], reason = [$reason]"
            )
        }
        val playbackState = if (player.exoPlayerIsNull()) {
            Media3Player.STATE_IDLE
        } else {
            player.exoPlayer.playbackState
        }
        updatePlaybackState(playWhenReady, playbackState)
    }

    fun onPlaybackStateChanged(playbackState: Int) {
        if (Player.DEBUG) {
            Log.d(
                Player.TAG,
                "ExoPlayer - onPlaybackStateChanged() called with: " +
                    "playbackState = [$playbackState]"
            )
        }
        updatePlaybackState(player.playWhenReady, playbackState)
    }

    fun onIsLoadingChanged(isLoading: Boolean) {
        if (!isLoading && currentState == Player.STATE_PAUSED && progressController.isRunning()) {
            progressController.stop()
        } else if (isLoading && !progressController.isRunning()) {
            progressController.start()
        }
    }

    fun block() {
        if (player.exoPlayerIsNull()) {
            return
        }
        if (Player.DEBUG) {
            Log.d(Player.TAG, "Playback - onPlaybackBlock() called")
        }
        player.clearCurrentPlaybackForBlock()
        player.exoPlayer.stop()
        isPrepared = false
        changeState(Player.STATE_BLOCKED)
    }

    fun unblock(mediaSource: MediaSource) {
        if (Player.DEBUG) {
            Log.d(Player.TAG, "Playback - onPlaybackUnblock() called")
        }
        if (player.exoPlayerIsNull()) {
            return
        }
        if (currentState == Player.STATE_BLOCKED) {
            changeState(Player.STATE_BUFFERING)
        }
        player.exoPlayer.setMediaSource(mediaSource, false)
        player.exoPlayer.prepare()
    }

    fun changeState(state: Int) {
        if (Player.DEBUG) {
            Log.d(Player.TAG, "changeState() called with: state = [$state]")
        }
        currentState = state
        historyController.updateLearningSession()
        when (state) {
            Player.STATE_BLOCKED -> onBlocked()
            Player.STATE_PLAYING -> onPlaying()
            Player.STATE_BUFFERING -> onBuffering()
            Player.STATE_PAUSED -> onPaused()
            Player.STATE_PAUSED_SEEK -> onPausedSeek()
            Player.STATE_COMPLETED -> onCompleted()
        }
        eventDispatcher.notifyPlaybackUpdate()
    }

    fun onBuffering() {
        if (Player.DEBUG) {
            Log.d(Player.TAG, "onBuffering() called")
        }
        player.UIs().call(PlayerUi::onBuffering)
    }

    private fun updatePlaybackState(playWhenReady: Boolean, playbackState: Int) {
        if (Player.DEBUG) {
            Log.d(
                Player.TAG,
                "ExoPlayer - updatePlaybackState() called with: " +
                    "playWhenReady = [$playWhenReady], playbackState = [$playbackState]"
            )
        }
        if (currentState == Player.STATE_PAUSED_SEEK) {
            if (Player.DEBUG) {
                Log.d(Player.TAG, "updatePlaybackState() is currently blocked")
            }
            return
        }

        when (playbackState) {
            Media3Player.STATE_IDLE -> isPrepared = false
            Media3Player.STATE_BUFFERING -> {
                if (isPrepared) {
                    changeState(Player.STATE_BUFFERING)
                }
            }
            Media3Player.STATE_READY -> {
                if (!isPrepared) {
                    isPrepared = true
                    onPrepared(playWhenReady)
                }
                changeState(if (playWhenReady) Player.STATE_PLAYING else Player.STATE_PAUSED)
            }
            Media3Player.STATE_ENDED -> {
                sleepTimerController.onItemEnded(player.playQueue?.item, false)
                changeState(Player.STATE_COMPLETED)
                player.saveStreamProgressStateCompleted()
                isPrepared = false
            }
        }
    }

    private fun onPrepared(playWhenReady: Boolean) {
        if (Player.DEBUG) {
            Log.d(Player.TAG, "onPrepared() called with: playWhenReady = [$playWhenReady]")
        }
        player.UIs().call(PlayerUi::onPrepared)
        if (playWhenReady && !player.isMuted) {
            player.audioReactor?.requestAudioFocus()
        }
    }

    private fun onBlocked() {
        if (Player.DEBUG) Log.d(Player.TAG, "onBlocked() called")
        if (!progressController.isRunning()) progressController.start()
        sponsorBlockController.hideManualSkipButton()
        player.UIs().call(PlayerUi::onBlocked)
    }

    private fun onPlaying() {
        if (Player.DEBUG) Log.d(Player.TAG, "onPlaying() called")
        if (!progressController.isRunning()) progressController.start()
        player.UIs().call(PlayerUi::onPlaying)
    }

    private fun onPaused() {
        if (Player.DEBUG) Log.d(Player.TAG, "onPaused() called")
        if (progressController.isRunning()) progressController.stop()
        sponsorBlockController.hideManualSkipButton()
        player.UIs().call(PlayerUi::onPaused)
    }

    private fun onPausedSeek() {
        if (Player.DEBUG) Log.d(Player.TAG, "onPausedSeek() called")
        player.UIs().call(PlayerUi::onPausedSeek)
    }

    private fun onCompleted() {
        val playQueue = player.playQueue
        if (Player.DEBUG) {
            Log.d(
                Player.TAG,
                "onCompleted() called" + if (playQueue == null) ". playQueue is null" else ""
            )
        }
        playQueue ?: return
        sponsorBlockController.hideManualSkipButton()
        player.UIs().call(PlayerUi::onCompleted)
        if (playQueue.index < playQueue.size() - 1) playQueue.offsetIndex(1)
        if (progressController.isRunning()) progressController.stop()
    }
}
