/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player as Media3Player
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.mediasource.FullLiveDvrUnavailableException
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.player.resolver.PlaybackResolver
import org.schabi.newpipe.util.ExtractorHelper

/** Recheck the broadcast instead of repeatedly preparing an already failed live source. */
internal class LivePreparationRecovery(
    private val player: Player,
    private val onArchive: () -> Unit,
    private val onStopped: (PlaybackException, StopReason) -> Unit,
    private val load: (PlayQueueItem) -> Single<StreamInfo> = {
        ExtractorHelper.getStreamInfo(it.serviceId, it.url, true)
    },
    private val worker: Scheduler = Schedulers.io()
) {
    enum class StopReason { EXHAUSTED, ENDED, UNAVAILABLE }

    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null
    private var request: Disposable? = null
    private var generation = 0
    private var key: Pair<Int, String>? = null
    private var attempts = 0
    private var stopped = false
    private var readySince: Long? = null

    fun recover(error: PlaybackException): Boolean {
        val item = player.playQueue?.item ?: return false
        val info = player.currentStreamInfo.orElse(null)
        // A newly available archive may also fail to prepare. Keep that transition in
        // the same budget until playback is stable instead of entering the VOD reload path.
        val continuingRecovery = key == (item.serviceId to item.url) && attempts > 0 && error.errorCode in RETRYABLE_CODES
        if (!eligible(item, info, error) && !continuingRecovery) return false
        if (key != (item.serviceId to item.url)) reset()
        key = item.serviceId to item.url
        if (stopped) return true
        resetAfterStablePlayback()
        readySince = null
        if (!player.playWhenReady) {
            cancel()
            return true
        }
        if (pending == null && request?.isDisposed != false) schedule(item, error)
        return true
    }

    private fun schedule(item: PlayQueueItem, error: PlaybackException) {
        if (attempts >= DELAYS.size) {
            finish(error, StopReason.EXHAUSTED)
            return
        }
        val delay = DELAYS[attempts++]
        val token = generation
        val retry = Runnable {
            pending = null
            if (!active(item, token)) return@Runnable
            Log.i(Player.TAG, "Rechecking live source: attempt=$attempts/${DELAYS.size}, error=${error.errorCodeName}")
            request = Single.defer { load(item) }
                .subscribeOn(worker)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { fresh ->
                        request = null
                        if (!active(item, token)) return@subscribe
                        val archive = fresh.streamType in POST_LIVE_TYPES ||
                            (fresh.streamType in ARCHIVE_TYPES && !PlaybackResolver.isManifestOnlyYoutubeLive(fresh))
                        if (fresh.serviceId != item.serviceId || fresh.url != item.url) {
                            finish(error, StopReason.UNAVAILABLE)
                        } else if (archive && !hasArchiveMedia(fresh)) {
                            finish(error, StopReason.ENDED)
                        } else if (fresh.streamType == StreamType.NONE) {
                            finish(error, StopReason.UNAVAILABLE)
                        } else {
                            if (archive) onArchive()
                            player.setRecovery()
                            player.reloadPlayQueueManager()
                        }
                    },
                    { failure ->
                        request = null
                        if (!active(item, token)) return@subscribe
                        // Only extraction evidence establishes unavailability. IDLE and unknown
                        // manifest timestamps also occur during ordinary errors and user stops.
                        if (generateSequence(failure) { it.cause }.any { it is ContentNotAvailableException }) {
                            finish(error, StopReason.UNAVAILABLE)
                        } else {
                            schedule(item, error)
                        }
                    }
                )
        }
        pending = retry
        handler.postDelayed(retry, delay)
    }

    private fun active(item: PlayQueueItem, token: Int): Boolean {
        val current = player.playQueue?.item ?: return false
        return generation == token && current === item && !player.exoPlayerIsNull() && player.playWhenReady
    }

    private fun finish(error: PlaybackException, reason: StopReason) {
        stopped = true
        cancel()
        onStopped(error, reason)
    }

    fun onPlaybackStateChanged(state: Int) {
        if (state == Media3Player.STATE_READY && player.playWhenReady) {
            if (readySince == null) readySince = SystemClock.elapsedRealtime()
        } else {
            resetAfterStablePlayback()
            readySince = null
        }
    }

    fun onPlayWhenReadyChanged(playWhenReady: Boolean) {
        if (!playWhenReady) {
            cancel()
            readySince = null
        } else if (stopped) {
            // A user retry may start a new budget; automatic preparation keeps its old budget.
            attempts = 0
            stopped = false
        }
    }

    private fun resetAfterStablePlayback() {
        if (readySince?.let { SystemClock.elapsedRealtime() - it >= 30_000 } == true) attempts = 0
    }

    fun cancel() {
        generation++
        pending?.let(handler::removeCallbacks)
        pending = null
        request?.dispose()
        request = null
    }

    fun reset() {
        cancel()
        key = null
        attempts = 0
        stopped = false
        readySince = null
    }

    companion object {
        private val DELAYS = longArrayOf(1_000, 3_000, 8_000)
        private val POST_LIVE_TYPES = setOf(StreamType.POST_LIVE_STREAM, StreamType.POST_LIVE_AUDIO_STREAM)
        private val ARCHIVE_TYPES = setOf(StreamType.VIDEO_STREAM, StreamType.AUDIO_STREAM, StreamType.POST_LIVE_STREAM, StreamType.POST_LIVE_AUDIO_STREAM)
        private val RETRYABLE_CODES = setOf(
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_TIMEOUT,
            PlaybackException.ERROR_CODE_UNSPECIFIED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED
        )

        private fun hasArchiveMedia(info: StreamInfo): Boolean = info.audioStreams.isNotEmpty() || info.videoStreams.isNotEmpty() || info.videoOnlyStreams.isNotEmpty() ||
            (info.streamType in POST_LIVE_TYPES && (info.hlsUrl.isNotEmpty() || info.dashMpdUrl.isNotEmpty()))

        internal fun eligible(item: PlayQueueItem, info: StreamInfo?, error: PlaybackException): Boolean {
            if (!PlayerHttpErrorRecovery.isYouTubeService(item.serviceId) || item.isDirectMedia) return false
            val protectedFailure = generateSequence(error as Throwable) { it.cause }.any { it is FullLiveDvrUnavailableException }
            if (!protectedFailure && error.errorCode !in RETRYABLE_CODES) return false
            if (protectedFailure) return true
            val matchingInfo = info?.takeIf { it.serviceId == item.serviceId && it.url == item.url }
            return if (matchingInfo != null) {
                matchingInfo.streamType == StreamType.LIVE_STREAM || matchingInfo.streamType == StreamType.AUDIO_LIVE_STREAM ||
                    (matchingInfo.streamType !in setOf(StreamType.POST_LIVE_STREAM, StreamType.POST_LIVE_AUDIO_STREAM) && PlaybackResolver.isManifestOnlyYoutubeLive(matchingInfo))
            } else {
                item.streamType == StreamType.LIVE_STREAM || item.streamType == StreamType.AUDIO_LIVE_STREAM
            }
        }
    }
}
