/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-FileCopyrightText: 2026 PipePipe contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Adapted for WizeStream from PipePipe's live-quality selection implementation.
 */

package org.schabi.newpipe.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import java.util.Locale
import kotlin.math.roundToInt
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamType

/**
 * Owns manual quality selection for adaptive live manifests.
 *
 * Live HLS/DASH variants are exposed by Media3 only after the manifest is parsed, so they cannot
 * use the normal VOD resolver path. A selected option is therefore reapplied as a video-track
 * override whenever Media3 refreshes the manifest and replaces its [TrackGroup] instances.
 */
class LiveQualityController(private val player: Player) {

    data class Option(
        val height: Int,
        val width: Int,
        val frameRate: Int,
        val codec: String,
        val hdr: Boolean
    ) {
        val label: String
            get() = buildString {
                if (codec.isNotEmpty()) {
                    append(codec).append(' ')
                }
                append(height).append('p')
                if (frameRate > 30) {
                    append(frameRate)
                }
                if (hdr) {
                    append(" HDR")
                }
            }
    }

    var options: List<Option> = emptyList()
        private set

    private var selected: Option? = null
    private var appliedOverride: Pair<TrackGroup, Int>? = null
    private var itemReady = false
    private var streamKey: String? = null

    val isLivePlayback: Boolean
        get() {
            if (!itemReady) {
                return false
            }
            val info = player.currentStreamInfo.orElse(null) ?: return false
            val metadata = player.currentMetadata
            return info.streamType == StreamType.LIVE_STREAM &&
                (metadata == null || metadata.maybeQuality.isEmpty)
        }

    val currentLabel: String
        get() = selected?.label ?: player.context.getString(R.string.auto)

    fun hasSelectableOptions(): Boolean = isLivePlayback && options.size > 1

    fun resetForNewItem() {
        clearOverride()
        options = emptyList()
        selected = null
        appliedOverride = null
        itemReady = false
        streamKey = null
    }

    /**
     * Called after StreamInfo becomes available. Tracks may already have arrived, so rebuild from
     * the current Media3 state instead of waiting for another tracks callback.
     */
    fun onItemLoaded() {
        val info = player.currentStreamInfo.orElse(null)
        val newStreamKey = info?.let { "${it.serviceId}:${it.url}" }

        if (newStreamKey != streamKey) {
            clearOverride()
            selected = null
            appliedOverride = null
            streamKey = newStreamKey
        }

        itemReady = true
        if (!isLivePlayback || player.exoPlayerIsNull()) {
            options = emptyList()
            return
        }

        options = extractOptions(player.exoPlayer.currentTracks)
        appliedOverride = null
        applySelectedTrack()
    }

    fun onTracksChanged(tracks: Tracks) {
        if (!isLivePlayback) {
            options = emptyList()
            appliedOverride = null
            return
        }

        options = extractOptions(tracks)
        // Manifest refreshes recreate TrackGroups, invalidating an override bound to the old group.
        appliedOverride = null
        applySelectedTrack()
    }

    /**
     * Selects menu index 0 as Auto, followed by the current [options].
     *
     * @return the new button label, or null if a stale menu index was supplied.
     */
    fun select(index: Int): String? {
        if (!isLivePlayback) {
            return null
        }

        val newSelection = if (index <= 0) {
            null
        } else {
            options.getOrNull(index - 1) ?: return null
        }
        selected = newSelection
        applySelectedTrack()
        return currentLabel
    }

    private fun applySelectedTrack() {
        if (player.exoPlayerIsNull()) {
            return
        }

        val desired = selected
        if (desired == null) {
            if (appliedOverride != null) {
                appliedOverride = null
                clearOverride()
            }
            return
        }

        val match = findTrack(player.exoPlayer.currentTracks, desired) ?: return
        if (appliedOverride?.first === match.first && appliedOverride?.second == match.second) {
            return
        }

        val parameters = player.trackSelector.buildUponParameters()
            .setOverrideForType(TrackSelectionOverride(match.first, match.second))
        player.trackSelector.setParameters(parameters)
        appliedOverride = match
    }

