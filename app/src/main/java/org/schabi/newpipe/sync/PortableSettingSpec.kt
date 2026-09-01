/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import android.content.Context
import org.schabi.newpipe.R

internal data class PortableSettingSpec(
    val id: PortableSettingId,
    val preferenceKey: String
)

internal fun portableSettingSpecs(context: Context): List<PortableSettingSpec> = listOf(
    portableSetting(context, PortableSettingId.SERVICE, R.string.current_service_key),
    portableSetting(context, PortableSettingId.CONTENT_COUNTRY, R.string.content_country_key),
    portableSetting(context, PortableSettingId.CONTENT_LANGUAGE, R.string.content_language_key),
    portableSetting(context, PortableSettingId.THEME, R.string.theme_key),
    portableSetting(context, PortableSettingId.NIGHT_THEME, R.string.night_theme_key),
    portableSetting(context, PortableSettingId.THEME_COLOR, R.string.theme_color_key),
    portableSetting(
        context,
        PortableSettingId.DEFAULT_RESOLUTION,
        R.string.default_resolution_key
    ),
    portableSetting(
        context,
        PortableSettingId.DEFAULT_POPUP_RESOLUTION,
        R.string.default_popup_resolution_key
    ),
    portableSetting(
        context,
        PortableSettingId.SHOW_HIGHER_RESOLUTIONS,
        R.string.show_higher_resolutions_key
    ),
    portableSetting(
        context,
        PortableSettingId.DEFAULT_VIDEO_FORMAT,
        R.string.default_video_format_key
    ),
    portableSetting(
        context,
        PortableSettingId.DEFAULT_AUDIO_FORMAT,
        R.string.default_audio_format_key
    ),
    portableSetting(context, PortableSettingId.AUTOPLAY, R.string.autoplay_key),
    portableSetting(
        context,
        PortableSettingId.MINIMIZE_ON_EXIT,
        R.string.minimize_on_exit_key
    ),
    portableSetting(context, PortableSettingId.NATIVE_PIP, R.string.native_pip_key),
    portableSetting(context, PortableSettingId.SEEK_DURATION, R.string.seek_duration_key),
    portableSetting(
        context,
        PortableSettingId.SEEK_PREVIEW_QUALITY,
        R.string.seekbar_preview_thumbnail_key
    ),
    portableSetting(
        context,
        PortableSettingId.PREFER_ORIGINAL_AUDIO,
        R.string.prefer_original_audio_key
    ),
    portableSetting(
        context,
        PortableSettingId.PREFER_DESCRIPTIVE_AUDIO,
        R.string.prefer_descriptive_audio_key
    ),
    portableSetting(
        context,
        PortableSettingId.SHOW_AGE_RESTRICTED_CONTENT,
        R.string.show_age_restricted_content
    ),
    portableSetting(
        context,
        PortableSettingId.YOUTUBE_RESTRICTED_MODE,
        R.string.youtube_restricted_mode_enabled
    ),
    portableSetting(context, PortableSettingId.SHOW_COMMENTS, R.string.show_comments_key),
    portableSetting(
        context,
        PortableSettingId.SHOW_DESCRIPTION,
        R.string.show_description_key
    ),
    portableSetting(context, PortableSettingId.SHOW_META_INFO, R.string.show_meta_info_key),
    portableSetting(context, PortableSettingId.SHOW_NEXT_VIDEO, R.string.show_next_video_key),
    portableSetting(context, PortableSettingId.SHOW_THUMBNAILS, R.string.show_thumbnail_key),
    portableSetting(context, PortableSettingId.IMAGE_QUALITY, R.string.image_quality_key),
    portableSetting(context, PortableSettingId.LIST_VIEW_MODE, R.string.list_view_mode_key),
    portableSetting(
        context,
        PortableSettingId.PREFERRED_OPEN_ACTION,
        R.string.preferred_open_action_key
    ),
    portableSetting(
        context,
        PortableSettingId.SHOW_HOLD_TO_APPEND,
        R.string.show_hold_to_append_key
    ),
    portableSetting(
        context,
        PortableSettingId.SHOW_PLAY_WITH_KODI,
        R.string.show_play_with_kodi_key
    ),
    portableSetting(
        context,
        PortableSettingId.START_FULLSCREEN,
        R.string.start_main_player_fullscreen_key
    ),
    portableSetting(context, PortableSettingId.AUTO_QUEUE, R.string.auto_queue_key),
    portableSetting(context, PortableSettingId.INEXACT_SEEK, R.string.use_inexact_seek_key),
    portableSetting(
        context,
        PortableSettingId.CLEAR_QUEUE_CONFIRMATION,
        R.string.clear_queue_confirmation_key
    ),
    portableSetting(context, PortableSettingId.PLAYBACK_SPEED, R.string.playback_speed_key),
    portableSetting(context, PortableSettingId.PLAYBACK_PITCH, R.string.playback_pitch_key),
    portableSetting(
        context,
        PortableSettingId.PLAYBACK_SKIP_SILENCE,
        R.string.playback_skip_silence_key
    ),
    portableSetting(context, PortableSettingId.LEARNING_MODE, R.string.learning_mode_key),
    portableSetting(context, PortableSettingId.LEARNING_NOTES, R.string.learning_notes_key),
    portableSetting(
        context,
        PortableSettingId.LEARNING_PLAYLIST_PROGRESS,
        R.string.learning_playlist_progress_key
    ),
    portableSetting(
        context,
        PortableSettingId.LEARNING_COUNT_BACKGROUND,
        R.string.learning_count_background_key
    )
)

private fun portableSetting(
    context: Context,
    id: PortableSettingId,
    preferenceKeyResource: Int
): PortableSettingSpec = PortableSettingSpec(id, context.getString(preferenceKeyResource))
