package org.schabi.newpipe.database.stream

import org.schabi.newpipe.database.subscription.SubscriptionEntity

/** Distinguishes the feed's retention timestamp from a date supplied by YouTube. */
object StreamUploadDate {
    // Synthetic feed-retention timestamps, including undated Shorts and regular continuation
    // videos, are approximate and have no source date text. Parsed YouTube dates keep that text.
    fun isSynthetic(serviceId: Int, isApproximation: Boolean?, textualUploadDate: String?): Boolean = serviceId == SubscriptionEntity.YOUTUBE_SERVICE_ID &&
        isApproximation == true && textualUploadDate.isNullOrBlank()
}
