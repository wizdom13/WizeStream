/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.helper

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Timeline
import androidx.media3.common.TrackGroup
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import androidx.media3.exoplayer.source.chunk.MediaChunkIterator
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.BandwidthMeter
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class PlaybackQualitySelectionTest {
    private val low = Format.Builder().setId("480p").setSampleMimeType("video/avc").setAverageBitrate(500_000).setWidth(854).setHeight(480).build()
    private val high = Format.Builder().setId("1080p").setSampleMimeType("video/avc").setAverageBitrate(2_000_000).setWidth(1920).setHeight(1080).build()
    private val bandwidth = mock(BandwidthMeter::class.java).apply {
        `when`(bitrateEstimate).thenReturn(1_000_000L)
        `when`(timeToFirstByteEstimateUs).thenReturn(C.TIME_UNSET)
    }

    private fun selection(vararg tracks: Int): ExoTrackSelection = PlaybackQualitySelection.create().createTrackSelections(
        arrayOf(ExoTrackSelection.Definition(TrackGroup(low, high), *tracks)),
        bandwidth,
        MediaPeriodId("live"),
        Timeline.EMPTY
    )[0]!!

    private fun update(selection: ExoTrackSelection, bufferUs: Long, availableUs: Long = C.TIME_UNSET) {
        selection.updateSelectedTrack(0, bufferUs, availableUs, emptyList(), Array(selection.length()) { MediaChunkIterator.EMPTY })
    }

    @Test
    fun bandwidthSpikeDoesNotUpgradeWithOnlyOneSecondBuffered() {
        val selection = selection(0, 1)
        update(selection, 0)
        assertEquals(low, selection.selectedFormat)
        `when`(bandwidth.bitrateEstimate).thenReturn(10_000_000L)
        update(selection, 1_000_000)
        assertEquals(low, selection.selectedFormat)
        update(selection, 10_000_000)
        assertEquals(high, selection.selectedFormat)
    }

    @Test
    fun shortLiveWindowStillAllowsUpgradeWhenMostAvailableContentIsBuffered() {
        val selection = selection(0, 1)
        update(selection, 0)
        `when`(bandwidth.bitrateEstimate).thenReturn(10_000_000L)
        update(selection, 4_000_000, 5_000_000)
        assertEquals(high, selection.selectedFormat)
    }

    @Test
    fun fixedResolutionDoesNotChangeWithBandwidth() {
        val selection = selection(0)
        `when`(bandwidth.bitrateEstimate).thenReturn(10_000_000L)
        update(selection, 30_000_000)
        assertEquals(low, selection.selectedFormat)
    }
}
