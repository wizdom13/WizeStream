/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.download

import java.net.URI
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.util.StreamTypeUtil

/** Capabilities shared by the detail action and the manual download catalog. */
object DownloadSourcePolicy {
    @JvmStatic
    fun isProgressive(stream: Stream): Boolean = stream.isUrl && stream.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP

    @JvmStatic
    fun isHlsVideo(stream: VideoStream): Boolean {
        if (!stream.isUrl || stream.isVideoOnly || stream.deliveryMethod != DeliveryMethod.HLS) return false
        val uri = runCatching { URI(stream.content) }.getOrNull() ?: return false
        return uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank() &&
            uri.rawUserInfo == null && uri.rawFragment == null
    }

    /** HLS is a fallback for complete videos; direct files keep their existing selection behavior. */
    @JvmStatic
    fun hlsVideoFallback(info: StreamInfo): List<VideoStream> {
        if (info.isUpcoming || StreamTypeUtil.isLiveStream(info.streamType) ||
            (info.videoStreams + info.videoOnlyStreams).any(::isProgressive)
        ) {
            return emptyList()
        }
        return info.videoStreams.filter(::isHlsVideo).distinctBy { it.content to it.resolution }
    }

    @JvmStatic
    fun hasDownload(info: StreamInfo): Boolean {
        if (info.isUpcoming || StreamTypeUtil.isLiveStream(info.streamType)) return false
        return sequenceOf(info.audioStreams, info.videoStreams, info.videoOnlyStreams, info.subtitles)
            .flatten().any(::isProgressive) || hlsVideoFallback(info).isNotEmpty()
    }
}
