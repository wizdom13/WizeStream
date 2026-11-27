/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.feed

import android.app.Application
import androidx.preference.PreferenceManager
import androidx.room.Room
import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins
import io.reactivex.rxjava3.core.Notification
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.Schedulers
import java.io.IOException
import java.time.OffsetDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockedStatic
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.times
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.feed.model.FeedEntity
import org.schabi.newpipe.database.feed.model.FeedLastUpdatedEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelTabInfo
import org.schabi.newpipe.extractor.linkhandler.ChannelTabs
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.search.filter.FilterItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.feed.service.ChannelFeedRefreshRequest
import org.schabi.newpipe.local.feed.service.FeedLoadManager
import org.schabi.newpipe.local.feed.service.FeedUpdateInfo
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.ServiceHelper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class SingleChannelFeedRefreshTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val profile = ProfileManager.DEFAULT_PROFILE_ID
    private val now = OffsetDateTime.now()
    private val videos = tab(ChannelTabs.VIDEOS)
    private val shorts = tab(ChannelTabs.SHORTS)
    private lateinit var database: AppDatabase
    private val subscriptions get() = database.subscriptionDAO()
    private val feed get() = database.feedDAO()
    private val databaseField = NewPipeDatabase::class.java.getDeclaredField("databaseInstance").apply {
        isAccessible = true
    }
    private var previousDatabase: Any? = null

    @Before
    fun setUp() {
        RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
        RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        ServiceHelper.setSelectedServiceId(context, 0)
        ProfileManager.getActiveProfileId(context)
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        previousDatabase = databaseField.get(null)
        databaseField.set(null, database)
    }

    @After
    fun tearDown() {
        databaseField.set(null, previousDatabase)
        database.close()
        RxJavaPlugins.reset()
        RxAndroidPlugins.reset()
    }

    @Test
    fun mutedTargetRefreshesImmediatelyWithoutChangingOtherFeedsOrSavedPreferences() {
        val target = channel("Target", muted = true, modes = SubscriptionEntity.YOUTUBE_MODE_ALL)
        val other = channel("Other")
        val foreign = channel("Target", profileId = "other-profile")
        cache(other, "old other", now.minusYears(1))
        cache(foreign, "old foreign", now.minusYears(1))
        setUpdated(target, 1, now.plusDays(1))
        setUpdated(target, 2, now.plusDays(1))
        setUpdated(other, 1, now.minusDays(2))
        setUpdated(foreign, 1, now.minusDays(3))
        val othersBefore = listOf(other, foreign).associate { it.uid to snapshot(it) }
        val targetUpdatesBefore = updated(target)
        var recordedAlerts = 0
        val manager = FeedLoadManager(context, profile) { _, _, _ ->
            recordedAlerts++
            null
        }

        mockedChannel(target).use { extractor ->
            val result = manager.startLoadingSubscription(ChannelFeedRefreshRequest.from(target))
                .test().assertComplete().assertNoErrors().values().single()
            assertTrue(result.single().isOnNext)
            extractor.verify({ ExtractorHelper.getChannelInfo(0, target.url, true) }, times(1))
            extractor.verify({ ExtractorHelper.getChannelTab(0, videos, true) }, times(1))
            extractor.verifyNoMoreInteractions()
        }

        assertEquals(setOf(1, 2), feed.getMemberships(target.uid).map { it.youtubeModeMask }.toSet())
        assertEquals(setOf(1, 2), updated(target).keys)
        assertTrue(updated(target).values.all { it != null })
        assertNotEquals(targetUpdatesBefore, updated(target))
        assertEquals(othersBefore, listOf(other, foreign).associate { it.uid to snapshot(it) })
        val stored = subscriptions.getSubscriptionForProfile(profile, target.uid)!!
        assertTrue(stored.feedMuted)
        assertEquals(target.youtubeModeMask, stored.youtubeModeMask)
        assertEquals(target.notificationMode, stored.notificationMode)
        assertEquals(target.notificationKeywords, stored.notificationKeywords)
        assertEquals(0, recordedAlerts)
        assertEquals("Updated Target", stored.name)
    }

    @Test
    fun savedModesAndFeedTabSelectionAreUsedInsteadOfTheCurrentFeedScope() {
        val target = channel("Music", modes = SubscriptionEntity.YOUTUBE_MODE_MUSIC)
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putStringSet(
                context.getString(R.string.feed_fetch_channel_tabs_key),
                setOf(context.getString(R.string.fetch_channel_tabs_shorts))
            )
            .commit()
        assertEquals(SubscriptionEntity.YOUTUBE_MODE_REGULAR, FeedScope.from(context).youtubeModeMask)

        mockedChannel(target, listOf(videos, shorts)).use { extractor ->
            assertTrue(refresh(target).single().isOnNext)
            extractor.verify { ExtractorHelper.getChannelTab(0, shorts, true) }
            extractor.verify({ ExtractorHelper.getChannelTab(0, videos, true) }, times(0))
        }

        assertEquals(listOf("shorts upload"), titles(target))
        assertEquals(listOf(SubscriptionEntity.YOUTUBE_MODE_MUSIC), feed.getMemberships(target.uid).map { it.youtubeModeMask })
        assertEquals(setOf(SubscriptionEntity.YOUTUBE_MODE_MUSIC), updated(target).keys)
    }

    @Test
    fun repeatedManualRefreshFetchesAgainDespiteTheRecentSuccessfulTimestamp() {
        val target = channel("Repeat")
        mockedChannel(target).use { extractor ->
            assertTrue(refresh(target).single().isOnNext)
            val memberships = feed.getMemberships(target.uid)
            assertTrue(refresh(target).single().isOnNext)
            extractor.verify({ ExtractorHelper.getChannelInfo(0, target.url, true) }, times(2))
            assertEquals(memberships, feed.getMemberships(target.uid))
        }
        assertEquals(listOf("videos upload"), titles(target))
    }

    @Test
    fun staleOrForeignRequestCannotFetchOrModifyAnySubscription() {
        val target = channel("Target")
        cache(target, "cached", now.minusDays(1))
        setUpdated(target, 1, now.minusHours(1))
        val request = ChannelFeedRefreshRequest.from(target)
        val before = snapshot(target)
        val requests = listOf(
            request.copy(profileId = "another-profile"),
            request.copy(subscriptionId = target.uid + 1),
            request.copy(serviceId = 1),
            request.copy(url = "https://example.com/other-channel"),
            request.copy(youtubeModeMask = SubscriptionEntity.YOUTUBE_MODE_MUSIC)
        )
        mockStatic(ExtractorHelper::class.java).use { extractor ->
            requests.forEach { stale ->
                val result = FeedLoadManager(context, profile).startLoadingSubscription(stale)
                    .test().assertComplete().assertNoErrors().values().single()
                assertTrue(result.single().isOnComplete)
            }
            extractor.verifyNoInteractions()
        }
        assertEquals(before, snapshot(target))
    }

    @Test
    fun unsubscribingAndResubscribingDuringFetchRejectsTheLateResult() {
        val target = channel("Target", muted = true)
        var replacement: SubscriptionEntity? = null
        mockedChannel(target, beforeInfo = {
            subscriptions.delete(target)
            replacement = channel("Target", muted = true)
        }).use {
            assertTrue(refresh(target).single().isOnComplete)
        }

        val recreated = requireNotNull(replacement)
        assertNotEquals(target.uid, recreated.uid)
        assertTrue(feed.getMemberships(recreated.uid).isEmpty())
        assertTrue(updated(recreated).isEmpty())
        assertEquals(recreated, subscriptions.getSubscriptionForProfile(profile, recreated.uid))
    }

    @Test
    fun changedIdentityDuringAFailedFetchDoesNotInvalidateTheReplacementTimestamp() {
        val target = channel("Target", muted = true)
        val replacement = target.copy(url = "https://example.com/replacement", name = "Replacement")
        cache(target, "cached", now.minusDays(1))
        setUpdated(target, 1, now.minusHours(1))
        val memberships = feed.getMemberships(target.uid)
        val timestamps = updated(target)
        mockedChannel(target, beforeInfo = {
            subscriptions.update(replacement)
            throw IOException("late failure")
        }).use {
            assertTrue(refresh(target).single().isOnComplete)
        }

        assertEquals(replacement, subscriptions.getSubscriptionForProfile(profile, target.uid))
        assertEquals(memberships, feed.getMemberships(target.uid))
        assertEquals(timestamps, updated(target))
    }

    @Test
    fun savedModeChangeDuringFetchRejectsBothSuccessAndFailure() {
        for (failure in listOf(false, true)) {
            val target = channel("Mode $failure", modes = SubscriptionEntity.YOUTUBE_MODE_ALL)
            cache(target, "cached $failure", now.minusDays(1))
            setUpdated(target, 1, now.minusHours(1))
            setUpdated(target, 2, now.minusHours(2))
            val memberships = feed.getMemberships(target.uid)
            val timestamps = updated(target)
            val edited = target.copy(youtubeModeMask = SubscriptionEntity.YOUTUBE_MODE_MUSIC)
            mockedChannel(target, beforeInfo = {
                subscriptions.update(edited)
                if (failure) throw IOException("late failure")
            }).use {
                assertTrue(refresh(target).single().isOnComplete)
            }
            assertEquals(edited, subscriptions.getSubscriptionForProfile(profile, target.uid))
            assertEquals(memberships, feed.getMemberships(target.uid))
            assertEquals(timestamps, updated(target))
        }
    }

    @Test
    fun profileSwitchDuringFetchCannotWriteIntoTheNewlyActiveProfile() {
        val target = channel("Target")
        val otherProfile = requireNotNull(ProfileManager.createProfile(context, "Other"))
        assertTrue(ProfileManager.setActiveProfile(context, profile))
        val foreign = channel("Target", profileId = otherProfile.id)
        cache(foreign, "foreign cached", now.minusDays(1))
        setUpdated(foreign, 1, now.minusHours(1))
        val before = snapshot(foreign)

        mockedChannel(target, beforeInfo = {
            assertTrue(ProfileManager.setActiveProfile(context, otherProfile.id))
        }).use {
            refresh(target)
        }

        assertEquals(otherProfile.id, ProfileManager.getActiveProfileId(context))
        assertEquals(before, snapshot(foreign))
        assertEquals(listOf("foreign cached"), titles(foreign))
    }

    @Test
    fun fetchFailurePreservesCachedItemsAndOnlyMarksTheTargetOutdated() {
        val target = channel("Target", muted = true, modes = SubscriptionEntity.YOUTUBE_MODE_ALL)
        val other = channel("Other")
        cache(target, "cached", now.minusDays(1))
        setUpdated(target, 1, now.minusHours(1))
        setUpdated(target, 2, now.minusHours(2))
        setUpdated(other, 1, now.minusHours(1))
        val before = snapshot(other)

        mockedChannel(target, beforeInfo = { throw IOException("offline") }).use {
            assertTrue(refresh(target).single().isOnError)
        }

        assertEquals(listOf("cached"), titles(target))
        assertEquals(mapOf(1 to null, 2 to null), updated(target))
        assertEquals(before, snapshot(other))
        assertTrue(subscriptions.getSubscriptionForProfile(profile, target.uid)!!.feedMuted)
    }

    @Test
    fun partialTabFailurePreservesCachedLiveItemsAndDoesNotMarkTheRefreshComplete() {
        val target = channel("Target", muted = true)
        cache(target, "live cached", now.minusDays(1), StreamType.LIVE_STREAM)
        mockedChannel(target, listOf(videos, shorts), failedTab = shorts).use {
            val result = refresh(target).single()
            assertTrue(result.isOnNext)
            assertTrue(result.value!!.errors.isNotEmpty())
        }

        assertEquals(setOf("live cached", "videos upload"), titles(target).toSet())
        assertEquals(mapOf(1 to null), updated(target))
        assertTrue(subscriptions.getSubscriptionForProfile(profile, target.uid)!!.feedMuted)
    }

    @Test
    fun cancellationDuringExtractionCannotPersistLateResults() {
        val target = channel("Target", muted = true)
        val manager = FeedLoadManager(context, profile)
        mockedChannel(target, beforeInfo = manager::cancel).use {
            manager.startLoadingSubscription(ChannelFeedRefreshRequest.from(target))
                .test().assertComplete().assertNoErrors()
        }
        assertTrue(feed.getMemberships(target.uid).isEmpty())
        assertTrue(updated(target).isEmpty())
        assertEquals(target, subscriptions.getSubscriptionForProfile(profile, target.uid))
    }

    private fun refresh(target: SubscriptionEntity): List<Notification<FeedUpdateInfo>> = FeedLoadManager(context, target.profileId).startLoadingSubscription(ChannelFeedRefreshRequest.from(target))
        .test().assertComplete().assertNoErrors().values().single()

    private fun channel(
        name: String,
        muted: Boolean = false,
        modes: Int = SubscriptionEntity.YOUTUBE_MODE_REGULAR,
        profileId: String = profile
    ) = SubscriptionEntity(
        serviceId = 0,
        url = "https://example.com/channel/$name",
        name = name,
        profileId = profileId,
        youtubeModeMask = modes,
        feedMuted = muted,
        notificationMode = NotificationMode.KEYWORDS_ONLY,
        notificationKeywords = "science"
    ).apply { uid = subscriptions.insert(this) }

    private fun tab(name: String) = ListLinkHandler(name, name, name, listOf(FilterItem(0, name)), emptyList())

    private fun mockedChannel(
        channel: SubscriptionEntity,
        tabs: List<ListLinkHandler> = listOf(videos),
        beforeInfo: () -> Unit = {},
        failedTab: ListLinkHandler? = null
    ): MockedStatic<ExtractorHelper> = mockStatic(ExtractorHelper::class.java).apply {
        `when`<Single<ChannelInfo>> { ExtractorHelper.getChannelInfo(channel.serviceId, channel.url, true) }
            .thenAnswer {
                Single.fromCallable {
                    beforeInfo()
                    val url = requireNotNull(channel.url)
                    ChannelInfo(
                        channel.serviceId,
                        "fixture",
                        url,
                        url,
                        "Updated ${channel.name}",
                        ListLinkHandler(url, url, "fixture", emptyList(), emptyList())
                    ).apply {
                        this.tabs = tabs
                        description = "Updated metadata"
                        subscriberCount = 123
                    }
                }
            }
        tabs.forEach { tab ->
            val page: Single<ChannelTabInfo> = if (tab == failedTab) {
                Single.error(IOException("tab unavailable"))
            } else {
                Single.just(
                    ChannelTabInfo(channel.serviceId, tab).apply {
                        relatedItems = listOf(
                            StreamInfoItem(
                                channel.serviceId,
                                "${channel.url}/upload/${tab.id}",
                                "${tab.id} upload",
                                StreamType.VIDEO_STREAM
                            ).apply {
                                uploaderName = channel.name
                                uploaderUrl = channel.url
                                uploadDate = DateWrapper(now.minusHours(1))
                            }
                        )
                    }
                )
            }
            `when`<Single<ChannelTabInfo>> { ExtractorHelper.getChannelTab(channel.serviceId, tab, true) }
                .thenReturn(page)
        }
    }

    private fun cache(
        channel: SubscriptionEntity,
        title: String,
        date: OffsetDateTime,
        type: StreamType = StreamType.VIDEO_STREAM
    ) {
        val stream = StreamEntity(
            serviceId = channel.serviceId,
            url = "https://example.com/cached/${channel.uid}/$title",
            title = title,
            streamType = type,
            duration = 60,
            uploader = requireNotNull(channel.name),
            uploaderUrl = channel.url,
            uploadDate = date
        )
        stream.uid = database.streamDAO().insert(stream)
        feed.insert(FeedEntity(stream.uid, channel.uid, firstDiscoveredAt = 1234))
    }

    private fun setUpdated(channel: SubscriptionEntity, mode: Int, time: OffsetDateTime) {
        feed.setLastUpdatedForSubscription(FeedLastUpdatedEntity(channel.uid, mode, time))
    }

    private fun updated(channel: SubscriptionEntity): Map<Int, String?> = database.query(
        "SELECT youtube_mode_mask, last_updated FROM feed_last_updated WHERE subscription_id = ?",
        arrayOf(channel.uid)
    ).use { cursor ->
        buildMap {
            while (cursor.moveToNext()) {
                put(cursor.getInt(0), if (cursor.isNull(1)) null else cursor.getString(1))
            }
        }
    }

    private fun titles(channel: SubscriptionEntity): List<String> = database.query(
        "SELECT DISTINCT s.title FROM streams s INNER JOIN feed f ON f.stream_id = s.uid " +
            "WHERE f.subscription_id = ? ORDER BY s.title",
        arrayOf(channel.uid)
    ).use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
    }

    private fun snapshot(channel: SubscriptionEntity) = Triple(
        subscriptions.getSubscriptionForProfile(channel.profileId, channel.uid),
        feed.getMemberships(channel.uid),
        updated(channel)
    )
}
