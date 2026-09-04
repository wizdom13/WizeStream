/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.download

import android.content.Context
import android.util.Log
import androidx.collection.SparseArrayCompat
import java.util.ArrayList
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod.PROGRESSIVE_HTTP
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.util.AudioTrackAdapter.AudioTracksWrapper
import org.schabi.newpipe.util.ListHelper
import org.schabi.newpipe.util.SecondaryStreamHelper
import org.schabi.newpipe.util.StreamItemAdapter
import org.schabi.newpipe.util.StreamItemAdapter.StreamInfoWrapper

internal data class DownloadStreamCatalog(
    val audioTracks: AudioTracksWrapper,
    val videoStreams: StreamInfoWrapper<VideoStream>,
    val subtitleStreams: StreamInfoWrapper<SubtitlesStream>,
    val selectedAudioTrackIndex: Int,
    val selectedVideoIndex: Int,
    val muxedAudioFallbackVideoIndex: Int
)

/** Creates the filtered and ordered stream catalog used by the download dialog. */
internal object DownloadStreamCatalogFactory {
    @JvmStatic
    fun create(context: Context, info: StreamInfo): DownloadStreamCatalog {
        val audioStreams = ListHelper.getStreamsOfSpecifiedDelivery(
            info.audioStreams,
            PROGRESSIVE_HTTP
        )
        val groupedAudioStreams = ArrayList(
            ListHelper.getGroupedAudioStreams(context, audioStreams)
        )

        // TODO: Adapt this when the downloader supports other stream delivery methods.
        val videoStreams = ListHelper.getSortedStreamVideosList(
            context,
            ListHelper.getStreamsOfSpecifiedDelivery(info.videoStreams, PROGRESSIVE_HTTP),
            ListHelper.getStreamsOfSpecifiedDelivery(info.videoOnlyStreams, PROGRESSIVE_HTTP),
            false,
            // Prefer video-only streams when multiple languages allow explicit audio selection.
            groupedAudioStreams.size > 1
        )
        val subtitleStreams = ListHelper.getStreamsOfSpecifiedDelivery(
            info.subtitles,
            PROGRESSIVE_HTTP
        )

        val fallbackIndex = addMuxedAudioFallback(groupedAudioStreams, videoStreams)
        return buildCatalog(
            context = context,
            groupedAudioStreams = groupedAudioStreams,
            videoStreams = videoStreams,
            subtitleStreams = subtitleStreams,
            selectedAudioTrackIndex = ListHelper.getDefaultAudioTrackGroup(
                context,
                groupedAudioStreams
            ),
            selectedVideoIndex = ListHelper.getDefaultResolutionIndex(context, videoStreams),
            fallbackIndex = fallbackIndex
        )
    }

    internal fun fromPreparedStreams(
        context: Context?,
        groupedAudioStreams: List<List<AudioStream>>,
        videoStreams: List<VideoStream>,
        subtitleStreams: List<SubtitlesStream>,
        selectedAudioTrackIndex: Int,
        selectedVideoIndex: Int
    ): DownloadStreamCatalog {
        val mutableAudioGroups = ArrayList(groupedAudioStreams)
        val fallbackIndex = addMuxedAudioFallback(mutableAudioGroups, videoStreams)
        return buildCatalog(
            context,
            mutableAudioGroups,
            videoStreams,
            subtitleStreams,
            selectedAudioTrackIndex,
            selectedVideoIndex,
            fallbackIndex
        )
    }

    private fun addMuxedAudioFallback(
        groupedAudioStreams: MutableList<List<AudioStream>>,
        videoStreams: List<VideoStream>
    ): Int {
        if (groupedAudioStreams.isNotEmpty()) {
            return -1
        }
        val fallbackIndex = MuxedAudioFallbackPolicy.findFallbackVideoIndex(videoStreams)
        if (fallbackIndex >= 0) {
            groupedAudioStreams.add(
                listOf(
                    MuxedAudioFallbackPolicy.createFallbackAudioStream(
                        videoStreams[fallbackIndex]
                    )
                )
            )
        }
        return fallbackIndex
    }

    private fun buildCatalog(
        context: Context?,
        groupedAudioStreams: List<List<AudioStream>>,
        videoStreams: List<VideoStream>,
        subtitleStreams: List<SubtitlesStream>,
        selectedAudioTrackIndex: Int,
        selectedVideoIndex: Int,
        fallbackIndex: Int
    ): DownloadStreamCatalog {
        return DownloadStreamCatalog(
            audioTracks = AudioTracksWrapper(groupedAudioStreams, context),
            videoStreams = StreamInfoWrapper(videoStreams, context),
            subtitleStreams = StreamInfoWrapper(subtitleStreams, context),
            selectedAudioTrackIndex = selectedAudioTrackIndex,
            selectedVideoIndex = selectedVideoIndex,
            muxedAudioFallbackVideoIndex = fallbackIndex
        )
    }
}

internal data class DownloadStreamAdapters(
    val audioStreams: StreamItemAdapter<AudioStream, Stream>,
    val videoStreams: StreamItemAdapter<VideoStream, AudioStream>
)

/** Rebuilds stream adapters and compatible secondary audio after an audio-track change. */
internal object DownloadStreamAdapterFactory {
    @JvmStatic
    fun create(
        context: Context,
        audioTracks: AudioTracksWrapper,
        selectedAudioTrackIndex: Int,
        videoStreams: StreamInfoWrapper<VideoStream>,
        muxedAudioFallbackVideoIndex: Int,
        debug: Boolean
    ): DownloadStreamAdapters {
        val audioStreams = selectedAudioStreams(audioTracks, selectedAudioTrackIndex)
        val hasMuxedAudioFallback = muxedAudioFallbackVideoIndex >= 0 &&
            muxedAudioFallbackVideoIndex < videoStreams.streamsList.size
        val secondaryAudioStreams = if (hasMuxedAudioFallback) {
            StreamInfoWrapper.empty()
        } else {
            audioStreams
        }
        val secondaryStreams = SparseArrayCompat<SecondaryStreamHelper<AudioStream>>(4)

        videoStreams.resetInfo()
        videoStreams.streamsList.forEachIndexed { index, videoStream ->
            if (!videoStream.isVideoOnly) {
                return@forEachIndexed
            }
            val audioStream = SecondaryStreamHelper.getAudioStreamFor(
                context,
                secondaryAudioStreams.streamsList,
                videoStream
            )
            if (audioStream != null) {
                secondaryStreams.append(
                    index,
                    SecondaryStreamHelper(secondaryAudioStreams, audioStream)
                )
            } else if (debug) {
                logMissingSecondaryStream(videoStream.format)
            }
        }

        return DownloadStreamAdapters(
            audioStreams = StreamItemAdapter(audioStreams),
            videoStreams = StreamItemAdapter(videoStreams, secondaryStreams)
        )
    }

    private fun selectedAudioStreams(
        audioTracks: AudioTracksWrapper,
        selectedAudioTrackIndex: Int
    ): StreamInfoWrapper<AudioStream> {
        return if (selectedAudioTrackIndex !in 0 until audioTracks.size()) {
            StreamInfoWrapper.empty()
        } else {
            audioTracks.tracksList[selectedAudioTrackIndex]
        }
    }

    private fun logMissingSecondaryStream(format: MediaFormat?) {
        if (format == null) {
            Log.w(TAG, "No audio stream candidates for unknown video format")
        } else {
            Log.w(TAG, "No audio stream candidates for video format ${format.name}")
        }
    }

    private const val TAG = "DownloadStreamAdapterFactory"
}
