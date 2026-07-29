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
