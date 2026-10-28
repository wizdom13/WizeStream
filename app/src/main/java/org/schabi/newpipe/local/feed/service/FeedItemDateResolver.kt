package org.schabi.newpipe.local.feed.service

import java.time.OffsetDateTime
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

internal object FeedItemDateResolver {
    /**
     * Retains undated uploads without claiming to know when they were published.
     *
     * The database needs a timestamp to expire old entries. This approximate first-seen value
     * is kept stable by StreamDAO and is not used as a publication date in the feed. Dates of
     * regular videos from another channel tab cannot establish a Short's publication date.
     * Unparsed date text is cleared so StreamUploadDate recognizes this as first-seen.
     * This also matches undated Shorts already cached by earlier app versions.
     */
    fun applyApproximateDates(streams: List<StreamInfoItem>, fetchedAt: OffsetDateTime) {
        streams.filter {
            it.serviceId == ServiceList.YouTube.serviceId && it.uploadDate == null &&
                it.streamType in setOf(StreamType.VIDEO_STREAM, StreamType.AUDIO_STREAM)
        }
            .forEachIndexed { index, stream ->
                // Mark this as first-seen, not a parsed publication date.
                stream.textualUploadDate = null
                stream.uploadDate = DateWrapper(fetchedAt.minusSeconds(index.toLong()), true)
            }
    }
}
