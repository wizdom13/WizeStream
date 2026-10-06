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
import androidx.media3.common.Player as Media3Player
import androidx.media3.exoplayer.ExoPlaybackException
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.player.datasource.InvidiousMediaResponse
import org.schabi.newpipe.player.helper.PlayerDataSource
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.player.resolver.PlaybackResolver
import org.schabi.newpipe.player.resolver.VideoPlaybackResolver
import org.schabi.newpipe.util.InfoCache

/** Owns playback-error classification, recovery scheduling, and user-facing error reporting. */
internal class PlayerErrorController(
    private val player: Player,
    private val eventDispatcher: PlayerEventDispatcher,
    private val videoResolver: VideoPlaybackResolver
) {
    private val recoveryGuard = PlayerHttpErrorRecovery.RecoveryGuard()
    private val decoderRecoveryGuard = PlayerHttpErrorRecovery.OneShotRecoveryGuard()
    private val audioTrackRecoveryGuard = PlayerHttpErrorRecovery.OneShotRecoveryGuard()
    private val liveStallRecoveryGuard = PlayerHttpErrorRecovery.OneShotRecoveryGuard()
    private val recoveryHandler = Handler(Looper.getMainLooper())
    private var pendingMediaUrlRecovery: Runnable? = null
    private var pendingLiveStallRecovery: Runnable? = null
    private var lastPlayingLiveKey: String? = null

    fun onPlayerError(error: PlaybackException) {
        cancelPendingLiveStallRecovery()
        Log.e(Player.TAG, "ExoPlayer - onPlayerError() called with:", error)

        if (player.videoAdjustments.recover(error)) return

        player.saveStreamProgressState()
        if (tryRecoverFromAudioTrackInitFailure(error)) {
            return
        }

        val downloaded = org.schabi.newpipe.player.mediaitem.MediaItemTag.from(player.exoPlayer.currentMediaItem)
            .flatMap { it.maybeStreamInfo }.orElse(null) as? org.schabi.newpipe.download.DownloadedStreamInfo
        if (downloaded != null && org.schabi.newpipe.download.DownloadedCopyRepository.reject(downloaded)) {
            player.setRecovery()
            player.reloadPlayQueueManager()
            return
        }
        if (tryRecoverFromYouTubeAv1DecoderFailure(error)) {
            return
        }
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

            ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> {
                if (generateSequence(error as Throwable) { it.cause }.any { it is InvidiousMediaResponse.InvalidResponseException }) {
                    isCatchableException = true
                    if (!player.exoPlayerIsNull()) {
                        player.exoPlayer.pause()
                    }
                    player.changeState(Player.STATE_PAUSED)
                    createErrorNotification(error, toastMessageRes = R.string.invidious_media_error)
                } else if (!player.exoPlayerIsNull()) {
                    player.playQueue?.error()
                }
            }

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

    fun onPlayWhenReadyChanged(playWhenReady: Boolean) {
        if (!playWhenReady) {
            cancelPendingLiveStallRecovery()
        }
    }

    fun onPlaybackStateChanged(playbackState: Int) {
        val item = player.playQueue?.item
        val key = item?.let { "${it.serviceId}:${it.url}" }
        when (playbackState) {
            Media3Player.STATE_READY -> {
                cancelPendingLiveStallRecovery()
                lastPlayingLiveKey = if (item != null && player.playWhenReady &&
                    canFallBackToLiveHls(item)
                ) {
                    key
                } else {
                    null
                }
            }

            Media3Player.STATE_BUFFERING -> {
                if (key == null || key != lastPlayingLiveKey || !player.playWhenReady ||
                    item == null || !canFallBackToLiveHls(item) ||
                    pendingLiveStallRecovery != null
                ) {
                    return
                }

                val recovery = Runnable {
                    pendingLiveStallRecovery = null
                    val queue = player.playQueue ?: return@Runnable
                    val currentItem = queue.item ?: return@Runnable
                    val currentKey = "${currentItem.serviceId}:${currentItem.url}"
                    val info = player.currentStreamInfo.orElse(null) ?: return@Runnable
                    if (currentKey != key || !player.playWhenReady || player.exoPlayerIsNull() ||
                        player.exoPlayer.playbackState != Media3Player.STATE_BUFFERING ||
                        !canFallBackToLiveHls(currentItem) ||
                        !liveStallRecoveryGuard.acquire(key)
                    ) {
                        return@Runnable
                    }

                    Log.w(Player.TAG, "Live DASH stalled while buffering; retrying with HLS")
                    videoResolver.preferHlsForLiveStream(info.url)
                    lastPlayingLiveKey = null
                    queue.unsetRecovery(queue.index)
                    player.reloadPlayQueueManager()
                }
                pendingLiveStallRecovery = recovery
                recoveryHandler.postDelayed(recovery, LIVE_STALL_TIMEOUT_MILLIS)
            }

            else -> {
                cancelPendingLiveStallRecovery()
                lastPlayingLiveKey = null
            }
        }
    }

    fun resetRecovery() {
        cancelPendingMediaUrlRecovery()
        cancelPendingLiveStallRecovery()
        lastPlayingLiveKey = null
        recoveryGuard.reset()
        decoderRecoveryGuard.reset()
        audioTrackRecoveryGuard.reset()
        liveStallRecoveryGuard.reset()
        videoResolver.clearRejectedVideoCodecFamily()
        videoResolver.clearLiveHlsFallback()
    }

    private fun canFallBackToLiveHls(item: PlayQueueItem): Boolean {
        val info = player.currentStreamInfo.orElse(null) ?: return false
        return PlayerHttpErrorRecovery.isYouTubeService(item.serviceId) &&
            PlaybackResolver.isManifestOnlyYoutubeLive(info) &&
            info.dashMpdUrl.isNotEmpty() &&
            !videoResolver.isHlsPreferredForLiveStream(info.url) &&
            player.videoPlayerSelected() && !player.isAudioOnly
    }

    private fun cancelPendingLiveStallRecovery() {
        pendingLiveStallRecovery?.let(recoveryHandler::removeCallbacks)
        pendingLiveStallRecovery = null
    }

    private fun tryRecoverFromAudioTrackInitFailure(error: PlaybackException): Boolean {
        if (!PlayerHttpErrorRecovery.isRecoverableAudioTrackInitFailure(error.errorCode)) {
            return false
        }

        val item = player.playQueue?.item ?: return false
        val recoveryKey = "${item.serviceId}:${item.url}"
        if (!audioTrackRecoveryGuard.acquire(recoveryKey)) {
            return false
        }

        Log.w(
            Player.TAG,
            "Recreating playback engine after AudioTrack initialization failure"
        )
        player.setRecovery()
        player.onBuffering()
        cancelPendingMediaUrlRecovery()
        player.restartForAudioTrackRecovery()
        return true
    }
    private fun tryRecoverFromYouTubeAv1DecoderFailure(error: PlaybackException): Boolean {
        val item = player.playQueue?.item ?: return false
        if (!PlayerHttpErrorRecovery.isYouTubeService(item.serviceId)) {
            return false
        }

        val quality = player.currentMetadata?.maybeQuality?.orElse(null) ?: return false
        val stream = player.selectedVideoStream.orElse(null)
            ?: (error as? ExoPlaybackException)?.rendererFormat?.id?.let { formatId ->
                quality.sortedVideoStreams.firstOrNull { candidate ->
                    candidate.itag.toString() == formatId
                }
            }
            ?: return false
        if (!PlayerHttpErrorRecovery.isRecoverableAv1DecoderInitFailure(
                error.errorCode,
                stream
            )
        ) {
            return false
        }

        if (!VideoPlaybackResolver.hasAlternativeCodecFamily(
                quality.sortedVideoStreams,
                stream.codec
            )
        ) {
            return false
        }

        val recoveryKey = "${item.serviceId}:${item.url}"
        if (!decoderRecoveryGuard.acquire(recoveryKey)) {
            return false
        }

        Log.w(
            Player.TAG,
            "Retrying YouTube playback after AV1 decoder initialization failure " +
                "with a different codec family"
        )
        player.setRecovery()
        player.onBuffering()
        cancelPendingMediaUrlRecovery()
        videoResolver.rejectVideoCodecFamilyForStream(item.url, stream.codec)
        PlayerDataSource.invalidateYoutubeManifestCaches()
        player.reloadPlayQueueManager()
        return true
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
            if (tryRecoverFromLiveDashHttpFailure(error, item)) {
                return true
            }
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

    internal fun tryRecoverFromLiveDashHttpFailure(
        error: PlaybackException,
        item: PlayQueueItem
    ): Boolean {
        if (PlayerHttpErrorRecovery.findInvalidResponseCode(error) != 403 ||
            !canFallBackToLiveHls(item) ||
            !liveStallRecoveryGuard.acquire("${item.serviceId}:${item.url}")
        ) {
            return false
        }

        val info = player.currentStreamInfo.orElse(null) ?: return false
        val queue = player.playQueue ?: return false
        Log.w(Player.TAG, "Live DASH HTTP 403 recovery exhausted; retrying with HLS")
        videoResolver.preferHlsForLiveStream(info.url)
        lastPlayingLiveKey = null
        queue.unsetRecovery(queue.index)
        player.onBuffering()
        player.reloadPlayQueueManager()
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
        recoveryDiagnostic: String? = null,
        toastMessageRes: Int = R.string.error_report_notification_toast
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
        ErrorUtil.createNotification(player.context, errorInfo, toastMessageRes)
    }

    private companion object {
        const val LIVE_STALL_TIMEOUT_MILLIS = 10_000L
    }
}
