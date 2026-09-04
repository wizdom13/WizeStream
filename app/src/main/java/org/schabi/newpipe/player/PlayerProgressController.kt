/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

import android.util.Log
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.SerialDisposable
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.math.max

/** Owns periodic playback progress sampling and delivery to player observers. */
internal class PlayerProgressController(
    private val player: Player,
    private val historyController: PlayerHistoryController,
    private val sponsorBlockController: SponsorBlockPlaybackController,
    private val eventDispatcher: PlayerEventDispatcher
) {
    private val progressUpdates = SerialDisposable()

    fun start() {
        progressUpdates.set(
            Observable.interval(
                Player.PROGRESS_LOOP_INTERVAL_MILLIS.toLong(),
                MILLISECONDS,
                AndroidSchedulers.mainThread()
            )
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { trigger() },
                    { error -> Log.e(Player.TAG, "Progress update failure: ", error) }
                )
        )
    }

    fun stop() {
        progressUpdates.set(null)
    }

    fun isRunning(): Boolean = progressUpdates.get() != null

    fun trigger() {
        if (player.exoPlayerIsNull()) {
            return
        }

        historyController.updateLearningSession()
        sponsorBlockController.onProgress()
        val exoPlayer = player.exoPlayer
        dispatch(
            max(exoPlayer.currentPosition.toInt(), 0),
            exoPlayer.duration.toInt(),
            exoPlayer.bufferedPercentage
        )
    }

    fun clear() {
        progressUpdates.set(null)
    }

    private fun dispatch(currentProgress: Int, duration: Int, bufferPercent: Int) {
        if (player.isPreparedForProgressUpdates) {
            player.UIs().call { ui ->
                ui.onUpdateProgress(currentProgress, duration, bufferPercent)
            }
            eventDispatcher.notifyProgressUpdate(currentProgress, duration, bufferPercent)
        }
    }
}
