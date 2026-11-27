package org.schabi.newpipe.local.feed.service

import org.schabi.newpipe.database.subscription.SubscriptionEntity

/** The saved subscription the user explicitly chose, independent of the current feed scope. */
data class ChannelFeedRefreshRequest(
    val profileId: String,
    val subscriptionId: Long,
    val serviceId: Int,
    val url: String,
    val youtubeModeMask: Int
) {
    internal fun matches(subscription: SubscriptionEntity): Boolean = subscription.profileId == profileId && subscription.uid == subscriptionId &&
        subscription.serviceId == serviceId && subscription.url == url &&
        subscription.youtubeModeMask == youtubeModeMask

    companion object {
        @JvmStatic
        fun from(subscription: SubscriptionEntity) = ChannelFeedRefreshRequest(
            subscription.profileId,
            subscription.uid,
            subscription.serviceId,
            requireNotNull(subscription.url),
            subscription.youtubeModeMask
        )
    }
}
