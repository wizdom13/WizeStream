package org.schabi.newpipe.local.feed.service

import java.time.OffsetDateTime
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.stream.StreamInfoItem

internal object FeedItemDateResolver {
    /**
     * Retains undated Shorts without claiming to know when they were published.
     *
     * The database needs a timestamp to expire old entries. This approximate first-seen value
     * is kept stable by StreamDAO and is not used as a publication date in the feed. Dates of
     * regular videos from another channel tab cannot establish a Short's publication date.
     * The Shorts tab supplies neither a date nor source date text; StreamUploadDate recognizes
     * this same signature in already-cached entries from earlier app versions.
     */
    fun applyApproximateDates(streams: List<StreamInfoItem>, fetchedAt: OffsetDateTime) {
        streams.filter { it.uploadDate == null && it.isShortFormContent }
            .forEachIndexed { index, stream ->
                stream.uploadDate = DateWrapper(fetchedAt.minusSeconds(index.toLong()), true)
            }
    }
}
