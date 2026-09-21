/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.libp2p.core.crypto.KeyType
import io.libp2p.core.crypto.generateKeyPair
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.local.subscription.FeedGroupIcon
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.settings.tabs.Tab
import org.schabi.newpipe.settings.tabs.TabsJsonHelper

@RunWith(AndroidJUnit4::class)
class RoomStructuredPreferenceSyncStoreTest {
    private lateinit var context: Context
    private lateinit var phoneDatabase: AppDatabase
    private lateinit var tabletDatabase: AppDatabase
    private lateinit var phonePreferences: SharedPreferences
    private lateinit var tabletPreferences: SharedPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        phoneDatabase = newDatabase()
        tabletDatabase = newDatabase()
        phonePreferences = context.getSharedPreferences(
            "structured-preferences-phone",
            Context.MODE_PRIVATE
        )
        tabletPreferences = context.getSharedPreferences(
            "structured-preferences-tablet",
            Context.MODE_PRIVATE
        )
        phonePreferences.edit().clear().commit()
        tabletPreferences.edit().clear().commit()
    }

    @After
    fun tearDown() {
        phoneDatabase.close()
        tabletDatabase.close()
        phonePreferences.edit().clear().commit()
        tabletPreferences.edit().clear().commit()
    }

    @Test
    fun feedGroupsWithSameIdentityStayIsolatedAcrossProfiles() {
        val workProfile = "11111111-1111-1111-1111-111111111111"
        seedProfileFeedGroup(
            phoneDatabase,
            ProfileManager.DEFAULT_PROFILE_ID,
            "https://example.com/channel/shared",
            "News"
        )
        seedProfileFeedGroup(
            phoneDatabase,
            workProfile,
            "https://example.com/channel/shared",
            "News"
        )
        seedProfileSubscription(
            tabletDatabase,
            ProfileManager.DEFAULT_PROFILE_ID,
            "https://example.com/channel/shared"
        )
        seedProfileSubscription(
            tabletDatabase,
            workProfile,
            "https://example.com/channel/shared"
        )

        val phoneStore = RoomStructuredPreferenceSyncStore(
            context,
            phoneDatabase,
            newPeerId(),
            phonePreferences,
            canMaterializeProfile = { true }
        )
        val tabletStore = RoomStructuredPreferenceSyncStore(
            context,
            tabletDatabase,
            newPeerId(),
            tabletPreferences,
            canMaterializeProfile = { true }
        )
        val phone = StructuredPreferenceSyncEngine(phoneStore)
        val tablet = StructuredPreferenceSyncEngine(tabletStore)

        synchronize(
            StructuredPreferenceCategory.FEED_GROUPS,
            phone,
            phoneStore,
            tablet,
            tabletStore
        )

        assertEquals(
            setOf(ProfileManager.DEFAULT_PROFILE_ID, workProfile),
            tabletDatabase.feedGroupDAO().getAllDirect().mapTo(mutableSetOf()) {
                it.profileId
            }
        )
        assertProfileGroupMembership(
            tabletDatabase,
            ProfileManager.DEFAULT_PROFILE_ID,
            "https://example.com/channel/shared"
        )
        assertProfileGroupMembership(
            tabletDatabase,
            workProfile,
            "https://example.com/channel/shared"
        )

        phoneDatabase.feedGroupDAO().deleteAllForProfile(ProfileManager.DEFAULT_PROFILE_ID)
        synchronize(
            StructuredPreferenceCategory.FEED_GROUPS,
            phone,
            phoneStore,
            tablet,
            tabletStore
        )

        assertTrue(
            tabletDatabase.feedGroupDAO()
                .getAllDirectForProfile(ProfileManager.DEFAULT_PROFILE_ID)
                .isEmpty()
        )
        assertEquals(
            1,
            tabletDatabase.feedGroupDAO().getAllDirectForProfile(workProfile).size
        )
        assertProfileGroupMembership(
            tabletDatabase,
            workProfile,
            "https://example.com/channel/shared"
        )
    }

    @Test
    fun structuredPreferenceCategoriesConvergeThroughRoomStores() {
        seedFeedGroups(phoneDatabase, PHONE_CHANNEL_URL)
        seedFeedGroups(tabletDatabase, TABLET_CHANNEL_URL)
        val phoneTabs = listOf(Tab.Type.FEED.tab, Tab.Type.SUBSCRIPTIONS.tab)
        val tabletTabs = listOf(Tab.Type.SUBSCRIPTIONS.tab, Tab.Type.FEED.tab)
        val savedTabsKey = context.getString(R.string.saved_tabs_key)
        phonePreferences.edit()
            .putString(savedTabsKey, TabsJsonHelper.getJsonToSave(phoneTabs))
            .putFloat(PROFILE_KEY + SPEED_SUFFIX, 1.5F)
            .putStringSet(
                context.getString(R.string.show_search_suggestions_key),
                setOf(context.getString(R.string.show_local_search_suggestions_key))
            )
            .commit()
        tabletPreferences.edit()
            .putString(savedTabsKey, TabsJsonHelper.getJsonToSave(tabletTabs))
            .putString(PROFILE_KEY + CAPTION_SUFFIX, "en")
            .putStringSet(
                context.getString(R.string.show_search_suggestions_key),
                setOf(context.getString(R.string.show_remote_search_suggestions_key))
            )
            .commit()

        val phoneStore = RoomStructuredPreferenceSyncStore(
            context,
            phoneDatabase,
            newPeerId(),
            phonePreferences
        )
        val tabletStore = RoomStructuredPreferenceSyncStore(
            context,
            tabletDatabase,
            newPeerId(),
            tabletPreferences
        )
        val phone = StructuredPreferenceSyncEngine(phoneStore)
        val tablet = StructuredPreferenceSyncEngine(tabletStore)

        StructuredPreferenceCategory.entries.forEach { category ->
            synchronize(category, phone, phoneStore, tablet, tabletStore)
        }

        assertEquals(
            setOf(PHONE_CHANNEL_URL, TABLET_CHANNEL_URL),
            feedGroupSubscriptionUrls(phoneDatabase)
        )
        assertEquals(
            feedGroupSubscriptionUrls(phoneDatabase),
            feedGroupSubscriptionUrls(tabletDatabase)
        )
        assertEquals(
            phonePreferences.getString(savedTabsKey, null),
            tabletPreferences.getString(savedTabsKey, null)
        )
        assertEquals(
            1.5F,
            tabletPreferences.getFloat(PROFILE_KEY + SPEED_SUFFIX, 0F)
        )
        assertEquals(
            "en",
            phonePreferences.getString(PROFILE_KEY + CAPTION_SUFFIX, null)
        )
        assertEquals(
            phonePreferences.getStringSet(
                context.getString(R.string.show_search_suggestions_key),
                emptySet()
            ),
            tabletPreferences.getStringSet(
                context.getString(R.string.show_search_suggestions_key),
                emptySet()
            )
        )
    }

    @Test
    fun blockedContentEntriesMergeAndMaterializeThroughRoomStores() {
        val blockedChannelsKey = context.getString(R.string.blocked_channels_key)
        val blockedVideosKey = context.getString(R.string.blocked_videos_key)
        val blockedKeywordsKey = context.getString(R.string.blocked_keywords_key)
        val phoneChannel = "https://example.com/channel/blocked\tBlocked channel"
        val tabletVideo = "https://example.com/watch?v=blocked\tBlocked video"

        phonePreferences.edit()
            .putStringSet(blockedChannelsKey, setOf(phoneChannel))
            .commit()
        tabletPreferences.edit()
            .putStringSet(blockedVideosKey, setOf(tabletVideo))
            .putString(blockedKeywordsKey, "Spoiler")
            .commit()

        val phoneStore = RoomStructuredPreferenceSyncStore(
            context,
            phoneDatabase,
            newPeerId(),
            phonePreferences
        )
        val tabletStore = RoomStructuredPreferenceSyncStore(
            context,
            tabletDatabase,
            newPeerId(),
            tabletPreferences
        )
        val phone = StructuredPreferenceSyncEngine(phoneStore)
        val tablet = StructuredPreferenceSyncEngine(tabletStore)

        synchronize(
            StructuredPreferenceCategory.CONTENT_BLOCKING,
            phone,
            phoneStore,
            tablet,
            tabletStore
        )

        assertEquals(
            setOf(phoneChannel),
            phonePreferences.getStringSet(blockedChannelsKey, emptySet())
        )
        assertEquals(
            setOf(phoneChannel),
            tabletPreferences.getStringSet(blockedChannelsKey, emptySet())
        )
        assertEquals(
            setOf(tabletVideo),
            phonePreferences.getStringSet(blockedVideosKey, emptySet())
        )
        assertEquals(
            setOf(tabletVideo),
            tabletPreferences.getStringSet(blockedVideosKey, emptySet())
        )
        assertEquals("Spoiler", phonePreferences.getString(blockedKeywordsKey, null))
        assertEquals("Spoiler", tabletPreferences.getString(blockedKeywordsKey, null))

        phonePreferences.edit().putStringSet(blockedChannelsKey, emptySet()).commit()
        synchronize(
            StructuredPreferenceCategory.CONTENT_BLOCKING,
            phone,
            phoneStore,
            tablet,
            tabletStore
        )

        assertTrue(phonePreferences.getStringSet(blockedChannelsKey, emptySet()).isNullOrEmpty())
        assertTrue(tabletPreferences.getStringSet(blockedChannelsKey, emptySet()).isNullOrEmpty())
        assertEquals(
            setOf(tabletVideo),
            phonePreferences.getStringSet(blockedVideosKey, emptySet())
        )
        assertEquals("Spoiler", phonePreferences.getString(blockedKeywordsKey, null))
    }

    @Test
    fun portableSettingsUseClosedAllowlistAndKeepLocalOnlyValues() {
        val themeKey = context.getString(R.string.theme_key)
        val downloadPathKey = context.getString(R.string.download_path_video_key)
        val safKey = context.getString(R.string.storage_use_saf)
        val decoderKey = context.getString(R.string.use_exoplayer_decoder_fallback_key)
        val notificationKey = context.getString(R.string.enable_streams_notifications)
        val searchPrivacyKey = context.getString(R.string.device_sync_search_history_key)
        val learningModeKey = context.getString(R.string.learning_mode_key)
        val learningBackgroundKey = context.getString(R.string.learning_count_background_key)
        phonePreferences.edit()
            .putString(themeKey, "dark_theme")
            .putString(downloadPathKey, "content://phone/private/downloads")
            .putBoolean(safKey, true)
            .putBoolean(decoderKey, true)
            .putBoolean(notificationKey, true)
            .putBoolean(searchPrivacyKey, true)
            .putBoolean(learningModeKey, true)
            .putBoolean(learningBackgroundKey, false)
            .commit()
        tabletPreferences.edit()
            .putString(downloadPathKey, "content://tablet/private/downloads")
            .putBoolean(safKey, false)
            .putBoolean(decoderKey, false)
            .putBoolean(notificationKey, false)
            .putBoolean(searchPrivacyKey, false)
            .putBoolean(learningModeKey, false)
            .putBoolean(learningBackgroundKey, true)
            .commit()
        val phoneStore = RoomStructuredPreferenceSyncStore(
            context,
            phoneDatabase,
            newPeerId(),
            phonePreferences
        )
        val tabletStore = RoomStructuredPreferenceSyncStore(
            context,
            tabletDatabase,
            newPeerId(),
            tabletPreferences
        )
        val phone = StructuredPreferenceSyncEngine(phoneStore)
        val tablet = StructuredPreferenceSyncEngine(tabletStore)

        val request = phone.createRequest(
            tabletStore.localPeerId,
            StructuredPreferenceCategory.SETTINGS
        )
        assertEquals(
            listOf(
                PortableSettingId.THEME,
                PortableSettingId.LEARNING_MODE,
                PortableSettingId.LEARNING_COUNT_BACKGROUND
            ),
            request.changes.mapNotNull {
                it.record?.portableSetting?.settingId
            }
        )

        synchronize(
            StructuredPreferenceCategory.SETTINGS,
            phone,
            phoneStore,
            tablet,
            tabletStore
        )

        assertEquals("dark_theme", tabletPreferences.getString(themeKey, null))
        assertEquals(
            "content://tablet/private/downloads",
            tabletPreferences.getString(downloadPathKey, null)
        )
        assertEquals(false, tabletPreferences.getBoolean(safKey, true))
        assertEquals(false, tabletPreferences.getBoolean(decoderKey, true))
        assertEquals(false, tabletPreferences.getBoolean(notificationKey, true))
        assertEquals(false, tabletPreferences.getBoolean(searchPrivacyKey, true))
        assertEquals(true, tabletPreferences.getBoolean(learningModeKey, false))
        assertEquals(false, tabletPreferences.getBoolean(learningBackgroundKey, true))
    }

    @Test
    fun recordRepositoryKeepsRevisionAndLamportBookkeepingCategoryIndependent() {
        val localPeerId = newPeerId()
        val remotePeerId = newPeerId()
        val repository = StructuredPreferenceRecordRepository(phoneDatabase, localPeerId)
        val category = StructuredPreferenceCategory.FILTERS
        val recordId = StructuredPreferenceRecordId.filterSet(
            StructuredFilterId.SEARCH_SUGGESTIONS
        )
        val localRecord = SyncedStructuredPreferenceRecord(
            filterSet = SyncedFilterSet(
                StructuredFilterId.SEARCH_SUGGESTIONS,
                listOf("local")
            )
        )
        repository.saveLocalUpsert(
            category = category,
            recordId = recordId,
            recordType = StructuredPreferenceRecordType.FILTER_SET,
            record = localRecord
        )

        assertEquals(mapOf(localPeerId to 1L), repository.getKnownRevisions(category))
        assertEquals(
            listOf(localRecord),
            repository.getPendingChanges(category, remotePeerId, 8)
                .changes
                .map(StructuredPreferenceChange::record)
        )
        repository.acknowledgePeer(category, remotePeerId, mapOf(localPeerId to 100L))
        assertTrue(repository.getPendingChanges(category, remotePeerId, 8).changes.isEmpty())

        val newerRemoteRecord = SyncedStructuredPreferenceRecord(
            filterSet = SyncedFilterSet(
                StructuredFilterId.SEARCH_SUGGESTIONS,
                listOf("remote")
            )
        )
        val remoteRevisionTwo = filterChange(
            category = category,
            originPeerId = remotePeerId,
            originRevision = 2,
            lamportVersion = 3,
            recordId = recordId,
            record = newerRemoteRecord
        )
        var materializations = 0
        assertEquals(
            StructuredPreferenceApplyResult(1, 1),
            repository.applyChanges(category, listOf(remoteRevisionTwo)) {
                materializations += 1
            }
        )
        assertEquals(0L, repository.getKnownRevisions(category)[remotePeerId])

        val remoteRevisionOne = filterChange(
            category = category,
            originPeerId = remotePeerId,
            originRevision = 1,
            lamportVersion = 2,
            recordId = recordId,
            record = localRecord
        )
        assertEquals(
            StructuredPreferenceApplyResult(1, 0),
            repository.applyChanges(category, listOf(remoteRevisionOne)) {
                materializations += 1
            }
        )
        assertEquals(2L, repository.getKnownRevisions(category)[remotePeerId])
        assertEquals(1, materializations)
        assertEquals(
            newerRemoteRecord,
            repository.getRecord(category, recordId)?.let(repository::decodeRecord)
        )
        assertEquals(
            StructuredPreferenceApplyResult(0, 0),
            repository.applyChanges(category, listOf(remoteRevisionTwo)) {
                materializations += 1
            }
        )
        assertEquals(1, materializations)
    }

    private fun filterChange(
        category: StructuredPreferenceCategory,
        originPeerId: String,
        originRevision: Long,
        lamportVersion: Long,
        recordId: String,
        record: SyncedStructuredPreferenceRecord
    ): StructuredPreferenceChange {
        return StructuredPreferenceChange(
            category = category,
            originPeerId = originPeerId,
            originRevision = originRevision,
            lamportVersion = lamportVersion,
            recordId = recordId,
            recordType = StructuredPreferenceRecordType.FILTER_SET,
            type = StructuredPreferenceChangeType.UPSERT,
            record = record
        )
    }

    private fun seedProfileSubscription(
        database: AppDatabase,
        profileId: String,
        url: String
    ) {
        database.subscriptionDAO().upsertAll(
            listOf(
                SubscriptionEntity(
                    serviceId = SERVICE_ID,
                    url = url,
                    name = profileId,
                    profileId = profileId
                )
            )
        )
    }

    private fun seedProfileFeedGroup(
        database: AppDatabase,
        profileId: String,
        memberUrl: String,
        name: String
    ) {
        seedProfileSubscription(database, profileId, memberUrl)
        val groupId = database.feedGroupDAO().insert(
            FeedGroupEntity(
                uid = 0,
                name = name,
                icon = FeedGroupIcon.NEWS,
                profileId = profileId
            )
        )
        val memberId = requireNotNull(
            database.subscriptionDAO().getSubscriptionDirectForProfile(
                profileId,
                SERVICE_ID,
                memberUrl
            )
        ).uid
        database.feedGroupDAO().updateSubscriptionsForGroupForProfile(
            profileId,
            groupId,
            listOf(memberId)
        )
    }

    private fun assertProfileGroupMembership(
        database: AppDatabase,
        profileId: String,
        expectedUrl: String
    ) {
        val group = database.feedGroupDAO().getAllDirectForProfile(profileId).single()
        val subscriptions = database.subscriptionDAO()
            .getAllDirectForProfile(profileId)
            .associateBy { it.uid }
        val urls = database.feedGroupDAO()
            .getSubscriptionIdsForDirectForProfile(profileId, group.uid)
            .mapNotNull(subscriptions::get)
            .mapNotNull(SubscriptionEntity::url)
            .toSet()
        assertEquals(setOf(expectedUrl), urls)
    }

    private fun seedFeedGroups(database: AppDatabase, memberUrl: String) {
        val phone = SubscriptionEntity(
            serviceId = SERVICE_ID,
            url = PHONE_CHANNEL_URL,
            name = "Phone"
        )
        val tablet = SubscriptionEntity(
            serviceId = SERVICE_ID,
            url = TABLET_CHANNEL_URL,
            name = "Tablet"
        )
        database.subscriptionDAO().upsertAll(listOf(phone, tablet))
        val groupId = database.feedGroupDAO().insert(
            FeedGroupEntity(
                uid = 0,
                name = "News",
                icon = FeedGroupIcon.NEWS
            )
        )
        val memberId = database.subscriptionDAO()
            .getSubscriptionDirect(SERVICE_ID, memberUrl)
            ?.uid
        database.feedGroupDAO().updateSubscriptionsForGroup(
            groupId,
            listOf(requireNotNull(memberId))
        )
    }

    private fun feedGroupSubscriptionUrls(database: AppDatabase): Set<String> {
        val group = database.feedGroupDAO().getAllDirect().single()
        val subscriptions = database.subscriptionDAO().getAllDirect().associateBy { it.uid }
        return database.feedGroupDAO().getSubscriptionIdsForDirect(group.uid)
            .mapNotNull(subscriptions::get)
            .mapNotNull(SubscriptionEntity::url)
            .toSet()
    }

    private fun synchronize(
        category: StructuredPreferenceCategory,
        initiator: StructuredPreferenceSyncEngine,
        initiatorStore: StructuredPreferenceSyncStore,
        responder: StructuredPreferenceSyncEngine,
        responderStore: StructuredPreferenceSyncStore
    ) {
        var rounds = 0
        while (true) {
            rounds += 1
            assertTrue(rounds < 100)
            val request = initiator.createRequest(
                responderStore.localPeerId,
                category
            )
            val response = responder.handleRequest(
                initiatorStore.localPeerId,
                request
            )
            initiator.handleResponse(
                responderStore.localPeerId,
                category,
                response
            )
            if (!request.hasMore && !response.hasMore) {
                return
            }
        }
    }

    private fun newDatabase(): AppDatabase {
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    private fun newPeerId(): String {
        val privateKey = generateKeyPair(KeyType.ED25519).first
        return DeviceIdentity(privateKey).peerId.toBase58()
    }

    companion object {
        private const val SERVICE_ID = 0
        private const val PHONE_CHANNEL_URL = "https://example.com/channel/phone"
        private const val TABLET_CHANNEL_URL = "https://example.com/channel/tablet"
        private const val SPEED_SUFFIX = ".speed"
        private const val CAPTION_SUFFIX = ".caption"
        private val PROFILE_KEY = CHANNEL_PROFILE_PREFIX + SERVICE_ID + "." + "b".repeat(64)
    }
}
