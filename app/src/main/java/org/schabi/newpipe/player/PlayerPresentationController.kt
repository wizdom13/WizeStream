/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

import androidx.media3.common.C
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.player.resolver.VideoPlaybackResolver
import org.schabi.newpipe.player.resolver.VideoPlaybackResolver.SourceType
import org.schabi.newpipe.player.visualizer.VisualizerAudioProcessor
import org.schabi.newpipe.util.StreamTypeUtil

/** Owns audio-only state, presentation mode, and renderer selection. */
internal class PlayerPresentationController(
    private val player: Player,
    private val videoResolver: VideoPlaybackResolver,
    private val trackSelector: DefaultTrackSelector,
    private val visualizerAudioProcessor: VisualizerAudioProcessor
) {
    var isAudioOnly: Boolean = false
        private set

    var mode: PlaybackPresentationMode = PlaybackPresentationMode.VIDEO
        private set

    fun updateFromIntent(
        audioPlayerSelected: Boolean,
        requestedMode: PlaybackPresentationMode?,
        updateMode: Boolean
    ) {
        isAudioOnly = audioPlayerSelected
        if (updateMode) {
            mode = requestedMode ?: if (audioPlayerSelected) {
                PlaybackPresentationMode.AUDIO_BACKGROUND
            } else {
                PlaybackPresentationMode.VIDEO
            }
            visualizerAudioProcessor.setEnabled(mode.allowsVisualizer())
        }
    }

    fun useVideoAndSubtitles(enabled: Boolean) {
        val playQueue = player.playQueue ?: return
        isAudioOnly = !enabled
        val item = playQueue.item
        val hasPendingRecovery = item != null &&
            item.recoveryPosition != PlayQueueItem.RECOVERY_UNSET
        val hasTimeline = !player.exoPlayerIsNull() &&
            !player.exoPlayer.currentTimeline.isEmpty

        val streamInfo = player.currentStreamInfo.orElse(null)
        if (hasTimeline || !hasPendingRecovery) {
            player.setRecovery()
        }
        if (streamInfo == null || needsQueueManagerReload(streamInfo)) {
            player.reloadPlayQueueManager()
        }

        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled)
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !enabled)
        )
    }

    fun setMode(newMode: PlaybackPresentationMode) {
        if (mode == newMode) return
        mode = newMode
        visualizerAudioProcessor.setEnabled(newMode.allowsVisualizer())
        useVideoAndSubtitles(newMode.rendersVideo())
        player.updateAudioTunneling()
        player.UIs().call { ui -> ui.onPlaybackPresentationModeChanged(newMode) }
    }

    private fun needsQueueManagerReload(streamInfo: StreamInfo): Boolean {
        val sourceType = videoResolver.streamSourceType
            .orElse(SourceType.VIDEO_WITH_AUDIO_OR_AUDIO_ONLY)
        val streamType = streamInfo.streamType
        val isStreamTypeAudio = StreamTypeUtil.isAudio(streamType)
        if (videoRendererIndex() == Player.RENDERER_UNAVAILABLE && !isStreamTypeAudio) {
            return true
        }
        if (isStreamTypeAudio ||
            streamType == StreamType.LIVE_STREAM && sourceType == SourceType.LIVE_STREAM
        ) {
            return false
        }
        if (sourceType == SourceType.VIDEO_WITH_SEPARATED_AUDIO ||
            sourceType == SourceType.VIDEO_WITH_AUDIO_OR_AUDIO_ONLY &&
            streamInfo.audioStreams.isNullOrEmpty()
        ) {
            return !StreamTypeUtil.isVideo(streamType)
        }
        return true
    }

    private fun videoRendererIndex(): Int {
        val mappedTrackInfo = trackSelector.currentMappedTrackInfo
            ?: return Player.RENDERER_UNAVAILABLE
        if (player.exoPlayerIsNull()) return Player.RENDERER_UNAVAILABLE
        return (0 until mappedTrackInfo.rendererCount).firstOrNull { index ->
            !mappedTrackInfo.getTrackGroups(index).isEmpty &&
                player.exoPlayer.getRendererType(index) == C.TRACK_TYPE_VIDEO
        } ?: Player.RENDERER_UNAVAILABLE
    }
}