    private fun clearOverride() {
        if (player.exoPlayerIsNull()) {
            return
        }
        val parameters = player.trackSelector.buildUponParameters()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        player.trackSelector.setParameters(parameters)
    }

    private fun extractOptions(tracks: Tracks): List<Option> {
        val seen = LinkedHashSet<Option>()
        for (group in tracks.groups) {
            if (group.type != C.TRACK_TYPE_VIDEO) {
                continue
            }
            for (index in 0 until group.length) {
                optionFromFormat(group.getTrackFormat(index))?.let(seen::add)
            }
        }

        return seen.sortedWith(
            compareByDescending<Option> { it.height }
                .thenByDescending { it.frameRate }
                .thenByDescending { it.hdr }
                .thenByDescending { codecPriority(it.codec) }
        )
    }

    /**
     * Multiple tracks may normalize to one display option. Prefer the highest-bitrate matching
     * representation so duplicate CDN pathways do not produce duplicate menu entries.
     */
    private fun findTrack(tracks: Tracks, option: Option): Pair<TrackGroup, Int>? {
        var best: Pair<TrackGroup, Int>? = null
        var bestBitrate = -1

        for (group in tracks.groups) {
            if (group.type != C.TRACK_TYPE_VIDEO) {
                continue
            }
            for (index in 0 until group.length) {
                val format = group.getTrackFormat(index)
                if (optionFromFormat(format) != option) {
                    continue
                }
                val bitrate = when {
                    format.averageBitrate > 0 -> format.averageBitrate
                    format.peakBitrate > 0 -> format.peakBitrate
                    else -> -1
                }
                if (best == null || bitrate > bestBitrate) {
                    best = group.mediaTrackGroup to index
                    bestBitrate = bitrate
                }
            }
        }

        return best
    }

    companion object {
        @JvmStatic
        fun optionFromFormat(format: Format): Option? {
            if (format.height <= 0) {
                return null
            }
            return Option(
                height = format.height,
                width = format.width,
                frameRate = if (format.frameRate.isNaN() || format.frameRate <= 0) {
                    0
                } else {
                    format.frameRate.roundToInt()
                },
                codec = codecName(format.codecs),
                hdr = isHdr(format)
            )
        }

        @JvmStatic
        fun codecName(codecs: String?): String {
            if (codecs == null) {
                return ""
            }
            return when {
                codecs.contains("av01", ignoreCase = true) -> "AV1"
                codecs.contains("vp9", ignoreCase = true) -> "VP9"
                codecs.contains("vp8", ignoreCase = true) -> "VP8"
                containsCodecFamily(codecs, "avc", "h264") -> "H264"
                containsCodecFamily(codecs, "hevc", "h265", "hev1", "hvc1") -> "HEVC"
                else -> codecs.substringBefore('.').uppercase(Locale.getDefault())
            }
        }

        private fun containsCodecFamily(codecs: String, vararg names: String): Boolean {
            return names.any { codecs.contains(it, ignoreCase = true) }
        }

        private fun isHdr(format: Format): Boolean {
            val transfer = format.colorInfo?.colorTransfer
            if (transfer == C.COLOR_TRANSFER_HLG || transfer == C.COLOR_TRANSFER_ST2084) {
                return true
            }
            val codecs = format.codecs ?: return false
            return codecs.startsWith("vp9.2", ignoreCase = true) ||
                Regex("av01\\.0\\.(09|1[0-3])M", RegexOption.IGNORE_CASE).containsMatchIn(codecs)
        }

        private fun codecPriority(codec: String): Int = when (codec) {
            "AV1" -> 4
            "VP9" -> 3
            "HEVC" -> 2
            "H264" -> 1
            else -> 0
        }
    }
}
