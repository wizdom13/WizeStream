/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

import android.util.Log
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import java.io.IOException

/** Debug-only live load tracing. Never includes URIs, headers or exception messages. */
internal class LiveLoadDiagnostics(
    private val engine: ExoPlayer,
    private val isLiveManifest: () -> Boolean
) : AnalyticsListener {
    private var intervalStartMs = Long.MIN_VALUE
    private var intervalEvents = 0
    private var omittedEvents = 0

    override fun onLoadStarted(eventTime: EventTime, loadEventInfo: LoadEventInfo, mediaLoadData: MediaLoadData) {
        load("started", eventTime, loadEventInfo, mediaLoadData)
    }

    override fun onLoadCompleted(eventTime: EventTime, loadEventInfo: LoadEventInfo, mediaLoadData: MediaLoadData) {
        load("completed", eventTime, loadEventInfo, mediaLoadData)
    }

    override fun onLoadCanceled(eventTime: EventTime, loadEventInfo: LoadEventInfo, mediaLoadData: MediaLoadData) {
        load("canceled", eventTime, loadEventInfo, mediaLoadData)
    }

    override fun onLoadError(
        eventTime: EventTime,
        loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData,
        error: IOException,
        wasCanceled: Boolean
    ) {
        // Error events bypass the routine-event rate limit. Only numeric HTTP status and
        // exception class names are safe; an IOException message can contain a signed URL.
        val causes = generateSequence(error as Throwable) { it.cause }.take(8).toList()
        val status = causes.filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
            .firstOrNull()?.responseCode
        load(
            "error",
            eventTime,
            loadEventInfo,
            mediaLoadData,
            " status=${status ?: "none"} canceled=$wasCanceled " +
                "causes=${causes.joinToString("<-") { it.javaClass.simpleName }}",
            force = true
        )
    }

    override fun onPlaybackStateChanged(eventTime: EventTime, state: Int) {
        if (isLiveManifest()) Log.d(TAG, "Live state=$state ${snapshot()}")
    }

    override fun onIsLoadingChanged(eventTime: EventTime, isLoading: Boolean) {
        if (isLiveManifest()) Log.d(TAG, "Live loading=$isLoading ${snapshot()}")
    }

    private fun load(
        action: String,
        time: EventTime,
        info: LoadEventInfo,
        data: MediaLoadData,
        suffix: String = "",
        force: Boolean = false
    ) {
        if (!isLiveManifest()) return
        if (intervalStartMs == Long.MIN_VALUE || time.realtimeMs - intervalStartMs >= 1_000) {
            intervalStartMs = time.realtimeMs
            intervalEvents = 0
        }
        if (!force && intervalEvents >= 12) {
            omittedEvents++
            return
        }
        intervalEvents++
        Log.d(
            TAG,
            "Live load $action task=${info.loadTaskId} dataType=${data.dataType} " +
                "trackType=${data.trackType} mediaStartMs=${data.mediaStartTimeMs} " +
                "mediaEndMs=${data.mediaEndTimeMs} durationMs=${info.loadDurationMs} " +
                "bytes=${info.bytesLoaded} omitted=$omittedEvents ${snapshot()}$suffix"
        )
        omittedEvents = 0
    }

    private fun snapshot(): String = "state=${engine.playbackState} " +
        "playWhenReady=${engine.playWhenReady} loading=${engine.isLoading} " +
        "positionMs=${engine.currentPosition} bufferedPositionMs=${engine.bufferedPosition} " +
        "bufferedDurationMs=${engine.totalBufferedDuration}"

    private companion object {
        const val TAG = "LiveLoadDiagnostics"
    }
}
