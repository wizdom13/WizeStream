package org.schabi.newpipe.extractor.services.odysee

import com.grack.nanojson.JsonObject
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor
import org.schabi.newpipe.extractor.stream.StreamType

class OdyseeStreamInfoItemExtractor(private val claim: JsonObject) : StreamInfoItemExtractor {
    override fun getName(): String {
        return claim.getObject("value").getString("title", claim.getString("name", ""))
    }

    override fun getUrl(): String = OdyseeApi.webUrl(
        claim.getString("canonical_url", claim.getString("permanent_url", ""))
    )

    override fun getThumbnails(): List<Image> {
        val url = claim.getObject("value").getObject("thumbnail").getString("url", "")
        return if (url.isBlank()) {
            emptyList()
        } else {
            listOf(
                Image(
                    url,
                    Image.HEIGHT_UNKNOWN,
                    Image.WIDTH_UNKNOWN,
                    Image.ResolutionLevel.UNKNOWN
                )
            )
        }
    }

    override fun getStreamType(): StreamType = StreamType.VIDEO_STREAM

    override fun getDuration(): Long {
        return claim.getObject("value").getObject("video").getLong("duration", 0L)
    }

    override fun getViewCount(): Long = claim.getObject("meta").getLong("views", -1L)

    override fun getUploaderName(): String {
        val channel = claim.getObject("signing_channel")
        return channel.getObject("value").getString("title", channel.getString("name", ""))
    }

    override fun getUploaderUrl(): String {
        val channel = claim.getObject("signing_channel")
        val uri = channel.getString("canonical_url", channel.getString("short_url", ""))
        return if (uri.isBlank()) "" else OdyseeApi.webUrl(uri)
    }

    override fun getTextualUploadDate(): String? = odyseeUploadDate(claim)?.toInstant()?.toString()

    override fun getUploadDate(): DateWrapper? = odyseeUploadDate(claim)?.let(::DateWrapper)
}
