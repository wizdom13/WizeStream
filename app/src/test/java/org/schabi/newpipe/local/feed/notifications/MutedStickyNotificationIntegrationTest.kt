/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.feed.notifications

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.graphics.Bitmap
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import androidx.room.Room
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Notification as FeedResult
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.processors.PublishProcessor
import io.reactivex.rxjava3.schedulers.Schedulers
import java.time.OffsetDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.error.CrashRecoveryManager
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.feed.service.FeedLoadManager
import org.schabi.newpipe.local.feed.service.FeedUpdateInfo
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.util.image.ExtractorImageCompat
import org.schabi.newpipe.util.image.ImageStrategy
import org.schabi.newpipe.util.image.PreferredImageQuality

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@OptIn(DelicateCoilApi::class, ExperimentalCoilApi::class)
class MutedStickyNotificationIntegrationTest {
    private val context: Application get() = RuntimeEnvironment.getApplication()
    private val preferences get() = PreferenceManager.getDefaultSharedPreferences(context)
    private val notifications get() = context.getSystemService(NotificationManager::class.java)
    private val profile = ProfileManager.DEFAULT_PROFILE_ID
    private val generation = "muted-sticky-integration"
    private val databaseField = NewPipeDatabase::class.java.getDeclaredField("databaseInstance").apply { isAccessible = true }
    private lateinit var database: AppDatabase
    private var previousDatabase: Any? = null
    private var imageLoader: ImageLoader? = null

    @Before
    fun setup() {
        StickyNotificationCoordinator.resetForTests()
        preferences.edit().clear().commit()
        CrashRecoveryManager.keepCurrentPreferences(context)
        ProfileManager.getActiveProfileId(context)
        RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
        RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        previousDatabase = databaseField.get(null)
        databaseField.set(null, database)
        preferences.edit()
            .putBoolean(context.getString(R.string.enable_streams_notifications), true)
            .putBoolean(StickyNotificationSettings.ENABLED_KEY, true)
            .putString(StickyNotificationSettings.GENERATION_KEY, generation)
            .commit()
        notifications.createNotificationChannel(
            NotificationChannel(context.getString(R.string.streams_notification_channel_id), "Streams", NotificationManager.IMPORTANCE_DEFAULT)
        )
        ImageStrategy.setPreferredImageQuality(PreferredImageQuality.MEDIUM)
    }

    @After
    fun teardown() {
        StickyNotificationCoordinator.resetForTests()
        imageLoader?.shutdown()
        SingletonImageLoader.reset()
        ImageStrategy.setPreferredImageQuality(PreferredImageQuality.MEDIUM)
        databaseField.set(null, previousDatabase)
        database.close()
        notifications.cancelAll()
        RxJavaPlugins.reset()
        RxAndroidPlugins.reset()
    }

