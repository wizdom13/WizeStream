package org.schabi.newpipe.local.feed.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.graphics.Bitmap
import android.os.Build
import androidx.preference.PreferenceManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.feed.service.FeedUpdateInfo
import org.schabi.newpipe.util.KEY_URL
import org.schabi.newpipe.util.image.ImageStrategy
import org.schabi.newpipe.util.image.PreferredImageQuality

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 35])
class NotificationThumbnailsTest {
    private val context = RuntimeEnvironment.getApplication()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val requested = mutableListOf<String?>()
    private val thumbnail = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
    private val avatar = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
    private lateinit var helper: NotificationHelper

    @Before
    fun setup() {
        ImageStrategy.setPreferredImageQuality(PreferredImageQuality.MEDIUM)
        preferences.edit().remove(context.getString(R.string.streams_notifications_thumbnails_key)).commit()
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(context.getString(R.string.streams_notification_channel_id), "Streams", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        helper = NotificationHelper(context) { url, _, _ ->
            requested += url
            when (url) {
                "https://example.com/thumbnail.jpg" -> thumbnail
                "https://example.com/avatar.jpg" -> avatar
                else -> null
            }
        }
    }

    @After
    fun cleanup() {
        manager.cancelAll()
        ImageStrategy.setPreferredImageQuality(PreferredImageQuality.MEDIUM)
        preferences.edit().remove(context.getString(R.string.streams_notifications_thumbnails_key)).commit()
    }

    @Test
    fun enabledByDefaultWithPictureTitleChannelGroupingAndMatchingClickTarget() {
        val data = update()
        helper.displayNewStreamsNotifications(data)
        val posted = shadowOf(manager).allNotifications
        assertEquals(2, posted.size)
        val child = posted.single { it.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
        assertEquals(data.url, child.group)
        assertEquals("New video", child.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Channel", child.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals("android.app.Notification\$BigPictureStyle", child.extras.getString(Notification.EXTRA_TEMPLATE))
        assertNotNull(child.extras.getParcelable<Bitmap>(Notification.EXTRA_PICTURE))
        assertEquals(data.streams.single().url, shadowOf(child.contentIntent).savedIntent.getStringExtra(KEY_URL))
        assertNull(child.sound)
        assertTrue(child.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertEquals(listOf("https://example.com/avatar.jpg", "https://example.com/thumbnail.jpg"), requested)
        // Stable notification IDs replace existing entries rather than posting an image update alert.
        helper.displayNewStreamsNotifications(data)
        assertEquals(2, shadowOf(manager).allNotifications.size)
    }

    @Test
    fun disabledSettingKeepsTextAndAvatarWithoutLoadingThumbnail() {
        preferences.edit().putBoolean(context.getString(R.string.streams_notifications_thumbnails_key), false).commit()
        helper.displayNewStreamsNotifications(update())
        val child = shadowOf(manager).allNotifications.single { it.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
        assertNull(child.extras.getParcelable<Bitmap>(Notification.EXTRA_PICTURE))
        assertEquals(listOf("https://example.com/avatar.jpg"), requested)
        assertEquals("New video", child.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
    }

    @Test
    fun failedArtworkStillPostsEveryVideoAndChannelSummary() {
        val data = update("https://example.com/missing.jpg")
        data.newStreams = data.newStreams + StreamInfoItem(0, "https://example.com/second", "Second", StreamType.VIDEO_STREAM)
        helper.displayNewStreamsNotifications(data)
        val posted = shadowOf(manager).allNotifications
        assertEquals(3, posted.size)
        assertTrue(posted.all { it.group == data.url })
        assertTrue(
            posted.filter { it.flags and Notification.FLAG_GROUP_SUMMARY == 0 }.all {
                it.extras.getParcelable<Bitmap>(Notification.EXTRA_PICTURE) == null
            }
        )
    }

    @Test
    fun disabledImagesSkipAllArtworkAndEmptyUpdatesPostNothing() {
        ImageStrategy.setPreferredImageQuality(PreferredImageQuality.NONE)
        helper.displayNewStreamsNotifications(update())
        assertTrue(requested.isEmpty())
        assertEquals(2, shadowOf(manager).allNotifications.size)
        manager.cancelAll()
        helper.displayNewStreamsNotifications(update().apply { newStreams = emptyList() })
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }

    private fun update(thumbnailUrl: String = "https://example.com/thumbnail.jpg"): FeedUpdateInfo {
        val item = StreamInfoItem(0, "https://example.com/video", "New video", StreamType.VIDEO_STREAM).apply {
            this.thumbnailUrl = thumbnailUrl
            // A missing uploader URL must not detach the child from its channel group.
            uploaderName = "Channel"
        }
        return FeedUpdateInfo(
            uid = 1,
            notificationMode = NotificationMode.ENABLED,
            notificationKeywords = "",
            name = "Channel",
            avatarUrl = "https://example.com/avatar.jpg",
            url = "https://example.com/channel",
            serviceId = 0,
            youtubeModeMask = SubscriptionEntity.YOUTUBE_MODE_REGULAR,
            description = null,
            subscriberCount = null,
            streams = listOf(item),
            errors = emptyList()
        ).apply { newStreams = streams }
    }
}
