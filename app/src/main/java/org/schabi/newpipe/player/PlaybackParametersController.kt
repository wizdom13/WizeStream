/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

import androidx.media3.common.PlaybackParameters
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.helper.ChannelPlaybackProfileManager
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.util.StreamTypeUtil

/** Owns playback parameter application, persistence, and channel-profile restoration. */
internal class PlaybackParametersController(private val player: Player) {
    val parameters: PlaybackParameters
        get() {
            return if (player.exoPlayerIsNull()) {
                PlaybackParameters.DEFAULT
            } else {
                player.exoPlayer.playbackParameters
            }
        }

    val speed: Float
        get() = parameters.speed

    val pitch: Float
        get() = parameters.pitch

    val skipSilence: Boolean
        get() = !player.exoPlayerIsNull() && player.exoPlayer.skipSilenceEnabled

    fun setSpeed(speed: Float) {
        setParameters(speed, pitch, skipSilence)
    }

    fun setSpeedTemporarily(speed: Float) {
        if (!player.exoPlayerIsNull()) {
            player.exoPlayer.playbackParameters = PlaybackParameters(speed, pitch)
        }
    }

    fun setParameters(speed: Float, pitch: Float, skipSilence: Boolean) {
        val roundedSpeed = Math.round(speed * DECIMAL_SCALE) / DECIMAL_SCALE
        val roundedPitch = Math.round(pitch * DECIMAL_SCALE) / DECIMAL_SCALE
        val currentInfo = player.currentStreamInfo.orElse(null)

        if (
            ChannelPlaybackProfileManager.saveSpeed(
                player.context,
                currentInfo,
                player.currentItem,
                roundedSpeed
            )
        ) {
            player.prefs.edit()
                .putFloat(player.context.getString(R.string.playback_pitch_key), roundedPitch)
                .putBoolean(
                    player.context.getString(R.string.playback_skip_silence_key),
                    skipSilence
                )
                .apply()
        } else {
            PlayerHelper.savePlaybackParametersToPrefs(
                player,
                roundedSpeed,
                roundedPitch,
                skipSilence
            )
        }
        applyParameters(roundedSpeed, roundedPitch, skipSilence)
    }

    fun applyParameters(speed: Float, pitch: Float, skipSilence: Boolean) {
        player.exoPlayer.playbackParameters = PlaybackParameters(speed, pitch)
        player.exoPlayer.skipSilenceEnabled = skipSilence
    }

    fun applySpeedProfile(item: PlayQueueItem) {
        val profileSpeed = if (ChannelPlaybackProfileManager.isAvailable(player.context, item)) {
            ChannelPlaybackProfileManager.getSpeed(player.context, item)
        } else {
            null
        }
        val preferredSpeed = PlayerHelper.retrievePlaybackParametersFromPrefs(player).speed
        val targetSpeed = resolvePlaybackSpeed(item.streamType, profileSpeed, preferredSpeed)
        player.exoPlayer.playbackParameters = PlaybackParameters(targetSpeed, pitch)
    }

    fun applySpeedProfile(info: StreamInfo) {
        val profileSpeed = if (ChannelPlaybackProfileManager.isAvailable(player.context, info)) {
            ChannelPlaybackProfileManager.getSpeed(player.context, info)
        } else {
            null
        }
        val preferredSpeed = PlayerHelper.retrievePlaybackParametersFromPrefs(player).speed
        val targetSpeed = resolvePlaybackSpeed(info.streamType, profileSpeed, preferredSpeed)
        player.exoPlayer.playbackParameters = PlaybackParameters(targetSpeed, pitch)
    }

    companion object {
        const val NORMAL_SPEED = 1.0f
        private const val DECIMAL_SCALE = 100.0f

        @JvmStatic
        fun resolvePlaybackSpeed(
            streamType: StreamType?,
            profileSpeed: Float?,
            preferredSpeed: Float
        ): Float {
            if (streamType != null && StreamTypeUtil.isLiveStream(streamType)) {
                return NORMAL_SPEED
            }
            return profileSpeed ?: preferredSpeed
        }
    }
}