    @Test
    fun mutedBeforePersistenceCompletesWithoutFeedRowsPendingAlertsOrAudibleCandidates() {
        val channel = channel("A")
        val update = update(channel)
        val capture = capture()
        val loader = FeedLoadManager(context, profile, capture::persist)
        val fetched = PublishProcessor.create<FeedResult<FeedUpdateInfo>>()
        val result = loader.storeResults(fetched).test()

        // The request and its notification recorder exist before the user's mute choice.
        database.subscriptionDAO().setFeedMutedForProfile(profile, listOf(channel.uid), true)
        fetched.onNext(FeedResult.createOnNext(update))
        fetched.onComplete()

        result.assertComplete().assertNoErrors().assertValue { outcomes ->
            outcomes.size == 1 && outcomes.single().isOnComplete
        }
        assertTrue(update.newStreams.isEmpty())
        assertFalse(database.streamDAO().exists(channel.serviceId, update.streams.single().url))
        assertTrue(database.feedDAO().getMemberships(channel.uid).isEmpty())
        assertTrue(pending().isEmpty())
        assertTrue(capture.newlyInsertedChannels().isEmpty())
        database.query("SELECT COUNT(*) FROM feed_last_updated", null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        val stored = requireNotNull(database.subscriptionDAO().getSubscriptionForProfile(profile, channel.uid))
        assertTrue(stored.feedMuted)
        assertEquals(NotificationMode.ENABLED, stored.notificationMode)
    }

    @Test
    fun mutingDuringArtworkKeepsCommittedAlertsVisibleButOnlyUnmutedChannelMaySound() {
        val mutedLater = channel("A")
        val stillEnabled = channel("B")
        val firstUpdate = update(mutedLater, thumbnail = "https://example.com/a-thumbnail.jpg")
        val secondUpdate = update(stillEnabled)
        val capture = capture()
        FeedLoadManager(context, profile, capture::persist)
            .storeResults(Flowable.just(FeedResult.createOnNext(firstUpdate), FeedResult.createOnNext(secondUpdate)))
            .test().assertComplete().assertNoErrors()
        val channelKeys = setOf(mutedLater.serviceId to requireNotNull(mutedLater.url), stillEnabled.serviceId to requireNotNull(stillEnabled.url))
        assertEquals(channelKeys, capture.newlyInsertedChannels())
        val committed = pending()
        assertEquals(2, committed.size)

        val imageStarted = CountDownLatch(1)
        val releaseImage = CompletableDeferred<Unit>()
        imageLoader = ImageLoader.Builder(context)
            .mainCoroutineContext(Dispatchers.Unconfined)
            .coroutineContext(Dispatchers.Unconfined)
            .components {
                add(
                    Interceptor { chain ->
                        imageStarted.countDown()
                        // Hold the real response independently of the production image timeout;
                        // this fixture tests mute ordering rather than a device's decode speed.
                        withContext(NonCancellable) { releaseImage.await() }
                        SuccessResult(Bitmap.createBitmap(16, 9, Bitmap.Config.ARGB_8888).asImage(), chain.request)
                    }
                )
            }
            .build()
        SingletonImageLoader.setUnsafe(requireNotNull(imageLoader))
        val executor = Executors.newSingleThreadExecutor()
        val render = executor.submit {
            StickyNotificationCoordinator.reconcile(context, silent = false, alertChannels = capture.newlyInsertedChannels())
        }
        try {
            assertTrue("Notification artwork must be in flight before muting", imageStarted.await(5, TimeUnit.SECONDS))
            database.subscriptionDAO().setFeedMutedForProfile(profile, listOf(mutedLater.uid), true)
            assertEquals(committed, pending())
        } finally {
            releaseImage.complete(Unit)
            try {
                render.get(5, TimeUnit.SECONDS)
            } finally {
                executor.shutdownNow()
                executor.awaitTermination(5, TimeUnit.SECONDS)
            }
        }

        assertEquals("Muting keeps previously committed alerts pending", committed, pending())
        val posted = notifications.activeNotifications.map { it.notification }
        assertEquals(4, posted.size)
        val children = posted.filter { target(it).action == StickyNotificationAction.OPEN_ITEM }
        assertEquals(setOf(firstUpdate.streams.single().url, secondUpdate.streams.single().url), children.map { target(it).url }.toSet())
        val summaries = posted.filter { target(it).action == StickyNotificationAction.OPEN_CHANNEL }
        assertEquals(2, summaries.size)
        val quiet = summaries.single { target(it).url == mutedLater.url }
        assertEquals(NotificationCompat.GROUP_ALERT_CHILDREN, quiet.groupAlertBehavior)
        assertTrue(quiet.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        val audible = summaries.single { target(it).url == stillEnabled.url }
        assertEquals(NotificationCompat.GROUP_ALERT_SUMMARY, audible.groupAlertBehavior)
        assertEquals(0, audible.flags and Notification.FLAG_ONLY_ALERT_ONCE)
        assertTrue(requireNotNull(database.subscriptionDAO().getSubscriptionForProfile(profile, mutedLater.uid)).feedMuted)
        assertFalse(requireNotNull(database.subscriptionDAO().getSubscriptionForProfile(profile, stillEnabled.uid)).feedMuted)
    }

    private fun capture(): StickyNotificationCapture = StickyNotificationCapture(
        context,
        requireNotNull(StickyNotificationSettings.capture(context, profile))
    )

    private fun pending() = database.pendingStreamNotificationDAO().pending(profile, generation)

    private fun channel(name: String): SubscriptionEntity = SubscriptionEntity(
        serviceId = 0,
        url = "https://example.com/channel/$name",
        name = "Channel $name",
        notificationMode = NotificationMode.ENABLED,
        profileId = profile
    ).apply { uid = database.subscriptionDAO().insert(this) }

    private fun update(channel: SubscriptionEntity, thumbnail: String? = null): FeedUpdateInfo = FeedUpdateInfo(
        uid = channel.uid,
        notificationMode = channel.notificationMode,
        notificationKeywords = "",
        name = requireNotNull(channel.name),
        avatarUrl = null,
        url = requireNotNull(channel.url),
        serviceId = channel.serviceId,
        youtubeModeMask = channel.youtubeModeMask,
        description = null,
        subscriberCount = null,
        streams = listOf(
            StreamInfoItem(channel.serviceId, "${channel.url}/video", "Video from ${channel.name}", StreamType.VIDEO_STREAM).apply {
                uploadDate = DateWrapper(OffsetDateTime.now().minusHours(1))
                uploaderName = channel.name
                uploaderUrl = channel.url
                if (thumbnail != null) {
                    ExtractorImageCompat.setThumbnailImages(this, listOf(Image(thumbnail, 90, 160, Image.ResolutionLevel.MEDIUM)))
                }
            }
        ),
        errors = emptyList()
    )

    private fun target(notification: Notification): StickyNotificationTarget = requireNotNull(
        StickyNotificationTarget.fromIntent(shadowOf(notification.contentIntent).savedIntent)
    )
}
