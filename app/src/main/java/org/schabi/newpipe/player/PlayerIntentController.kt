/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.IntentCompat
import androidx.media3.common.Player as Media3Player
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.functions.Action
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.player.playqueue.LocalMediaPlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.player.resolver.VideoPlaybackResolver
import org.schabi.newpipe.player.ui.MainPlayerUi
import org.schabi.newpipe.player.ui.PlayerUi
import org.schabi.newpipe.util.DependentPreferenceHelper
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.SerializedCache

/** Owns playback intent interpretation and queue/session selection. */
internal class PlayerIntentController(
    private val player: Player,
    private val context: Context,
    private val historyController: PlayerHistoryController,
    private val presentationController: PlayerPresentationController,
    private val popupPlayerReturnState: PopupPlayerReturnState,
    private val videoResolver: VideoPlaybackResolver,
    private val streamItemDisposable: CompositeDisposable
) {
    private var openPlayQueueAfterIntent = false

    fun handle(intent: Intent) {
        openPlayQueueAfterIntent = false
        val intentType = IntentCompat.getSerializableExtra(
            intent,
            Player.PLAYER_INTENT_TYPE,
            PlayerIntentType::class.java
        ) ?: return

        if (intentType != PlayerIntentType.TimestampChange) {
            historyController.updateLearningSession()
            val requestedType = checkNotNull(
                IntentCompat.getSerializableExtra(
                    intent,
                    Player.PLAYER_TYPE,
                    PlayerType::class.java
                )
            )
            if (player.playerType == PlayerType.MAIN &&
                requestedType == PlayerType.POPUP &&
                !popupPlayerReturnState.isRemembered
            ) {
                popupPlayerReturnState.remember(
                    player.UIs().get(MainPlayerUi::class.java)
                        .map(MainPlayerUi::isFullscreen)
                        .orElse(false)
                )
            }
            player.setPlayerTypeForIntent(requestedType)
            historyController.updateLearningSession()
        }
        player.initUIsForCurrentPlayerType()

        val requestedMode = IntentCompat.getSerializableExtra(
            intent,
            Player.PLAYBACK_PRESENTATION_MODE,
            PlaybackPresentationMode::class.java
        )
        presentationController.updateFromIntent(
            player.audioPlayerSelected(),
            requestedMode,
            intentType != PlayerIntentType.TimestampChange
        )
        if (intent.hasExtra(Player.PLAYBACK_QUALITY)) {
            videoResolver.setPlaybackQuality(intent.getStringExtra(Player.PLAYBACK_QUALITY))
        }
        val playWhenReady = intent.getBooleanExtra(Player.PLAY_WHEN_READY, true)

        when (intentType) {
            PlayerIntentType.Enqueue -> {
                player.playQueue?.let { queue ->
                    val newQueue = playQueueFromCache(intent) ?: return
                    queue.append(newQueue.streams)
                    return
                }
            }

            PlayerIntentType.EnqueueNext -> {
                player.playQueue?.let { queue ->
                    val newQueue = playQueueFromCache(intent) ?: return
                    queue.enqueueNext(newQueue.streams[0], false)
                    return
                }
            }

            PlayerIntentType.TimestampChange -> {
                handleTimestampChange(intent, playWhenReady)
                return
            }

            PlayerIntentType.AllOthers -> Unit
        }

        val newQueue = playQueueFromCache(intent) ?: return
        openPlayQueueAfterIntent = player.playerType == PlayerType.MAIN &&
            (newQueue as? LocalMediaPlayQueue)?.consumeOpenQueueOnStart() == true
        val currentQueue = player.playQueue
        val samePlayQueue =
            currentQueue != null && currentQueue.equalStreamsAndIndex(newQueue)

        if (!player.exoPlayerIsNull() &&
            newQueue.size() == 1 &&
            newQueue.item != null &&
            currentQueue != null &&
            currentQueue.size() == 1 &&
            currentQueue.item != null &&
            newQueue.item!!.isSameItem(currentQueue.item) &&
            newQueue.item!!.recoveryPosition != PlayQueueItem.RECOVERY_UNSET
        ) {
            prepareIfIdle()
            player.exoPlayer.seekTo(currentQueue.index, newQueue.item!!.recoveryPosition)
            player.exoPlayer.playWhenReady = playWhenReady
        } else if (!player.exoPlayerIsNull() &&
            samePlayQueue &&
            currentQueue != null &&
            !currentQueue.isDisposed
        ) {
            prepareIfIdle()
            player.exoPlayer.playWhenReady = playWhenReady
        } else if (intent.getBooleanExtra(Player.RESUME_PLAYBACK, false) &&
            DependentPreferenceHelper.getResumePlaybackEnabled(context) &&
            (currentQueue == null || !currentQueue.equalStreamsAndIndex(newQueue)) &&
            !newQueue.isEmpty &&
            newQueue.item != null &&
            newQueue.item!!.recoveryPosition == PlayQueueItem.RECOVERY_UNSET
        ) {
            val item = newQueue.item!!
            historyController.restoreStreamState(
                item,
                Consumer { state ->
                    if (!state.isFinished(item.duration)) {
                        newQueue.setRecovery(newQueue.index, state.progressMillis)
                    }
                    player.initPlayback(newQueue, playWhenReady)
                },
                Consumer { error ->
                    if (Player.DEBUG) {
                        Log.w(Player.TAG, "Failed to start playback", error)
                    }
                    player.initPlayback(newQueue, playWhenReady)
                },
                Action { player.initPlayback(newQueue, playWhenReady) }
            )
        } else {
            player.initPlayback(if (samePlayQueue) currentQueue!! else newQueue, playWhenReady)
        }
    }

    fun handlePost(oldPlayerType: PlayerType) {
        if (oldPlayerType != player.playerType && player.playQueue != null) {
            player.reloadPlayQueueManager()
        }
        player.UIs().call(PlayerUi::setupAfterIntent)
        NavigationHelper.sendPlayerStartedEvent(context)
        if (openPlayQueueAfterIntent) {
            openPlayQueueAfterIntent = false
            context.startActivity(
                Intent(context, PlayQueueActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun handleTimestampChange(intent: Intent, playWhenReady: Boolean) {
        val data = requireNotNull(
            IntentCompat.getParcelableExtra(
                intent,
                Player.PLAYER_INTENT_DATA,
                TimestampChangeData::class.java
            )
        )
        val single = ExtractorHelper.getStreamInfo(data.serviceId, data.url, false)
        streamItemDisposable.add(
            single.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { info -> applyTimestamp(info, data, playWhenReady) },
                    { error ->
                        ErrorUtil.createNotification(
                            context,
                            ErrorInfo(
                                error,
                                UserAction.PLAY_ON_POPUP,
                                data.url,
                                null,
                                data.url
                            )
                        )
                    }
                )
        )
    }

    private fun applyTimestamp(
        info: StreamInfo,
        data: TimestampChangeData,
        playWhenReady: Boolean
    ) {
        val oldQueue = player.playQueue
        info.startPosition = data.seconds.toLong()
        val item = PlayQueueItem(info)
        if (oldQueue != null && item.isSameItem(oldQueue.item)) {
            prepareIfIdle()
            player.exoPlayer.seekTo(oldQueue.index, data.seconds * 1000L)
            player.exoPlayer.playWhenReady = playWhenReady
            return
        }
        val newQueue = if (oldQueue == null) {
            SinglePlayQueue(item)
        } else {
            oldQueue.enqueueNext(item, true)
            oldQueue.offsetIndex(1)
            oldQueue
        }
        player.initPlayback(newQueue, playWhenReady)
    }

    private fun prepareIfIdle() {
        if (player.exoPlayer.playbackState == Media3Player.STATE_IDLE) {
            player.exoPlayer.prepare()
        }
    }

    private fun playQueueFromCache(intent: Intent): PlayQueue? {
        val key = intent.getStringExtra(Player.PLAY_QUEUE_KEY) ?: return null
        return SerializedCache.getInstance().take(key, PlayQueue::class.java)
    }
}
