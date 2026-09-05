/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

import androidx.media3.common.PlaybackException
import java.util.Optional
import org.schabi.newpipe.player.event.PlayerEventListener
import org.schabi.newpipe.player.event.PlayerServiceEventListener
import org.schabi.newpipe.player.ui.PlayerUi

/** Owns activity and fragment listener bindings and dispatches player state updates. */
internal class PlayerEventDispatcher(private val player: Player) {
    private var fragmentListener: PlayerServiceEventListener? = null
    private var activityListener: PlayerEventListener? = null

    fun setFragmentListener(listener: PlayerServiceEventListener) {
        fragmentListener = listener
        player.UIs().call(PlayerUi::onFragmentListenerSet)
        notifyQueueUpdate()
        notifyMetadataUpdate()
        notifyPlaybackUpdate()
        notifySleepTimerUpdate()
        player.triggerProgressUpdate()
    }

    fun removeFragmentListener(listener: PlayerServiceEventListener) {
        if (fragmentListener === listener) {
            fragmentListener = null
        }
    }

    fun setActivityListener(listener: PlayerEventListener) {
        activityListener = listener
        notifyMetadataUpdate()
        notifyPlaybackUpdate()
        notifySleepTimerUpdate()
        player.triggerProgressUpdate()
    }

    fun removeActivityListener(listener: PlayerEventListener) {
        if (activityListener === listener) {
            activityListener = null
        }
    }

    fun stopBindings() {
        fragmentListener?.onServiceStopped()
        fragmentListener = null
        activityListener?.onServiceStopped()
        activityListener = null
    }

    fun notifyQueueUpdate() {
        val queue = player.playQueue ?: return
        fragmentListener?.onQueueUpdate(queue)
        activityListener?.onQueueUpdate(queue)
    }

    fun notifyMetadataUpdate() {
        val streamInfo = player.currentStreamInfo
        streamInfo.ifPresent { info ->
            fragmentListener?.onMetadataUpdate(info, player.playQueue)
            activityListener?.onMetadataUpdate(info, player.playQueue)
        }

        val metadata = player.currentMetadata
        val queue = player.playQueue
        if (streamInfo.isEmpty && metadata != null && queue != null) {
            fragmentListener?.onMetadataUpdate(metadata, queue)
            activityListener?.onMetadataUpdate(metadata, queue)
        }
    }

    fun notifyPlaybackUpdate() {
        val queue = player.playQueue
        if (player.exoPlayerIsNull() || queue == null) {
            return
        }
        val parameters = player.exoPlayer.playbackParameters
        fragmentListener?.onPlaybackUpdate(
            player.currentState,
            player.repeatMode,
            queue.isShuffled,
            parameters
        )
        activityListener?.onPlaybackUpdate(
            player.currentState,
            player.repeatMode,
            queue.isShuffled,
            parameters
        )
    }

    fun notifyProgressUpdate(currentProgress: Int, duration: Int, bufferPercent: Int) {
        fragmentListener?.onProgressUpdate(currentProgress, duration, bufferPercent)
        activityListener?.onProgressUpdate(currentProgress, duration, bufferPercent)
    }

    fun notifyAudioTrackUpdate() {
        fragmentListener?.onAudioTrackUpdate()
        activityListener?.onAudioTrackUpdate()
    }

    fun notifySleepTimerUpdate() {
        val mode = player.sleepTimerMode
        val remainingMillis = player.sleepTimerRemainingMillis
        val fadeOutEnabled = player.isSleepTimerFadeOutEnabled
        player.UIs().call { ui ->
            ui.onSleepTimerChanged(mode, remainingMillis, fadeOutEnabled)
        }
        fragmentListener?.onSleepTimerChanged(mode, remainingMillis, fadeOutEnabled)
        activityListener?.onSleepTimerChanged(mode, remainingMillis, fadeOutEnabled)
    }

    fun notifyPlayerError(error: PlaybackException, isCatchable: Boolean) {
        fragmentListener?.onPlayerError(error, isCatchable)
    }

    fun getFragmentListener(): Optional<PlayerServiceEventListener> {
        return Optional.ofNullable(fragmentListener)
    }
}
