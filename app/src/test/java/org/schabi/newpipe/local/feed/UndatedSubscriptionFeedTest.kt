/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.feed

import android.app.Application
import androidx.preference.PreferenceManager
import androidx.room.Room
import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.Schedulers
import java.time.OffsetDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelTabInfo
import org.schabi.newpipe.extractor.feed.FeedExtractor
import org.schabi.newpipe.extractor.linkhandler.ChannelTabs
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.search.filter.FilterItem
import org.schabi.newpipe.extractor.services.youtube.YoutubeService
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.feed.service.FeedLoadManager
import org.schabi.newpipe.local.feed.service.FeedUpdateInfo
import org.schabi.newpipe.local.subscription.SubscriptionManager
import org.schabi.newpipe.plugin.PluginManager
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.ServiceHelper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class UndatedSubscriptionFeedTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val profile = ProfileManager.DEFAULT_PROFILE_ID
    private val plugins = listOf(FixtureService(73421, "XVideos"), FixtureService(84532, "PornHub"))
    private lateinit var database: AppDatabase
    private val subscriptions get() = database.subscriptionDAO()
    private val feed get() = database.feedDAO()
    private val databaseField = NewPipeDatabase::class.java.getDeclaredField("databaseInstance").apply {
        isAccessible = true
    }
    private val pluginProfileField = PluginManager::class.java.getDeclaredField("loadedProfileId").apply {
        isAccessible = true
    }
    private var previousDatabase: Any? = null
    private var previousPluginProfile: Any? = null

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
        synchronized(NewPipeDatabase::class.java) {
            previousDatabase = databaseField.get(null)
            databaseField.set(null, database)
            assertSame(database, NewPipeDatabase.getInstance(context))
        }
        previousPluginProfile = pluginProfileField.get(null)
        pluginProfileField.set(null, profile)
        plugins.forEach { plugin ->
            assertNull(ServiceList.getById(plugin.serviceId))
            ServiceList.register(plugin)
        }
    }

    @After
    fun tearDown() {
        plugins.forEach { ServiceList.unregister(it.serviceId) }
        pluginProfileField.set(null, previousPluginProfile)
        synchronized(NewPipeDatabase::class.java) {
            databaseField.set(null, previousDatabase)
        }
        if (::database.isInitialized) database.close()
        RxJavaPlugins.reset()
        RxAndroidPlugins.reset()
    }

    @Test
    fun initialSubscriptionSeedingShowsUndatedPluginItemsInTheCorrectServiceAndProfile() {
        val seeded = plugins.map { plugin ->
            val info = channelInfo(plugin.serviceId, "seed")
            val items = listOf(stream(info, "first"), stream(info, "second", StreamType.AUDIO_STREAM))
            val tab = ChannelTabInfo(plugin.serviceId, info.tabs.single()).apply { relatedItems = items }
            SubscriptionManager(context, profile).upsertAll(listOf(info to tab))
            Triple(info, items, subscriptions.getSubscriptionDirectForProfile(profile, plugin.serviceId, info.url)!!)
        }
        val foreignInfo = channelInfo(plugins.first().serviceId, "seed")
        val foreignItems = listOf(stream(foreignInfo, "foreign"))
        SubscriptionManager(context, "other-profile").upsertAll(
            listOf(
                foreignInfo to ChannelTabInfo(foreignInfo.serviceId, foreignInfo.tabs.single()).apply {
                    relatedItems = foreignItems
                }
            )
        )

        for ((info, sourceItems, subscription) in seeded) {
            val visible = visible(info.serviceId)
            assertEquals(sourceItems.map { it.url }, visible.map { it.url })
            assertTrue(visible.all { it.serviceId == info.serviceId && it.uploaderUrl == info.url })
            assertTrue(visible.all { it.uploadDate == null && it.isUploadDateApproximation == null })
            assertTrue(sourceItems.all { it.uploadDate == null && it.textualUploadDate == null })
            val memberships = feed.getMemberships(subscription.uid)
            assertEquals(2, memberships.size)
            assertTrue(memberships.all { it.firstDiscoveredAt > 0 })
        }
        assertEquals(4, visible(FeedScope.ALL_SERVICES).size)
        assertEquals(foreignItems.map { it.url }, visible(foreignInfo.serviceId, "other-profile").map { it.url })
        assertTrue(visible(plugins.last().serviceId, "other-profile").isEmpty())
    }

    @Test
    fun bothRefreshModesFallBackToPluginChannelTabsAndDoNotAlertForUndatedHistory() {
        for (fastMode in listOf(false, true)) {
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(context.getString(R.string.feed_use_dedicated_fetch_method_key), fastMode)
                .commit()
            val targets = plugins.map { channel(it.serviceId, "refresh-$fastMode") }
            val alerts = mutableListOf<List<String>>()
            val lookupsBefore = plugins.map { it.feedLookups }
            val manager = manager(alerts)
            mockStatic(ExtractorHelper::class.java).use { extractor ->
                val tabs = targets.associate { target ->
                    target.uid to mockChannel(extractor, target) {
                        listOf(stream(channelInfo(target.serviceId, target.url!!, true), "undated"))
                    }
                }
                val results = refresh(manager, targets)
                assertEquals(targets.map { it.uid }.toSet(), results.map { it.uid }.toSet())
                assertTrue(results.all { it.errors.isEmpty() && it.streams.single().uploadDate == null && it.newStreams.isEmpty() })
                for (target in targets) {
                    extractor.verify({ ExtractorHelper.getChannelInfo(target.serviceId, target.url, true) }, times(1))
                    extractor.verify({ ExtractorHelper.getChannelTab(target.serviceId, tabs.getValue(target.uid), true) }, times(1))
                    val membership = feed.getMemberships(target.uid).single()
                    assertNull(database.streamDAO().getStreamDirect(membership.streamId)!!.uploadDate)
                    assertTrue(visible(target.serviceId).any { it.uid == membership.streamId })
                }
                extractor.verifyNoMoreInteractions()
            }
            assertEquals(2, alerts.size)
            assertTrue(alerts.all { it.isEmpty() })
            assertEquals(lookupsBefore.map { it + if (fastMode) 1 else 0 }, plugins.map { it.feedLookups })
        }
    }

    @Test
    fun repeatedRefreshKeepsDiscoveryOrderAndLaterPublicationMetadataUpgradesWithoutAlerts() {
        val target = channel(plugins.first().serviceId, "repeat")
        val info = channelInfo(target.serviceId, target.url!!, true)
        var items = listOf(stream(info, "first"), stream(info, "second"))
        val originalUrls = items.map { it.url }
        val alerts = mutableListOf<List<String>>()
        mockStatic(ExtractorHelper::class.java).use { extractor ->
            mockChannel(extractor, target) { items }
            assertTrue(refresh(manager(alerts), listOf(target)).single().newStreams.isEmpty())
            assertEquals(originalUrls, visible(target.serviceId).map { it.url })

            // Represent discovery during a previous session, without timing-dependent sleeps.
            database.openHelper.writableDatabase.execSQL(
                "UPDATE feed SET first_discovered_at = first_discovered_at - 86400000 WHERE subscription_id = ?",
                arrayOf(target.uid)
            )
            val originalMemberships = feed.getMemberships(target.uid).sortedBy { it.streamId }
            items = listOf(stream(info, "second"), stream(info, "first"))
            assertTrue(refresh(manager(alerts), listOf(target)).single().newStreams.isEmpty())
            assertEquals(originalMemberships, feed.getMemberships(target.uid).sortedBy { it.streamId })
            assertEquals(originalUrls, visible(target.serviceId).map { it.url })
            assertTrue(visible(target.serviceId).all { it.uploadDate == null })

            val publication = OffsetDateTime.now().minusHours(2).withNano(0)
            items = listOf(
                stream(info, "first").apply {
                    uploadDate = DateWrapper(publication)
                    textualUploadDate = publication.toString()
                },
                stream(info, "second")
            )
            assertTrue(refresh(manager(alerts), listOf(target)).single().newStreams.isEmpty())
            val upgraded = database.streamDAO().getStreamDirect(target.serviceId, originalUrls.first())!!
            assertEquals(publication.toInstant(), upgraded.uploadDate!!.toInstant())
            assertEquals(publication.toString(), upgraded.textualUploadDate)
            assertFalse(upgraded.isUploadDateApproximation!!)

            items = listOf(stream(info, "first"), stream(info, "second"))
            assertTrue(refresh(manager(alerts), listOf(target)).single().newStreams.isEmpty())
            assertTrue(items.all { it.uploadDate == null })
            val preserved = database.streamDAO().getStreamDirect(target.serviceId, originalUrls.first())!!
            assertEquals(publication.toInstant(), preserved.uploadDate!!.toInstant())
            assertEquals(publication.toString(), preserved.textualUploadDate)
            assertEquals(originalMemberships, feed.getMemberships(target.uid).sortedBy { it.streamId })
        }
        assertEquals(4, alerts.size)
        assertTrue(alerts.all { it.isEmpty() })
    }

    private fun manager(alerts: MutableList<List<String>>) = FeedLoadManager(context, profile) { _, update, _ ->
        alerts += update.newStreams.map { it.url }
        null
    }

    private fun refresh(manager: FeedLoadManager, targets: List<SubscriptionEntity>): List<FeedUpdateInfo> = manager.startLoading(
        ignoreOutdatedThreshold = true,
        onlySubscriptions = targets.map { it.uid }.toSet()
    ).test().assertComplete().assertNoErrors().values().single().map { result ->
        assertTrue(result.error?.toString().orEmpty(), result.isOnNext)
        requireNotNull(result.value)
    }

    private fun channel(serviceId: Int, slug: String): SubscriptionEntity = SubscriptionEntity.from(channelInfo(serviceId, slug)).apply {
        profileId = profile
        notificationMode = NotificationMode.ENABLED
        uid = subscriptions.insert(this)
    }

    private fun channelInfo(serviceId: Int, value: String, isUrl: Boolean = false): ChannelInfo {
        val url = if (isUrl) value else "https://$serviceId.example.org/channels/$value"
        val handler = ListLinkHandler(url, url, value, emptyList(), emptyList())
        return ChannelInfo(serviceId, value, url, url, "Fixture creator", handler).apply {
            tabs = listOf(ListLinkHandler("$url/videos", "$url/videos", value, listOf(FilterItem(0, ChannelTabs.VIDEOS)), emptyList()))
            description = "Neutral subscription fixture"
        }
    }

    private fun stream(info: ChannelInfo, id: String, type: StreamType = StreamType.VIDEO_STREAM) = StreamInfoItem(
        info.serviceId,
        "${info.url}/upload/$id",
        "Fixture $id",
        type
    ).apply {
        uploaderName = info.name
        uploaderUrl = info.url
        duration = 60
    }

    private fun mockChannel(
        extractor: MockedStatic<ExtractorHelper>,
        channel: SubscriptionEntity,
        items: () -> List<StreamInfoItem>
    ): ListLinkHandler {
        val info = channelInfo(channel.serviceId, channel.url!!, true)
        val tab = info.tabs.single()
        extractor.`when`<Single<ChannelInfo>> { ExtractorHelper.getChannelInfo(channel.serviceId, channel.url, true) }
            .thenReturn(Single.just(info))
        extractor.`when`<Single<ChannelTabInfo>> { ExtractorHelper.getChannelTab(channel.serviceId, tab, true) }
            .thenReturn(Single.fromCallable { ChannelTabInfo(channel.serviceId, tab).apply { relatedItems = items() } })
        return tab
    }

    private fun visible(serviceId: Int, profileId: String = profile): List<StreamEntity> = FeedDatabaseManager(context, profileId).getStreams(
        FeedGroupEntity.GROUP_ALL_ID,
        includePlayedStreams = true,
        includePartiallyPlayedStreams = true,
        includeFutureStreams = false,
        scope = FeedScope(serviceId, SubscriptionEntity.YOUTUBE_MODE_REGULAR)
    ).blockingGet()!!.map { it.stream }

    /** Dynamic registry IDs exercise host policy; extraction is supplied by neutral channel fixtures. */
    private class FixtureService(id: Int, private val fixtureName: String) : YoutubeService(id) {
        var feedLookups = 0
            private set

        override fun getServiceInfo(): StreamingService.ServiceInfo = StreamingService.ServiceInfo(fixtureName, emptyList())
        override fun getBaseUrl(): String = "https://$serviceId.example.org"
        override fun getFeedExtractor(channelUrl: String): FeedExtractor? {
            feedLookups++
            return null
        }
    }
}
