/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.fragments.detail.StreamDetailActionPolicy

class DownloadSourcePolicyTest {
    @Test
    fun hlsOnlyVideoOffersDownloadWithoutChangingItsSourceType() {
        val hls = video(DeliveryMethod.HLS)
        val info = info(hls)
        assertTrue(StreamDetailActionPolicy.forStream(info).showDownload)
        assertEquals(listOf(hls), DownloadSourcePolicy.hlsVideoFallback(info))
        assertEquals(DeliveryMethod.HLS, hls.deliveryMethod)
    }

    @Test
    fun progressiveVideoKeepsPriorityOverHls() {
        val info = info(video(DeliveryMethod.HLS), video(DeliveryMethod.PROGRESSIVE_HTTP))
        assertTrue(DownloadSourcePolicy.hasDownload(info))
        assertTrue(DownloadSourcePolicy.hlsVideoFallback(info).isEmpty())
        info.videoStreams = listOf(video(DeliveryMethod.HLS))
        info.videoOnlyStreams = listOf(video(DeliveryMethod.PROGRESSIVE_HTTP, videoOnly = true))
        assertTrue(DownloadSourcePolicy.hlsVideoFallback(info).isEmpty())
    }

    @Test
    fun liveAndUpcomingSourcesRemainUnavailableForDownload() {
        val info = info(video(DeliveryMethod.HLS))
        info.streamType = StreamType.LIVE_STREAM
        assertFalse(StreamDetailActionPolicy.forStream(info).showDownload)
        assertTrue(DownloadSourcePolicy.hlsVideoFallback(info).isEmpty())
        info.streamType = StreamType.VIDEO_STREAM
        info.isUpcoming = true
        assertFalse(StreamDetailActionPolicy.forStream(info).showDownload)
        assertTrue(DownloadSourcePolicy.hlsVideoFallback(info).isEmpty())
    }

    @Test
    fun unsupportedDeliveryVideoOnlyAndInlineManifestsAreExcluded() {
        listOf(
            video(DeliveryMethod.DASH),
            video(DeliveryMethod.HLS, videoOnly = true),
            video(DeliveryMethod.HLS, isUrl = false),
            video(DeliveryMethod.HLS, url = "file:///tmp/manifest.m3u8"),
            video(DeliveryMethod.HLS, url = "https://user:password@example.org/manifest.m3u8")
        ).forEach { stream ->
            assertFalse(DownloadSourcePolicy.hasDownload(info(stream)))
            assertTrue(DownloadSourcePolicy.hlsVideoFallback(info(stream)).isEmpty())
        }
    }

    private fun info(vararg streams: VideoStream) = StreamInfo(0, "fixture", "https://example.org/watch", "Fixture").apply {
        streamType = StreamType.VIDEO_STREAM
        videoStreams = streams.toList()
    }

    private fun video(
        delivery: DeliveryMethod,
        videoOnly: Boolean = false,
        isUrl: Boolean = true,
        url: String = "https://example.org/manifest.m3u8"
    ) = VideoStream.Builder()
        .setId("fixture")
        .setContent(url, isUrl)
        .setMediaFormat(MediaFormat.MPEG_4)
        .setDeliveryMethod(delivery)
        .setResolution("Auto")
        .setIsVideoOnly(videoOnly)
        .build()
}
