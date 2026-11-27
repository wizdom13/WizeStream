/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

import androidx.media3.common.MediaItem
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.player.boost.VolumeBoostController
import org.schabi.newpipe.player.equalizer.EqualizerController
import org.schabi.newpipe.player.equalizer.EqualizerState
import org.schabi.newpipe.player.mediaitem.LocalMediaItemTag
import org.schabi.newpipe.player.mediaitem.MediaItemTag
import org.schabi.newpipe.player.mediaitem.PlaceholderTag
import org.schabi.newpipe.player.mediasource.LoadedMediaSource
import org.schabi.newpipe.player.playqueue.PlayQueueItem

/** Owns mute, real-time audio effects, audio focus, and effective-volume coordination. */
internal class PlayerAudioController(private val player: Player) {
    private val equalizer = EqualizerController(player.context)
    private val volumeBoost = VolumeBoostController(player.context)
    val volume = PlaybackVolumeController(
        player.context,
        player::applyPlayerVolume,
        player::updateAudioTunneling
    )

    private val channelMute = ChannelPlaybackMuteController(player.context, ::applyMute)

    val isMuted: Boolean get() = channelMute.isMuted
    val hasChannelMuteIdentity: Boolean get() = channelMute.hasChannelIdentity

    fun beginItem(item: PlayQueueItem?) = channelMute.begin(item)

    fun onMetadata(info: StreamInfo) = channelMute.onMetadata(info)

    fun onMediaItemTransition(item: MediaItem?) {
        val engine = player.getExoPlayer() ?: return
        if (item != engine.currentMediaItem) return
        MediaItemTag.from(item).ifPresent { tag ->
            if (tag is PlaceholderTag) return@ifPresent
            // Extractors may canonicalize the URL. Keep clicks made while the original item loaded.
            val source = tag.getMaybeExtras(Any::class.java).orElse(null) as? LoadedMediaSource
            val queueItem = source?.stream
            val uploaderUrl = if (tag is LocalMediaItemTag) null else tag.uploaderUrl
            channelMute.select(tag.serviceId, queueItem?.url ?: tag.streamUrl, uploaderUrl)
        }
    }

    val equalizerState: EqualizerState
        get() = equalizer.state

    val isEqualizerAvailable: Boolean
        get() = equalizer.isAvailable

    val isEqualizerOperational: Boolean
        get() = equalizer.isOperational

    val equalizerHeadroomMultiplier: Float
        get() = equalizer.headroomMultiplier

    val volumeBoostGainMb: Int
        get() = volumeBoost.gainMb

    val isVolumeBoostEnabled: Boolean
        get() = volumeBoost.isEnabled

    val isVolumeBoostAvailable: Boolean
        get() = volumeBoost.isAvailable

    val isVolumeBoostOperational: Boolean
        get() = volumeBoost.isOperational

    fun attachAudioSession(audioSessionId: Int) {
        volume.attach()
        equalizer.attachAudioSession(audioSessionId)
        volumeBoost.attachAudioSession(audioSessionId)
    }

    fun releaseAudioSession() {
        volume.detach()
        equalizer.releaseAudioSession()
        volumeBoost.releaseAudioSession()
    }

    fun onAudioSessionChanged(audioSessionId: Int) {
        attachAudioSession(audioSessionId)
        player.applyPlayerVolume()
        player.UIs().call { ui ->
            ui.onEqualizerStateChanged(equalizer.state, equalizer.isOperational)
            ui.onVolumeBoostChanged(volumeBoost.gainMb, volumeBoost.isOperational)
        }
    }

    fun toggleMute() {
        if (player.exoPlayerIsNull() || player.audioReactor == null) {
            return
        }
        // Media3 may have switched items before the batched metadata/UI callback is delivered.
        onMediaItemTransition(player.exoPlayer.currentMediaItem)
        channelMute.toggle()
    }

    private fun applyMute() {
        player.applyPlayerVolume()
        if (isMuted) {
            player.audioReactor?.abandonAudioFocus()
        } else if (player.playWhenReady) {
            player.audioReactor?.requestAudioFocus()
        }
        player.UIs().call { ui -> ui.onMuteUnmuteChanged(isMuted) }
        player.notifyPlaybackUpdateToListeners()
    }

    fun previewEqualizerState(state: EqualizerState) {
        applyEqualizerState(state, false)
    }

    fun updateEqualizerState(state: EqualizerState) {
        applyEqualizerState(state, true)
    }

    fun updateVolumeBoostGainMb(gainMb: Int) {
        val wasEnabled = volumeBoost.isEnabled
        volumeBoost.updateGainMb(gainMb)
        if (wasEnabled != volumeBoost.isEnabled) {
            player.updateAudioTunneling()
        }
        player.UIs().call { ui ->
            ui.onVolumeBoostChanged(volumeBoost.gainMb, volumeBoost.isOperational)
        }
    }

    private fun applyEqualizerState(state: EqualizerState, persist: Boolean) {
        val enabledChanged = equalizer.state.isEnabled != state.isEnabled
        if (persist) {
            equalizer.updateState(state)
        } else {
            equalizer.previewState(state)
        }
        player.applyPlayerVolume()
        if (enabledChanged) {
            player.updateAudioTunneling()
        }
        player.UIs().call { ui ->
            ui.onEqualizerStateChanged(state, equalizer.isOperational)
        }
    }
}
