/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

import android.util.Log
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player as Media3Player
import androidx.media3.common.Player.PositionInfo
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import org.schabi.newpipe.player.ui.PlayerUi

/** Owns Media3 listener events that coordinate multiple player controllers. */
internal class PlayerMedia3ListenerController(
    private val player: Player,
    private val audioController: PlayerAudioController,
    private val metadataController: PlayerMetadataController,
    private val playbackParametersController: PlaybackParametersController,
    private val sponsorBlockController: SponsorBlockPlaybackController,
    private val errorController: PlayerErrorController,
    private val sleepTimerController: SleepTimerPlaybackController
) {
    fun onAudioSessionIdChanged(audioSessionId: Int) {
        audioController.onAudioSessionChanged(audioSessionId)
    }

    fun onEvents(media3Player: Media3Player) {
        metadataController.onEvents(media3Player)
    }

    fun onTimelineChanged(timeline: Timeline, reason: Int) {
        val item = player.currentItem
        if (item != null && player.isLive) {
            playbackParametersController.applySpeedProfile(item)
        }
    }

    fun onTracksChanged(tracks: Tracks) {
        if (Player.DEBUG) {
            Log.d(
                Player.TAG,
                "ExoPlayer - onTracksChanged(), track group size = ${tracks.groups.size}"
            )
        }
        player.UIs().call { ui -> ui.onTextTracksChanged(tracks) }
    }

    fun onPlaybackParametersChanged(parameters: PlaybackParameters) {
        if (Player.DEBUG) {
            Log.d(
                Player.TAG,
                "ExoPlayer - playbackParameters(), speed = [${parameters.speed}], " +
                    "pitch = [${parameters.pitch}]"
            )
        }
        player.UIs().call { ui -> ui.onPlaybackParametersChanged(parameters) }
    }

    fun onPositionDiscontinuity(
        oldPosition: PositionInfo,
        newPosition: PositionInfo,
        reason: Int
    ) {
        if (Player.DEBUG) {
            Log.d(
                Player.TAG,
                "ExoPlayer - onPositionDiscontinuity() called with " +
                    "oldPositionIndex = [${oldPosition.mediaItemIndex}], " +
                    "oldPositionMs = [${oldPosition.positionMs}], " +
                    "newPositionIndex = [${newPosition.mediaItemIndex}], " +
                    "newPositionMs = [${newPosition.positionMs}], " +
                    "discontinuityReason = [$reason]"
            )
        }
        val queue = player.playQueue ?: return
        sponsorBlockController.onPositionDiscontinuity(
            reason == Media3Player.DISCONTINUITY_REASON_SEEK,
            newPosition.positionMs
        )

        val newIndex = newPosition.mediaItemIndex
        if (newIndex != oldPosition.mediaItemIndex) {
            player.UIs().call(PlayerUi::onMediaItemTransition)
            errorController.resetRecovery()
        }
        if (reason == Media3Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
            sleepTimerController.onItemEnded(queue.getItem(oldPosition.mediaItemIndex), true)
        }

        when (reason) {
            Media3Player.DISCONTINUITY_REASON_AUTO_TRANSITION,
            Media3Player.DISCONTINUITY_REASON_REMOVE -> {
                if (player.repeatMode == Media3Player.REPEAT_MODE_ONE &&
                    newIndex == queue.index
                ) {
                    player.registerStreamViewed()
                } else {
                    handleSeek(queue, newIndex)
                }
            }

            Media3Player.DISCONTINUITY_REASON_SEEK -> handleSeek(queue, newIndex)

            Media3Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT,
            Media3Player.DISCONTINUITY_REASON_INTERNAL -> synchronizeQueueIndex(queue, newIndex)

            Media3Player.DISCONTINUITY_REASON_SKIP -> Unit
        }

        if (reason != Media3Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
            sleepTimerController.onPositionDiscontinuity(queue.getItem(newIndex))
        }
    }

    fun onRenderedFirstFrame() {
        player.UIs().call(PlayerUi::onRenderedFirstFrame)
    }

    fun onCues(cueGroup: CueGroup) {
        player.UIs().call { ui -> ui.onCues(cueGroup.cues) }
    }

    fun onVideoSizeChanged(videoSize: VideoSize) {
        if (Player.DEBUG) {
            Log.d(
                Player.TAG,
                "onVideoSizeChanged() called with: width / height = " +
                    "[${videoSize.width} / ${videoSize.height} = " +
                    "${videoSize.width.toFloat() / videoSize.height}], " +
                    "unappliedRotationDegrees = [${videoSize.unappliedRotationDegrees}], " +
                    "pixelWidthHeightRatio = [${videoSize.pixelWidthHeightRatio}]"
            )
        }
        player.UIs().call { ui -> ui.onVideoSizeChanged(videoSize) }
    }

    private fun handleSeek(
        queue: org.schabi.newpipe.player.playqueue.PlayQueue,
        newIndex: Int
    ) {
        if (Player.DEBUG) Log.d(Player.TAG, "ExoPlayer - onSeekProcessed() called")
        if (player.isPreparedForProgressUpdates()) {
            player.saveStreamProgressState()
        }
        synchronizeQueueIndex(queue, newIndex)
    }

    private fun synchronizeQueueIndex(
        queue: org.schabi.newpipe.player.playqueue.PlayQueue,
        newIndex: Int
    ) {
        if (player.currentState != Player.STATE_BLOCKED && newIndex != queue.index) {
            player.saveStreamProgressStateCompleted()
            queue.index = newIndex
        }
    }
}
