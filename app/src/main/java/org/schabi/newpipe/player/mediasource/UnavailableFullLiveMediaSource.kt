/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.mediasource

import androidx.media3.common.MediaItem
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.source.BaseMediaSource
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.Allocator

/** Keep the failed item's metadata while refusing a source that would discard protected DVR. */
class UnavailableFullLiveMediaSource(private val item: MediaItem) : BaseMediaSource() {
    override fun getMediaItem(): MediaItem = item

    override fun prepareSourceInternal(mediaTransferListener: TransferListener?) = Unit

    override fun maybeThrowSourceInfoRefreshError() {
        throw FullLiveDvrUnavailableException("The refreshed Full live source has no DASH DVR manifest")
    }

    override fun createPeriod(id: MediaSource.MediaPeriodId, allocator: Allocator, startPositionUs: Long): MediaPeriod = error("An unavailable DVR source cannot create media periods")

    override fun releasePeriod(mediaPeriod: MediaPeriod) = Unit

    override fun releaseSourceInternal() = Unit
}
