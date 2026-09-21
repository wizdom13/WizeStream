/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

import android.content.Context
import android.util.Log
import androidx.core.math.MathUtils
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionParameters
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
        val trackSelectionParameters = player.getTrackSelectorForLifecycle()?.parameters
        destroyPlayer()
        initPlayer(playOnReady, trackSelectionParameters)
        val skipSilence = player.prefs.getBoolean(
            context.getString(R.string.playback_skip_silence_key),
            player.playbackSkipSilence
        )
        val parameters = PlayerHelper.retrievePlaybackParametersFromPrefs(player)
        val initialSpeed = PlaybackParametersController.resolvePlaybackSpeed(
            queue.item?.streamType,
            null,
            parameters.speed
        )
        playbackParametersController.applyParameters(
            initialSpeed,
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

    /** Replace only the engine; keep queue ordering, pause state and the running sleep timer. */
    fun restartForVideoAdjustments() {
        val queue = player.playQueue ?: return
        val previous = player.getExoPlayer() ?: return
        val playOnReady = previous.playWhenReady
        val parameters = previous.playbackParameters
        val skipSilence = previous.skipSilenceEnabled
        val repeatMode = previous.repeatMode
        val shuffle = previous.shuffleModeEnabled
        val trackSelectionParameters = player.getTrackSelectorForLifecycle()?.parameters
        if (!previous.currentTimeline.isEmpty) {
            queue.setRecovery(queue.index, previous.currentPosition.coerceAtLeast(0))
        }
        destroyPlayer(preserveQueue = true)
        initPlayer(playOnReady, trackSelectionParameters)
        player.exoPlayer.apply {
            playbackParameters = parameters
            skipSilenceEnabled = skipSilence
            this.repeatMode = repeatMode
            shuffleModeEnabled = shuffle
        }
        reloadPlayQueueManager()
        player.UIs().call(PlayerUi::initPlayback)
        player.applyPlayerVolume()
        player.notifyPlaybackUpdateToListeners()
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

    private fun initPlayer(
        playOnReady: Boolean,
        trackSelectionParameters: TrackSelectionParameters? = null
    ) {
        if (Player.DEBUG) {
            Log.d(Player.TAG, "initPlayer() called with: playOnReady = [$playOnReady]")
        }
        val trackSelector = DefaultTrackSelector(context, PlayerHelper.getQualitySelector()).apply {
            if (trackSelectionParameters != null) {
                setParameters(trackSelectionParameters)
            }
        }
        val loadController = LoadController()
        val exoPlayer = ExoPlayer.Builder(context, renderFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadController)
            .setUsePlatformDiagnostics(false)
            .build()
        player.setTrackSelectorForLifecycle(trackSelector)
        player.setLoadControllerForLifecycle(loadController)
        player.setExoPlayerForLifecycle(exoPlayer)
        player.videoAdjustments.attach(exoPlayer)
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

    private fun destroyPlayer(preserveQueue: Boolean = false) {
        if (Player.DEBUG) Log.d(Player.TAG, "destroyPlayer() called")
        errorController.resetRecovery()
        historyController.stopLearningSession()
        val exoPlayer = player.getExoPlayer()
        val trackSelector = player.getTrackSelectorForLifecycle()
        val loadController = player.getLoadControllerForLifecycle()
        // Stop receiving playback errors before UIs detach their video surfaces. Media3 may report
        // a surface-detach timeout while the player is deliberately shutting down; surfacing that
        // expected teardown failure would show an erroneous playback error to the user.
        player.videoAdjustments.detach()
        exoPlayer?.removeListener(player)
        player.UIs().call(PlayerUi::destroyPlayer)
        audioController.releaseAudioSession()
        try {
            if (exoPlayer != null) {
                exoPlayer.stop()
                exoPlayer.release()
            }
        } finally {
            player.clearExoPlayerForLifecycle()
            if (trackSelector != null) {
                player.clearTrackSelectorForLifecycle(trackSelector)
            }
            if (loadController != null) {
                player.clearLoadControllerForLifecycle(loadController)
            }
        }
        if (player.isProgressLoopRunning) player.stopProgressLoop()
        if (!preserveQueue) player.playQueue?.dispose()
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
