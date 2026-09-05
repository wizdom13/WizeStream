/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

import android.content.Context
import android.content.SharedPreferences
import androidx.media3.common.C
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import org.schabi.newpipe.R
import org.schabi.newpipe.player.helper.ChannelPlaybackProfileManager
import org.schabi.newpipe.player.helper.PlayerHelper

/** Owns caption renderer selection and persisted language preferences. */
internal class PlayerCaptionController(
    private val player: Player,
    private val context: Context,
    private val prefs: SharedPreferences,
    private val trackSelector: DefaultTrackSelector
) {
    fun rendererIndex(): Int {
        if (player.exoPlayerIsNull()) return Player.RENDERER_UNAVAILABLE
        return (0 until player.exoPlayer.rendererCount).firstOrNull { index ->
            player.exoPlayer.getRendererType(index) == C.TRACK_TYPE_TEXT
        } ?: Player.RENDERER_UNAVAILABLE
    }

    fun preference(): String? {
        val currentInfo = player.currentStreamInfo.orElse(null)
        return if (currentInfo != null &&
            ChannelPlaybackProfileManager.hasCaptionPreference(context, currentInfo)
        ) {
            ChannelPlaybackProfileManager.getCaptionPreference(context, currentInfo)
        } else {
            prefs.getString(context.getString(R.string.caption_user_set_key), null)
        }
    }

    fun setPreference(language: String?) {
        val textRendererIndex = rendererIndex()
        if (textRendererIndex != Player.RENDERER_UNAVAILABLE) {
            val parameters = trackSelector.buildUponParameters()
            if (language == null) {
                parameters.setRendererDisabled(textRendererIndex, true)
            } else {
                parameters
                    .setPreferredTextLanguages(
                        language,
                        PlayerHelper.captionLanguageStemOf(language)
                    )
                    .setPreferredTextRoleFlags(C.ROLE_FLAG_CAPTION)
                    .setRendererDisabled(textRendererIndex, false)
            }
            trackSelector.setParameters(parameters)
        }

        val currentInfo = player.currentStreamInfo.orElse(null)
        if (!ChannelPlaybackProfileManager.saveCaptionPreference(
                context,
                currentInfo,
                player.currentItem,
                language
            )
        ) {
            prefs.edit().apply {
                if (language == null) {
                    remove(context.getString(R.string.caption_user_set_key))
                } else {
                    putString(context.getString(R.string.caption_user_set_key), language)
                }
            }.apply()
        }
    }
}
