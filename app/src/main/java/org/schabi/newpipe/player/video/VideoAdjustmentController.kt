/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.video

import androidx.media3.common.ColorInfo
import androidx.media3.common.Effect
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener

/** Player-service state, independent of the Activity, fullscreen, popup and PiP surfaces. */
class VideoAdjustmentController(
    initialState: VideoAdjustmentState,
    private val save: (VideoAdjustmentState) -> Unit,
    private val restartPlayback: () -> Unit,
    private val onFailure: () -> Unit,
    private val effects: (VideoAdjustmentState) -> List<Effect> = SdrVideoEffects::create
) : AnalyticsListener {
    var state = initialState.clamped()
        private set
    var pipelineActive = false
        private set
    var failed = false
        private set
    var hdr = false
        private set
    private var engine: ExoPlayer? = null
    private val listeners = mutableSetOf<Runnable>()

    /** Must run before prepare; a never-enabled player does not create a GL effects pipeline. */
    fun attach(player: ExoPlayer) {
        engine = player
        pipelineActive = state.enabled
        hdr = false
        player.addAnalyticsListener(this)
        if (pipelineActive) player.setVideoEffects(effects(state))
        notifyChanged()
    }

    fun detach() {
        engine?.removeAnalyticsListener(this)
        engine = null
        pipelineActive = false
    }

    fun update(newState: VideoAdjustmentState) {
        val previous = state
        state = newState.clamped()
        if (state == previous) return
        if (state.enabled && !previous.enabled) failed = false
        save(state)
        val player = engine
        if (player != null) {
            if (pipelineActive != state.enabled) {
                // Rebuilding also removes the GL pipeline completely when switched off.
                restartPlayback()
            } else if (pipelineActive && state.copy(remember = previous.remember) != previous) {
                player.setVideoEffects(effects(state))
                if (!player.playWhenReady) {
                    // Decode the paused frame again so the editor also previews while paused.
                    player.seekTo(player.currentPosition)
                }
            }
        }
        notifyChanged()
    }

    /** One retry without effects; subsequent errors follow the regular playback error path. */
    fun recover(error: PlaybackException): Boolean {
        val decoderFailure = error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED
        val videoDecoderFailure = error is ExoPlaybackException &&
            MimeTypes.isVideo(error.rendererFormat?.sampleMimeType) && decoderFailure
        val processingFailure = error.errorCode == PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED
        if (!pipelineActive || (!processingFailure && !videoDecoderFailure)) {
            return false
        }
        failed = true
        pipelineActive = false
        state = state.copy(enabled = false)
        save(state)
        restartPlayback()
        notifyChanged()
        onFailure()
        return true
    }

    override fun onVideoInputFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        format: Format,
        decoderReuseEvaluation: DecoderReuseEvaluation?
    ) {
        hdr = ColorInfo.isTransferHdr(format.colorInfo)
        notifyChanged()
    }

    fun addListener(listener: Runnable) {
        listeners.add(listener)
    }

    fun removeListener(listener: Runnable) {
        listeners.remove(listener)
    }

    private fun notifyChanged() = listeners.toList().forEach(Runnable::run)
}
