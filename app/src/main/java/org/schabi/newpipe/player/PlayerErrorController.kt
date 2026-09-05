/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_NO_PERMISSION
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED
import androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
import androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
import androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED
import androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED
import androidx.media3.common.PlaybackException.ERROR_CODE_TIMEOUT
import androidx.media3.common.PlaybackException.ERROR_CODE_UNSPECIFIED
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.player.helper.PlayerDataSource
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.player.resolver.VideoPlaybackResolver
import org.schabi.newpipe.util.InfoCache

/** Owns playback-error classification, recovery scheduling, and user-facing error reporting. */
internal class PlayerErrorController(
    private val player: Player,
    private val eventDispatcher: PlayerEventDispatcher,
    private val videoResolver: VideoPlaybackResolver
) {
    private val recoveryGuard = PlayerHttpErrorRecovery.RecoveryGuard()
    private val recoveryHandler = Handler(Looper.getMainLooper())
    private var pendingMediaUrlRecovery: Runnable? = null

    fun onPlayerError(error: PlaybackException) {
        Log.e(Player.TAG, "ExoPlayer - onPlayerError() called with:", error)

        player.saveStreamProgressState()
        if (tryRecoverFromYouTubeMediaUrlFailure(error)) {
            return
        }

        var isCatchableException = false
        when (error.errorCode) {
            ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                isCatchableException = true
                player.exoPlayer.seekToDefaultPosition()
                player.exoPlayer.prepare()
                player.onBuffering()
            }

            ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
            ERROR_CODE_IO_BAD_HTTP_STATUS,
            ERROR_CODE_IO_FILE_NOT_FOUND,
            ERROR_CODE_IO_NO_PERMISSION,
            ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
            ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
            ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> {
                if (!player.exoPlayerIsNull()) {
                    player.playQueue?.error()
                }
            }

            ERROR_CODE_TIMEOUT,
            ERROR_CODE_IO_UNSPECIFIED,
            ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            ERROR_CODE_UNSPECIFIED -> {
                player.setRecovery()
                player.reloadPlayQueueManager()
            }

            else -> player.onPlaybackShutdown()
        }

        if (!isCatchableException) {
            createErrorNotification(error)
        }
        eventDispatcher.notifyPlayerError(error, isCatchableException)
    }

    fun resetRecovery() {
        cancelPendingMediaUrlRecovery()
        recoveryGuard.reset()
    }

    private fun tryRecoverFromYouTubeMediaUrlFailure(error: PlaybackException): Boolean {
        val item = player.playQueue?.item ?: return false
        if (!PlayerHttpErrorRecovery.isRecoverableYouTubeMediaUrlFailure(error, item)) {
            return false
        }

        val recoveryKey = "${item.serviceId}:${item.url}"
        val attempt = recoveryGuard.acquireAttempt(recoveryKey)
        if (attempt == null) {
            Log.w(
                Player.TAG,
                "YouTube media URL recovery exhausted after " +
                    "${PlayerHttpErrorRecovery.RecoveryGuard.MAX_ATTEMPTS} attempts"
            )
            cancelPendingMediaUrlRecovery()
            invalidateYouTubeMediaCaches(item)
            recoveryGuard.reset()
            if (!player.exoPlayerIsNull()) {
                player.exoPlayer.pause()
            }
            player.changeState(Player.STATE_PAUSED)
            createErrorNotification(
                error,
                "recovery=exhausted, attempts=" +
                    "${PlayerHttpErrorRecovery.RecoveryGuard.MAX_ATTEMPTS}/" +
                    PlayerHttpErrorRecovery.RecoveryGuard.MAX_ATTEMPTS
            )
            eventDispatcher.notifyPlayerError(error, true)
            return true
        }

        player.setRecovery()
        player.onBuffering()
        cancelPendingMediaUrlRecovery()

        val recoveryItemServiceId = item.serviceId
        val recoveryItemUrl = item.url
        val recovery = Runnable {
            pendingMediaUrlRecovery = null
            val currentQueueItem = player.playQueue?.item ?: return@Runnable
            if (currentQueueItem.serviceId != recoveryItemServiceId ||
                currentQueueItem.url != recoveryItemUrl
            ) {
                return@Runnable
            }

            val responseCode = PlayerHttpErrorRecovery.findInvalidResponseCode(error)
            Log.w(
                Player.TAG,
                "Refreshing YouTube StreamInfo after recoverable media URL failure" +
                    " (status=${responseCode ?: "network"}, attempt=${attempt.number}/" +
                    "${PlayerHttpErrorRecovery.RecoveryGuard.MAX_ATTEMPTS})"
            )
            player.selectedVideoStream
                .filter { stream ->
                    PlayerHttpErrorRecovery.shouldAvoidAndroidVrAv1HfrStream(error, stream)
                }
                .ifPresent { stream ->
                    videoResolver.rejectVideoStreamOnce(recoveryItemUrl, stream.itag)
                }
            invalidateYouTubeMediaCaches(item)
            player.reloadPlayQueueManager()
        }
        pendingMediaUrlRecovery = recovery
        recoveryHandler.postDelayed(recovery, attempt.delayMillis)
        return true
    }

    private fun cancelPendingMediaUrlRecovery() {
        pendingMediaUrlRecovery?.let(recoveryHandler::removeCallbacks)
        pendingMediaUrlRecovery = null
    }

    private fun invalidateYouTubeMediaCaches(item: PlayQueueItem) {
        PlayerDataSource.invalidateYoutubeManifestCaches()
        InfoCache.getInstance().removeInfo(item.serviceId, item.url, InfoCache.Type.STREAM)
    }

    private fun createErrorNotification(
        error: PlaybackException,
        recoveryDiagnostic: String? = null
    ) {
        val diagnosticSuffix = buildString {
            PlayerHttpErrorRecovery.buildSafeErrorContext(error)?.let { safeErrorContext ->
                append(" [$safeErrorContext]")
            }
            recoveryDiagnostic?.let { diagnostic ->
                append(" [$diagnostic]")
            }
        }

        val metadata = player.currentMetadata
        val errorInfo = if (metadata == null) {
            ErrorInfo(
                error,
                UserAction.PLAY_STREAM,
                "Player error[type=${error.errorCodeName}] occurred, " +
                    "currentMetadata is null$diagnosticSuffix"
            )
        } else {
            ErrorInfo(
                error,
                UserAction.PLAY_STREAM,
                "Player error[type=${error.errorCodeName}] occurred while playing " +
                    "${metadata.streamUrl}$diagnosticSuffix",
                metadata.serviceId,
                metadata.streamUrl
            )
        }
        ErrorUtil.createNotification(player.context, errorInfo)
    }
}
