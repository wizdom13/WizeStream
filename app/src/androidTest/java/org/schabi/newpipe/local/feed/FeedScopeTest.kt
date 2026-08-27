/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.feed

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.util.ServiceHelper

@RunWith(AndroidJUnit4::class)
class FeedScopeTest {
    @Test
    fun changesFollowServiceAndYoutubeModeWithoutRecreatingSubscriber() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        ServiceHelper.setSelectedServiceId(context, SubscriptionEntity.YOUTUBE_SERVICE_ID)

        val observer = FeedScope.changes(context).test()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ServiceHelper.setSelectedServiceId(context, 1)
            ServiceHelper.setSelectedServiceId(context, SubscriptionEntity.YOUTUBE_SERVICE_ID)
            ServiceHelper.setYoutubeMusicMode(context)
        }

        observer.awaitCount(4)
            .assertValueCount(4)
            .assertValueAt(
                0,
                FeedScope(
                    SubscriptionEntity.YOUTUBE_SERVICE_ID,
                    SubscriptionEntity.YOUTUBE_MODE_REGULAR
                )
            )
            .assertValueAt(
                1,
                FeedScope(1, SubscriptionEntity.YOUTUBE_MODE_REGULAR)
            )
            .assertValueAt(
                2,
                FeedScope(
                    SubscriptionEntity.YOUTUBE_SERVICE_ID,
                    SubscriptionEntity.YOUTUBE_MODE_REGULAR
                )
            )
            .assertValueAt(
                3,
                FeedScope(
                    SubscriptionEntity.YOUTUBE_SERVICE_ID,
                    SubscriptionEntity.YOUTUBE_MODE_MUSIC
                )
            )
            .assertNoErrors()

        observer.cancel()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ServiceHelper.setSelectedServiceId(context, SubscriptionEntity.YOUTUBE_SERVICE_ID)
        }
        observer.assertValueCount(4)
    }
}
