package org.schabi.newpipe.extractor.services.odysee

import com.grack.nanojson.JsonObject
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.linkhandler.LinkHandler
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream

class OdyseeStreamExtractor(
    service: StreamingService,
    linkHandler: LinkHandler
) : StreamExtractor(service, linkHandler) {
    private lateinit var claim: JsonObject
    private var streamingUrl = ""

    override fun onFetchPage(downloader: Downloader) {
        val uri = linkHandler.id
        claim = OdyseeApi.resolveClaim(uri)
        val get = OdyseeApi.rpc(
            "get",
            JsonObject().apply { put("uri", uri) }
        )
        streamingUrl = get.getObject("result").getString("streaming_url", "")
    }

    override fun getName(): String {
        return claim.getObject("value").getString("title", claim.getString("name", ""))
    }

    override fun getUploaderName(): String = claim
        .getObject("signing_channel").getObject("value").getString(
            "title",
            claim.getObject("signing_channel").getString("name", "")
        )

    override fun getUploaderUrl(): String {
        val channel = claim.getObject("signing_channel")
        val uri = channel.getString("canonical_url", channel.getString("short_url", ""))
        return if (uri.isBlank()) "" else OdyseeApi.webUrl(uri)
    }

    override fun getThumbnailUrl(): String {
        return claim.getObject("value").getObject("thumbnail").getString("url", "")
    }

    override fun getLength(): Long {
        return claim.getObject("value").getObject("video").getLong("duration", 0L)
    }

    override fun getViewCount(): Long = claim.getObject("meta").getLong("views", -1L)

    override fun getStreamType(): StreamType = StreamType.VIDEO_STREAM

    override fun getVideoStreams(): List<VideoStream> {
        if (streamingUrl.isBlank()) return emptyList()
        return listOf(
            VideoStream.Builder()
                .setId(claim.getString("claim_id", "odysee"))
                .setContent(streamingUrl, true)
                .setMediaFormat(MediaFormat.MPEG_4)
                .setIsVideoOnly(false)
                .setResolution(VideoStream.RESOLUTION_UNKNOWN)
                .build()
        )
    }

    override fun getVideoOnlyStreams(): List<VideoStream> = emptyList()

    override fun getAudioStreams(): List<AudioStream> = emptyList()
}
