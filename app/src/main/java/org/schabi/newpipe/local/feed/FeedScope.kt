/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.feed

import android.content.Context
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.util.ServiceHelper

/**
 * Identifies the subscription/feed namespace selected in the service drawer.
 *
 * YouTube and YouTube Music share an extractor service id, so [youtubeModeMask] keeps their
 * subscriptions and feed updates independent.
 */
data class FeedScope(
    val serviceId: Int,
    val youtubeModeMask: Int
) {
    fun includes(subscription: SubscriptionEntity): Boolean {
        if (subscription.serviceId != serviceId) {
            return false
        }
        return serviceId != SubscriptionEntity.YOUTUBE_SERVICE_ID ||
            subscription.youtubeModeMask and youtubeModeMask != 0
    }

    companion object {
        @JvmStatic
        fun from(context: Context): FeedScope {
            return FeedScope(
                ServiceHelper.getSelectedServiceId(context),
                if (ServiceHelper.isYoutubeMusicMode(context)) {
                    SubscriptionEntity.YOUTUBE_MODE_MUSIC
                } else {
                    SubscriptionEntity.YOUTUBE_MODE_REGULAR
                }
            )
        }
    }
}
