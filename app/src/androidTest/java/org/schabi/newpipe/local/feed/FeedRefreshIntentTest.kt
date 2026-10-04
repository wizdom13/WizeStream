package org.schabi.newpipe.local.feed

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.local.feed.service.FeedLoadService

@RunWith(AndroidJUnit4::class)
class FeedRefreshIntentTest {
    @Test
    fun normalRefreshRespectsThresholdAndForcedRefreshKeepsTheSelectedGroupAndScope() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = FeedScope(0, SubscriptionEntity.YOUTUBE_MODE_MUSIC)
        val normal = FeedLoadService.createLoadIntent(context, 42L, scope)
        val forced = FeedLoadService.createLoadIntent(context, 42L, scope, ignoreOutdatedThreshold = true)
        assertFalse(normal.getBooleanExtra(FeedLoadService.EXTRA_IGNORE_OUTDATED_THRESHOLD, true))
        assertTrue(forced.getBooleanExtra(FeedLoadService.EXTRA_IGNORE_OUTDATED_THRESHOLD, false))
        listOf(normal, forced).forEach { intent ->
            assertEquals(FeedLoadService::class.java.name, intent.component!!.className)
            assertEquals(42L, intent.getLongExtra(FeedLoadService.EXTRA_GROUP_ID, -1))
            assertEquals(scope.serviceId, intent.getIntExtra(FeedLoadService.EXTRA_SERVICE_ID, -1))
            assertEquals(scope.youtubeModeMask, intent.getIntExtra(FeedLoadService.EXTRA_YOUTUBE_MODE_MASK, -1))
        }
    }
}
