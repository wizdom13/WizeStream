/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

import android.content.Context
import android.util.Log
import androidx.core.math.MathUtils
import androidx.media3.common.C
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.schabi.newpipe.R
import org.schabi.newpipe.player.helper.AudioReactor
import org.schabi.newpipe.player.helper.LoadController
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.player.playback.MediaSourceManager
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.ui.PlayerUi

/** Owns player-engine lifecycle, queue-manager lifetime, and recovery positions. */
internal class PlayerLifecycleController(
    private val player: Player,
    private val context: Context,
    private val service: PlayerService,
    private val renderFactory: DefaultRenderersFactory,
    private val trackSelector: DefaultTrackSelector,
    private val loadController: LoadController,
    private val audioController: PlayerAudioController,
    private val broadcastController: PlayerBroadcastController,
    private val errorController: PlayerErrorController,
    private val historyController: PlayerHistoryController,
    private val thumbnailController: PlayerThumbnailController,
    private val localMetadataController: PlayerLocalMetadataController,
    private val sleepTimerController: SleepTimerPlaybackController,
    private val progressController: PlayerProgressController,
    private val playbackParametersController: PlaybackParametersController,
    private val streamItemDisposable: CompositeDisposable
) {
    private var playQueueManager: MediaSourceManager? = null

    fun initPlayback(queue: PlayQueue, playOnReady: Boolean) {
        destroyPlayer()
        initPlayer(playOnReady)
        val skipSilence = player.prefs.getBoolean(
            context.getString(R.string.playback_skip_silence_key),
            player.playbackSkipSilence
        )
        val parameters = PlayerHelper.retrievePlaybackParametersFromPrefs(player)
        playbackParametersController.applyParameters(
            parameters.speed,
            parameters.pitch,
            skipSilence
        )

        player.setPlayQueueForLifecycle(queue)
        queue.init()
        player.exoPlayer.shuffleModeEnabled = queue.isShuffled
        sleepTimerController.onQueueReplaced()
        reloadPlayQueueManager()
        player.UIs().call(PlayerUi::initPlayback)
        player.applyPlayerVolume()
        player.notifyQueueUpdateToListeners()
        player.notifySleepTimerUpdateToListeners()
    }

    fun destroy() {
        if (Player.DEBUG) Log.d(Player.TAG, "destroy() called")
        thumbnailController.cancel()
        localMetadataController.cancel()
        sleepTimerController.clear()
        player.saveStreamProgressState()
        setRecovery()
        player.stopActivityBinding()
        destroyPlayer()
        broadcastController.unregister()
        historyController.clear()
        progressController.clear()
        streamItemDisposable.clear()
        player.UIs().destroyAll(Any::class.java)
    }

    fun setRecovery() {
        val queue = player.playQueue ?: return
        if (player.exoPlayerIsNull()) return
        val position = MathUtils.clamp(
            player.exoPlayer.currentPosition,
            0,
            player.exoPlayer.duration
        )
        setRecovery(queue.index, position)
    }

    fun reloadPlayQueueManager() {
        playQueueManager?.dispose()
        playQueueManager = player.playQueue?.let { MediaSourceManager(player, it) }
    }

    fun shutdown() {
        if (Player.DEBUG) Log.d(Player.TAG, "onPlaybackShutdown() called")
        service.destroyPlayerAndStopService()
    }

    fun smoothStopForImmediateReusing() {
        player.exoPlayer.stop()
        setRecovery()
        player.UIs().call(PlayerUi::smoothStopForImmediateReusing)
    }

    private fun initPlayer(playOnReady: Boolean) {
        if (Player.DEBUG) {
            Log.d(Player.TAG, "initPlayer() called with: playOnReady = [$playOnReady]")
        }
        val exoPlayer = ExoPlayer.Builder(context, renderFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadController)
            .setUsePlatformDiagnostics(false)
            .build()
        player.setExoPlayerForLifecycle(exoPlayer)
        exoPlayer.addListener(player)
        exoPlayer.playWhenReady = playOnReady
        exoPlayer.setSeekParameters(PlayerHelper.getSeekParameters(context))
        exoPlayer.setWakeMode(C.WAKE_MODE_NETWORK)
        exoPlayer.setHandleAudioBecomingNoisy(true)
        audioController.attachAudioSession(exoPlayer.audioSessionId)
        player.setAudioReactorForLifecycle(AudioReactor(context, exoPlayer))
        broadcastController.register()
        player.UIs().call(PlayerUi::initPlayer)
        player.updateAudioTunneling()
    }

    private fun destroyPlayer() {
        if (Player.DEBUG) Log.d(Player.TAG, "destroyPlayer() called")
        errorController.resetRecovery()
        historyController.stopLearningSession()
        player.UIs().call(PlayerUi::destroyPlayer)
        audioController.releaseAudioSession()
        if (!player.exoPlayerIsNull()) {
            player.exoPlayer.removeListener(player)
            player.exoPlayer.stop()
            player.exoPlayer.release()
        }
        if (player.isProgressLoopRunning) player.stopProgressLoop()
        player.playQueue?.dispose()
        player.audioReactor?.dispose()
        playQueueManager?.dispose()
    }

    private fun setRecovery(queuePosition: Int, windowPosition: Long) {
        val queue = player.playQueue ?: return
        if (queue.size() <= queuePosition) return
        if (Player.DEBUG) {
            Log.d(
                Player.TAG,
                "Setting recovery, queue: $queuePosition, pos: $windowPosition"
            )
        }
        queue.setRecovery(queuePosition, windowPosition)
    }
}
