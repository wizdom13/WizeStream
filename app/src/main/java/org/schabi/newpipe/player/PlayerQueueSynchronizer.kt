/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

import android.util.Log
import org.schabi.newpipe.player.playqueue.PlayQueueItem

/** Keeps the Media3 timeline position synchronized with the logical play queue. */
internal class PlayerQueueSynchronizer(
    private val player: Player,
    private val historyController: PlayerHistoryController,
    private val playbackParametersController: PlaybackParametersController,
    private val thumbnailController: PlayerThumbnailController
) {
    fun synchronize(item: PlayQueueItem, wasBlocked: Boolean) {
        if (Player.DEBUG) {
            Log.d(
                Player.TAG,
                "Playback - onPlaybackSynchronize(was blocked: $wasBlocked) called with " +
                    "item=[${item.title}], url=[${item.url}]"
            )
        }
        val playQueue = player.playQueue ?: return
        if (player.exoPlayerIsNull() || player.currentItem === item) {
            return
        }

        val exoPlayer = player.exoPlayer
        val playQueueIndex = playQueue.indexOf(item)
        val playlistIndex = exoPlayer.currentMediaItemIndex
        val playlistSize = exoPlayer.currentTimeline.windowCount
        val previousItem = player.currentItem
        val removeThumbnailBeforeSync = previousItem == null ||
            previousItem.serviceId != item.serviceId ||
            previousItem.url != item.url

        historyController.stopLearningSession()
        player.setCurrentItemForPlaybackSynchronization(item)
        historyController.updateLearningSession()
        playbackParametersController.applySpeedProfile(item)

        when {
            playQueueIndex != playQueue.index -> {
                Log.e(
                    Player.TAG,
                    "Playback - Play Queue may be not in sync: item index=[$playQueueIndex], " +
                        "queue index=[${playQueue.index}]"
                )
            }

            playlistSize > 0 && playQueueIndex >= playlistSize || playQueueIndex < 0 -> {
                Log.e(
                    Player.TAG,
                    "Playback - Trying to seek to invalid index=[$playQueueIndex] " +
                        "with playlist length=[$playlistSize]"
                )
            }

            wasBlocked || playlistIndex != playQueueIndex || !player.isPlaying -> {
                if (Player.DEBUG) {
                    Log.d(
                        Player.TAG,
                        "Playback - Rewinding to correct index=[$playQueueIndex], " +
                            "from=[$playlistIndex], size=[$playlistSize]."
                    )
                }
                if (removeThumbnailBeforeSync) {
                    thumbnailController.clear()
                }

                if (item.recoveryPosition != PlayQueueItem.RECOVERY_UNSET) {
                    exoPlayer.seekTo(playQueueIndex, item.recoveryPosition)
                    playQueue.unsetRecovery(playQueueIndex)
                } else {
                    exoPlayer.seekToDefaultPosition(playQueueIndex)
                }
            }
        }
    }
}
