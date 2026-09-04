/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.media3.common.C
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import org.schabi.newpipe.R
import org.schabi.newpipe.player.helper.SleepTimer
import org.schabi.newpipe.player.playqueue.PlayQueueItem

/** Owns sleep-timer state, queue targets, fade-out volume, and lifecycle updates. */
internal class SleepTimerPlaybackController(private val player: Player) {
    private val timer = SleepTimer()
    private val handler = Handler(Looper.getMainLooper())
    private val tick = Runnable(::onTick)

    private var currentItemTarget: PlayQueueItem? = null
    private var queueTarget: PlayQueueItem? = null
    private var queueTargetFollowsLoading = false

    var volumeMultiplier = 1.0f
        private set

    val isActive: Boolean
        get() = timer.isActive

    val mode: SleepTimer.Mode
        get() = timer.mode

    val isFadeOutEnabled: Boolean
        get() = timer.isFadeOutEnabled

    fun startDuration(durationMillis: Long, fadeOut: Boolean) {
        prepareStart()
        timer.startDuration(durationMillis, fadeOut)
        startUpdates()
    }

    fun startAtEndOfCurrent(fadeOut: Boolean): Boolean {
        val item = currentQueueItem() ?: return false
        prepareStart()
        currentItemTarget = item
        timer.startEndOfCurrent(fadeOut)
        startUpdates()
        return true
    }

    fun startAtEndOfQueue(fadeOut: Boolean): Boolean {
        val queue = player.playQueue ?: return false
        val item = lastQueueItem() ?: return false
        prepareStart()
        queueTarget = item
        queueTargetFollowsLoading = !queue.isComplete
        timer.startEndOfQueue(fadeOut)
        startUpdates()
        return true
    }

    fun cancel() {
        if (!timer.isActive) {
            return
        }
        resetState()
        setVolumeMultiplier(1.0f)
        player.notifySleepTimerUpdateToListeners()
    }

    fun clear() {
        resetState()
        setVolumeMultiplier(1.0f)
    }

    fun onQueueReplaced() {
        when (timer.mode) {
            SleepTimer.Mode.END_OF_CURRENT -> {
                currentItemTarget = currentQueueItem()
                if (currentItemTarget == null) {
                    clear()
                }
            }

            SleepTimer.Mode.END_OF_QUEUE -> {
                val queue = player.playQueue
                queueTarget = lastQueueItem()
                queueTargetFollowsLoading = queue != null && !queue.isComplete
                if (queueTarget == null) {
                    clear()
                }
            }

            else -> Unit
        }
    }

    fun onShuffleModeChanged() {
        if (timer.mode != SleepTimer.Mode.END_OF_QUEUE) {
            return
        }
        val queue = player.playQueue ?: return
        queueTarget = lastQueueItem()
        queueTargetFollowsLoading = !queue.isComplete
        player.notifySleepTimerUpdateToListeners()
    }

    fun onItemEnded(endedItem: PlayQueueItem?, pausePlayback: Boolean) {
        if (endedItem == null || !timer.isActive) {
            return
        }
        val targetReached =
            timer.mode == SleepTimer.Mode.END_OF_CURRENT && endedItem === currentItemTarget ||
                timer.mode == SleepTimer.Mode.END_OF_QUEUE && endedItem === queueTarget
        if (targetReached) {
            finish(pausePlayback)
        }
    }

    fun onCurrentItemChanged() {
        if (timer.mode == SleepTimer.Mode.END_OF_CURRENT) {
            currentItemTarget = currentQueueItem()
            player.notifySleepTimerUpdateToListeners()
        }
    }

    fun onPositionDiscontinuity(item: PlayQueueItem?) {
        if (timer.mode == SleepTimer.Mode.END_OF_CURRENT) {
            currentItemTarget = item
            player.notifySleepTimerUpdateToListeners()
        }
    }

    fun onQueueItemSelected(item: PlayQueueItem) {
        if (timer.mode == SleepTimer.Mode.END_OF_CURRENT) {
            currentItemTarget = item
            player.notifySleepTimerUpdateToListeners()
        }
    }

    fun onQueueEdited() {
        val queue = player.playQueue ?: return
        if (
            timer.mode == SleepTimer.Mode.END_OF_CURRENT &&
            findQueueItemIndex(currentItemTarget) < 0
        ) {
            currentItemTarget = currentQueueItem()
            if (currentItemTarget == null) {
                clear()
            }
        } else if (
            timer.mode == SleepTimer.Mode.END_OF_QUEUE &&
            (queueTargetFollowsLoading || findQueueItemIndex(queueTarget) < 0)
        ) {
            queueTarget = lastQueueItem()
            queueTargetFollowsLoading = !queue.isComplete
            if (queueTarget == null) {
                clear()
            }
        }
    }

    fun remainingMillis(): Long =
        when (timer.mode) {
            SleepTimer.Mode.DURATION -> timer.durationRemainingMillis
            SleepTimer.Mode.END_OF_CURRENT -> {
                if (currentQueueItem() === currentItemTarget) {
                    currentItemRemainingMillis()
                } else {
                    SleepTimer.REMAINING_TIME_UNSET
                }
            }

            SleepTimer.Mode.END_OF_QUEUE -> queueTargetRemainingMillis()
            SleepTimer.Mode.NONE -> SleepTimer.REMAINING_TIME_UNSET
        }

