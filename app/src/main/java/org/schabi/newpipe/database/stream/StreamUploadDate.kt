package org.schabi.newpipe.database.stream

import org.schabi.newpipe.database.subscription.SubscriptionEntity

/** Distinguishes the feed's retention timestamp from a date supplied by YouTube. */
object StreamUploadDate {
    // The Shorts fallback, including versions already stored in users' databases, has an
    // approximate date but no source date text. YouTube's parsed relative dates keep that text.
    fun isSynthetic(serviceId: Int, isApproximation: Boolean?, textualUploadDate: String?): Boolean = serviceId == SubscriptionEntity.YOUTUBE_SERVICE_ID &&
        isApproximation == true && textualUploadDate.isNullOrBlank()
}
