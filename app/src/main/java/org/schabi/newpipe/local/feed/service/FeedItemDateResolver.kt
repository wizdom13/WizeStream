package org.schabi.newpipe.local.feed.service

import java.time.OffsetDateTime
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.util.StreamTypeUtil

internal object FeedItemDateResolver {
    /**
     * Retains undated YouTube feed items without claiming to know when they were published.
     *
     * Channel continuation responses can omit publication dates for regular videos, just as the
     * Shorts grid does. The database still needs a timestamp for retention, so undated non-live
     * YouTube items receive an approximate first-seen value. StreamUploadDate recognizes this
     * signature as synthetic, which keeps these items out of "new" notifications/highlights and
     * places them after source-dated items when sorting by publication date.
     */
    fun applyApproximateDates(streams: List<StreamInfoItem>, fetchedAt: OffsetDateTime) {
        streams.filter {
            it.serviceId == ServiceList.YouTube.serviceId &&
                it.uploadDate == null &&
                !StreamTypeUtil.isLiveStream(it.streamType)
        }.forEachIndexed { index, stream ->
            stream.uploadDate = DateWrapper(fetchedAt.minusSeconds(index.toLong()), true)
        }
    }
}