    private fun prepareStart() {
        resetState()
        setVolumeMultiplier(1.0f)
    }

    private fun resetState() {
        handler.removeCallbacks(tick)
        timer.cancel()
        currentItemTarget = null
        queueTarget = null
        queueTargetFollowsLoading = false
    }

    private fun startUpdates() {
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    private fun onTick() {
        if (!timer.isActive) {
            return
        }
        if (timer.hasDurationExpired()) {
            finish(true)
            return
        }

        setVolumeMultiplier(timer.getFadeOutVolumeMultiplier(fadeOutRemainingMillis()))
        player.notifySleepTimerUpdateToListeners()
        handler.postDelayed(tick, UPDATE_INTERVAL_MILLIS)
    }

    private fun setVolumeMultiplier(value: Float) {
        val clampedValue = value.coerceIn(0.0f, 1.0f)
        if (abs(volumeMultiplier - clampedValue) < VOLUME_CHANGE_EPSILON) {
            return
        }
        volumeMultiplier = clampedValue
        player.applyPlayerVolume()
    }

    private fun finish(pausePlayback: Boolean) {
        if (!timer.isActive) {
            return
        }
        resetState()
        if (pausePlayback && !player.exoPlayerIsNull() && player.playWhenReady) {
            player.pause()
        }
        setVolumeMultiplier(1.0f)
        player.notifySleepTimerUpdateToListeners()
        Toast.makeText(player.context, R.string.sleep_timer_finished, Toast.LENGTH_SHORT).show()
    }

    private fun fadeOutRemainingMillis(): Long =
        when (timer.mode) {
            SleepTimer.Mode.DURATION -> timer.durationRemainingMillis
            SleepTimer.Mode.END_OF_CURRENT -> {
                if (currentQueueItem() === currentItemTarget) {
                    currentItemRemainingMillis()
                } else {
                    SleepTimer.REMAINING_TIME_UNSET
                }
            }

            SleepTimer.Mode.END_OF_QUEUE -> {
                if (currentQueueItem() === queueTarget) {
                    currentItemRemainingMillis()
                } else {
                    SleepTimer.REMAINING_TIME_UNSET
                }
            }

            SleepTimer.Mode.NONE -> SleepTimer.REMAINING_TIME_UNSET
        }

    private fun queueTargetRemainingMillis(): Long {
        val queue = player.playQueue ?: return SleepTimer.REMAINING_TIME_UNSET
        if (player.exoPlayerIsNull()) {
            return SleepTimer.REMAINING_TIME_UNSET
        }
        val currentIndex = queue.index
        val targetIndex = findQueueItemIndex(queueTarget)
        if (targetIndex < currentIndex || targetIndex < 0) {
            return SleepTimer.REMAINING_TIME_UNSET
        }

        var remainingMillis = currentItemRemainingMillis()
        if (remainingMillis == SleepTimer.REMAINING_TIME_UNSET) {
            return SleepTimer.REMAINING_TIME_UNSET
        }
        for (index in currentIndex + 1..targetIndex) {
            val item = queue.getItem(index)
            if (item == null || item.duration <= 0L) {
                return SleepTimer.REMAINING_TIME_UNSET
            }
            val itemDurationMillis = playbackTimeToWallClockMillis(
                TimeUnit.SECONDS.toMillis(item.duration)
            )
            if (Long.MAX_VALUE - remainingMillis < itemDurationMillis) {
                return SleepTimer.REMAINING_TIME_UNSET
            }
            remainingMillis += itemDurationMillis
        }
        return remainingMillis
    }

    private fun currentItemRemainingMillis(): Long {
        if (player.exoPlayerIsNull()) {
            return SleepTimer.REMAINING_TIME_UNSET
        }
        val exoPlayer = player.exoPlayer
        var durationMillis = exoPlayer.duration
        if (durationMillis == C.TIME_UNSET || durationMillis <= 0L) {
            val item = currentQueueItem()
            if (item == null || item.duration <= 0L) {
                return SleepTimer.REMAINING_TIME_UNSET
            }
            durationMillis = TimeUnit.SECONDS.toMillis(item.duration)
        }
        val mediaRemainingMillis =
            (durationMillis - exoPlayer.currentPosition.coerceAtLeast(0L)).coerceAtLeast(0L)
        return playbackTimeToWallClockMillis(mediaRemainingMillis)
    }

    private fun playbackTimeToWallClockMillis(playbackTimeMillis: Long): Long {
        return (playbackTimeMillis / player.playbackSpeed.coerceAtLeast(0.01f)).toLong()
    }

    private fun currentQueueItem(): PlayQueueItem? = player.playQueue?.item

    private fun lastQueueItem(): PlayQueueItem? {
        val queue = player.playQueue
        return if (queue == null || queue.isEmpty) null else queue.getItem(queue.size() - 1)
    }

    private fun findQueueItemIndex(target: PlayQueueItem?): Int {
        val queue = player.playQueue
        return if (queue == null || target == null) -1 else queue.indexOf(target)
    }

    private companion object {
        const val UPDATE_INTERVAL_MILLIS = 1_000L
        const val VOLUME_CHANGE_EPSILON = 0.001f
    }
}
