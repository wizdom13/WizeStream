/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

import org.schabi.newpipe.player.equalizer.EqualizerController
import org.schabi.newpipe.player.equalizer.EqualizerState

/** Owns mute, equalizer, audio-focus, and effective-volume coordination. */
internal class PlayerAudioController(private val player: Player) {
    private val equalizer = EqualizerController(player.context)

    var isMuted = false
        private set

    val equalizerState: EqualizerState
        get() = equalizer.state

    val isEqualizerAvailable: Boolean
        get() = equalizer.isAvailable

    val isEqualizerOperational: Boolean
        get() = equalizer.isOperational

    val equalizerHeadroomMultiplier: Float
        get() = equalizer.headroomMultiplier

    fun attachAudioSession(audioSessionId: Int) {
        equalizer.attachAudioSession(audioSessionId)
    }

    fun releaseAudioSession() {
        equalizer.releaseAudioSession()
    }

    fun onAudioSessionChanged(audioSessionId: Int) {
        attachAudioSession(audioSessionId)
        player.applyPlayerVolume()
        player.UIs().call { ui ->
            ui.onEqualizerStateChanged(equalizer.state, equalizer.isOperational)
        }
    }

    fun toggleMute() {
        val audioReactor = player.audioReactor
        if (player.exoPlayerIsNull() || audioReactor == null) {
            return
        }
        isMuted = !isMuted
        player.applyPlayerVolume()
        if (isMuted) {
            audioReactor.abandonAudioFocus()
        } else {
            audioReactor.requestAudioFocus()
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
