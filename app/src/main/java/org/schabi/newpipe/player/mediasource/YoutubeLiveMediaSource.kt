/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.mediasource

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.dash.manifest.DashManifest
import androidx.media3.exoplayer.source.CompositeMediaSource
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.Allocator
import java.io.IOException

/**
 * Use YouTube's refreshable DASH DVR window when available. A finite/static or broken manifest
 * falls back to HLS before publishing a timeline, so playback never starts on a frozen DVR snapshot.
 * Once playback has started, manifest updates keep the chosen source and the user's seek position.
 */
class YoutubeLiveMediaSource(
    private val dashSource: MediaSource,
    private val hlsSource: MediaSource
) : CompositeMediaSource<Int>() {
    private var usingHls = false
    private var publishedTimeline = false
    private val selectedSource: MediaSource
        get() = if (usingHls) hlsSource else dashSource

    override fun getMediaItem(): MediaItem = selectedSource.mediaItem

    override fun prepareSourceInternal(mediaTransferListener: TransferListener?) {
        super.prepareSourceInternal(mediaTransferListener)
        usingHls = false
        publishedTimeline = false
        prepareChildSource(DASH, dashSource)
    }

    override fun onChildSourceInfoRefreshed(id: Int, mediaSource: MediaSource, timeline: Timeline) {
        if (usingHls && id == DASH) return
        if (!usingHls && !publishedTimeline) {
            if (timeline.isEmpty) return
            val manifest = timeline.getWindow(0, Timeline.Window()).manifest as? DashManifest
            if (manifest == null || !isRefreshable(manifest)) {
                useHls()
                return
            }
        }
        publishedTimeline = true
        refreshSourceInfo(timeline)
    }

    override fun maybeThrowSourceInfoRefreshError() {
        try {
            super.maybeThrowSourceInfoRefreshError()
        } catch (error: IOException) {
            if (usingHls || publishedTimeline) throw error
            useHls()
        }
    }

    private fun useHls() {
        usingHls = true
        releaseChildSource(DASH)
        prepareChildSource(HLS, hlsSource)
    }

    override fun createPeriod(id: MediaSource.MediaPeriodId, allocator: Allocator, startPositionUs: Long): MediaPeriod = selectedSource.createPeriod(id, allocator, startPositionUs)

    override fun releasePeriod(mediaPeriod: MediaPeriod) {
        selectedSource.releasePeriod(mediaPeriod)
    }

    companion object {
        private const val DASH = 0
        private const val HLS = 1

        internal fun isRefreshable(manifest: DashManifest): Boolean = manifest.dynamic && manifest.durationMs == C.TIME_UNSET &&
            manifest.minUpdatePeriodMs != C.TIME_UNSET
    }
}
