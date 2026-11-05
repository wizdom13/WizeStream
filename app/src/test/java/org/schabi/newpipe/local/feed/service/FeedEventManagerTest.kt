/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.feed.service

import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.local.feed.FeedScope

class FeedEventManagerTest {
    @Test
    fun lateResultConsumerDoesNotResetANewerRefresh() {
        val scope = FeedScope(3, SubscriptionEntity.YOUTUBE_MODE_REGULAR)
        val result = FeedEventManager.Event.SuccessResultEvent()
        val progress = FeedEventManager.Event.ProgressEvent(1, 4)
        FeedEventManager.postEvent(scope, result)
        FeedEventManager.postEvent(scope, progress)
        FeedEventManager.reset(scope, result)
        FeedEventManager.events(scope).take(1).test().assertValue(progress)
        FeedEventManager.postEvent(scope, result)
        FeedEventManager.reset(scope, result)
        FeedEventManager.events(scope).take(1).test().assertValue(FeedEventManager.Event.IdleEvent)
    }

    @Test
    fun eventsAreIndependentForEachServiceAndYoutubeMode() {
        val youtube = FeedScope(
            SubscriptionEntity.YOUTUBE_SERVICE_ID,
            SubscriptionEntity.YOUTUBE_MODE_REGULAR
        )
        val youtubeMusic = FeedScope(
            SubscriptionEntity.YOUTUBE_SERVICE_ID,
            SubscriptionEntity.YOUTUBE_MODE_MUSIC
        )
        val otherService = FeedScope(1, SubscriptionEntity.YOUTUBE_MODE_REGULAR)

        val youtubeEvents = FeedEventManager.events(youtube).test()
        val youtubeMusicEvents = FeedEventManager.events(youtubeMusic).test()
        val otherServiceEvents = FeedEventManager.events(otherService).test()

        val progress = FeedEventManager.Event.ProgressEvent(1, 3)
        FeedEventManager.postEvent(youtubeMusic, progress)

        youtubeEvents.assertValue(FeedEventManager.Event.IdleEvent)
        otherServiceEvents.assertValue(FeedEventManager.Event.IdleEvent)
        assertEquals(progress, youtubeMusicEvents.values().last())
    }
}
