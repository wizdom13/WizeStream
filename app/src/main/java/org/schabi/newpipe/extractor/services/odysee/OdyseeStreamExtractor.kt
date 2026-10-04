package org.schabi.newpipe.extractor.services.odysee

import com.grack.nanojson.JsonObject
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.exceptions.ExtractionException
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
        streamingUrl = (get["result"] as? JsonObject)?.getString("streaming_url", "").orEmpty()
        if (streamingUrl.isBlank()) {
            throw ExtractionException("Odysee did not return a playable stream URL")
        }
    }

    private val value: JsonObject
        get() = claim["value"] as JsonObject

    private val channel: JsonObject?
        get() = claim["signing_channel"] as? JsonObject

    override fun getName(): String {
        return value.getString("title", "").ifBlank { claim.getString("name", "") }
            .ifBlank { throw ExtractionException("Odysee claim has no title or name") }
    }

    override fun getUploaderName(): String {
        val uploader = channel ?: return ""
        return (uploader["value"] as? JsonObject)?.getString("title", "").orEmpty()
            .ifBlank { uploader.getString("name", "") }
    }

    override fun getUploaderUrl(): String {
        val uploader = channel ?: return ""
        val uri = uploader.getString("canonical_url", "").ifBlank { uploader.getString("short_url", "") }
        return if (uri.isBlank()) "" else OdyseeApi.webUrl(uri)
    }

    override fun getThumbnailUrl(): String = (value["thumbnail"] as? JsonObject)?.getString("url", "").orEmpty()

    override fun getLength(): Long = (value["video"] as? JsonObject)?.getLong("duration", 0L) ?: 0L

    override fun getViewCount(): Long = (claim["meta"] as? JsonObject)?.getLong("views", -1L) ?: -1L

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
