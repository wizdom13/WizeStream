/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

import android.content.Context
import androidx.media3.exoplayer.source.MediaSource
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.player.helper.ChannelPlaybackProfileManager
import org.schabi.newpipe.player.helper.LoadController
import org.schabi.newpipe.player.helper.PlayerDataSource
import org.schabi.newpipe.player.mediaitem.LocalMediaItemTag
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.player.resolver.AudioPlaybackResolver
import org.schabi.newpipe.player.resolver.VideoPlaybackResolver
import org.schabi.newpipe.player.resolver.VideoPlaybackResolver.SourceType

/** Owns remote/local source resolution and stream-selection changes. */
internal class PlayerStreamController(
    private val player: Player,
    private val context: Context,
    private val audioResolver: AudioPlaybackResolver,
    private val videoResolver: VideoPlaybackResolver,
    private val dataSource: PlayerDataSource,
    private val loadController: LoadController
) {
    fun sourceOf(info: StreamInfo): MediaSource? {
        if (player.audioPlayerSelected()) {
            return audioResolver.resolve(info)
        }
        if (player.isAudioOnly &&
            videoResolver.streamSourceType.orElse(
                SourceType.VIDEO_WITH_AUDIO_OR_AUDIO_ONLY
            ) == SourceType.VIDEO_WITH_AUDIO_OR_AUDIO_ONLY
        ) {
            return audioResolver.resolve(info)
        }
        return if (ChannelPlaybackProfileManager.isAvailable(context, info)) {
            videoResolver.resolve(
                info,
                ChannelPlaybackProfileManager.getQuality(context, info)
            )
        } else {
            videoResolver.resolve(info)
        }
    }

    fun sourceOfLocal(item: PlayQueueItem): MediaSource? {
        if (!item.isLocalMedia) return null
        return dataSource.progressiveMediaSourceFactory
            .createMediaSource(LocalMediaItemTag.of(item).asMediaItem())
    }

    fun disablePreloadingOfCurrentTrack() {
        loadController.disablePreloadingOfCurrentTrack()
    }

    fun setPlaybackQuality(quality: String?) {
        player.saveStreamProgressState()
        player.setRecovery()
        if (quality != null) {
            ChannelPlaybackProfileManager.saveQuality(
                context,
                player.currentStreamInfo.orElse(null),
                player.currentItem,
                quality
            )
        }
        videoResolver.setPlaybackQuality(quality)
        player.reloadPlayQueueManager()
    }

    fun setAudioTrack(audioTrackId: String?) {
        player.saveStreamProgressState()
        player.setRecovery()
        videoResolver.setAudioTrack(audioTrackId)
        audioResolver.setAudioTrack(audioTrackId)
        player.reloadPlayQueueManager()
    }
}
