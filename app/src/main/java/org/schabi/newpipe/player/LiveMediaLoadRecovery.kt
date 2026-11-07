/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import java.io.IOException

/** Observes media-chunk failures before they become fatal playback errors. */
internal class LiveMediaLoadRecovery(
    private val engine: ExoPlayer,
    private val recovery: PlayerErrorController
) : AnalyticsListener {
    override fun onLoadError(
        eventTime: EventTime,
        loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData,
        error: IOException,
        wasCanceled: Boolean
    ) {
        if (wasCanceled || !isMediaSegment(mediaLoadData) ||
            PlayerHttpErrorRecovery.findInvalidResponseCode(error) != 403
        ) {
            return
        }
        recovery.onLiveMediaSegmentRejected(
            engine,
            mediaLoadData.trackType,
            mediaLoadData.mediaStartTimeMs
        )
    }

    override fun onLoadCompleted(
        eventTime: EventTime,
        loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData
    ) {
        if (isMediaSegment(mediaLoadData)) {
            recovery.onLiveMediaSegmentLoaded(
                engine,
                mediaLoadData.trackType,
                mediaLoadData.mediaStartTimeMs
            )
        }
    }

    private fun isMediaSegment(data: MediaLoadData): Boolean {
        return data.dataType == C.DATA_TYPE_MEDIA &&
            (data.trackType == C.TRACK_TYPE_AUDIO || data.trackType == C.TRACK_TYPE_VIDEO) &&
            data.mediaStartTimeMs != C.TIME_UNSET
    }
}
