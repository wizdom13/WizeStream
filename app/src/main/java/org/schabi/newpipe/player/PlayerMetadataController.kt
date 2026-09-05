/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

import android.content.Context
import android.graphics.Bitmap
import androidx.media3.common.Player as Media3Player
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.ServiceList.YouTube
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.player.mediaitem.LocalMediaItemTag
import org.schabi.newpipe.player.mediaitem.MediaItemTag
import org.schabi.newpipe.util.image.ExtractorImageCompat
import java.util.Optional

/** Owns current media metadata and the work triggered when it changes. */
internal class PlayerMetadataController(
    private val player: Player,
    private val context: Context,
    private val historyController: PlayerHistoryController,
    private val playbackParametersController: PlaybackParametersController,
    private val sponsorBlockController: SponsorBlockPlaybackController,
    private val thumbnailController: PlayerThumbnailController,
    private val localMetadataController: PlayerLocalMetadataController
) {
    var currentMetadata: MediaItemTag? = null
        private set

    fun onEvents(media3Player: Media3Player) {
        MediaItemTag.from(media3Player.currentMediaItem).ifPresent { tag ->
            if (tag === currentMetadata) return@ifPresent
            val previousInfo = currentMetadata?.maybeStreamInfo?.orElse(null)
            val previousAudioTrack = currentMetadata?.maybeAudioTrack?.orElse(null)
            currentMetadata = tag

            if (tag.errors.isNotEmpty()) {
                ErrorUtil.createNotification(
                    context,
                    ErrorInfo(
                        tag.errors,
                        UserAction.PLAY_STREAM,
                        "Loading failed for [${tag.title}]: ${tag.streamUrl}",
                        tag.serviceId,
                        tag.streamUrl
                    )
                )
            }

            tag.maybeStreamInfo.ifPresent { info ->
                if (Player.DEBUG) {
                    android.util.Log.d(
                        Player.TAG,
                        "ExoPlayer - onEvents() update stream info: ${info.name}"
                    )
                }
                if (previousInfo == null || previousInfo.url != info.url) {
                    updateMetadataWith(info)
                } else if (previousAudioTrack == null ||
                    tag.maybeAudioTrack
                        .map {
                            it.selectedAudioStreamIndex !=
                                previousAudioTrack.selectedAudioStreamIndex
                        }
                        .orElse(false)
                ) {
                    player.notifyAudioTrackUpdateToListeners()
                }
            }
            if (tag is LocalMediaItemTag) {
                updateMetadataForLocalMedia(tag.item)
            }
        }
    }

    fun clear() {
        currentMetadata = null
    }

    fun currentStreamInfo(): Optional<StreamInfo> =
        Optional.ofNullable(currentMetadata).flatMap(MediaItemTag::getMaybeStreamInfo)

    fun updateMetadataWith(info: StreamInfo) {
        if (Player.DEBUG) {
            android.util.Log.d(
                Player.TAG,
                "Playback - onMetadataChanged() called, playing: ${info.name}"
            )
        }
        if (player.exoPlayerIsNull()) return

        localMetadataController.cancel()
        playbackParametersController.applySpeedProfile(info)
        sponsorBlockController.updateSegments(info)
        maybeAutoQueueNextStream(info)
        thumbnailController.load(ExtractorImageCompat.thumbnailImages(info))
        historyController.registerViewed()

        player.notifyMetadataUpdateToListeners()
        player.notifyAudioTrackUpdateToListeners()
        player.UIs().call { ui -> ui.onMetadataChanged(info) }
    }

    fun updateMetadataForLocalMedia(
        item: org.schabi.newpipe.player.playqueue.PlayQueueItem
    ) {
        sponsorBlockController.reset()
        thumbnailController.loadLocal(item)
        localMetadataController.load(item)
        historyController.registerViewed(item)
        player.notifyMetadataUpdateToListeners()
        player.notifyAudioTrackUpdateToListeners()
        player.UIs().call { ui -> ui.onMetadataChanged(currentMetadata) }
    }

    fun videoUrl(): String =
        currentMetadata?.streamUrl ?: context.getString(R.string.unknown_content)

    fun videoUrlAtCurrentTime(): String {
        val timeSeconds = player.exoPlayer.currentPosition / 1000
        val url = videoUrl()
        return if (!player.isLive && timeSeconds >= 0 &&
            currentMetadata?.serviceId == YouTube.serviceId
        ) {
            "$url&t=$timeSeconds"
        } else {
            url
        }
    }

    fun videoTitle(): String =
        currentMetadata?.title ?: context.getString(R.string.unknown_content)

    fun uploaderName(): String =
        currentMetadata?.uploaderName ?: context.getString(R.string.unknown_content)

    fun thumbnail(): Bitmap? = thumbnailController.currentThumbnail

    fun selectedVideoStream(): Optional<VideoStream> =
        Optional.ofNullable(currentMetadata)
            .flatMap(MediaItemTag::getMaybeQuality)
            .filter { quality ->
                quality.selectedVideoStreamIndex >= 0 &&
                    quality.selectedVideoStreamIndex < quality.sortedVideoStreams.size
            }
            .map { quality ->
                quality.sortedVideoStreams[quality.selectedVideoStreamIndex]
            }

    fun selectedAudioStream(): Optional<AudioStream> =
        Optional.ofNullable(currentMetadata)
            .flatMap(MediaItemTag::getMaybeAudioTrack)
            .map(MediaItemTag.AudioTrack::getSelectedAudioStream)

    private fun maybeAutoQueueNextStream(info: StreamInfo) {
        val playQueue = player.playQueue ?: return
        if (playQueue.index != playQueue.size() - 1 ||
            player.repeatMode != Media3Player.REPEAT_MODE_OFF ||
            !PlayerHelper.isAutoQueueEnabled(context)
        ) {
            return
        }
        val currentQueueItem = playQueue.item
        val preferShortFormContent =
            info.isShortFormContent || currentQueueItem?.isShortFormContent == true
        PlayerHelper.autoQueueOf(info, playQueue.streams, preferShortFormContent)
            ?.let { playQueue.append(it.streams) }
    }
}
