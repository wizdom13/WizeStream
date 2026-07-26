/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import io.libp2p.core.crypto.KeyType
import io.libp2p.core.crypto.generateKeyPair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuredPreferenceSyncEngineTest {
    @Test
    fun `feed group membership edits merge and deletion tombstones converge`() {
        val phoneStore = newStore()
        val tabletStore = newStore()
        val phone = StructuredPreferenceSyncEngine(phoneStore)
        val tablet = StructuredPreferenceSyncEngine(tabletStore)
        val groupId = StructuredPreferenceRecordId.initialFeedGroup("News", 8, 0)
        val group = SyncedStructuredPreferenceRecord(
            feedGroup = SyncedFeedGroup("News", 8)
        )
        phoneStore.upsert(
            StructuredPreferenceCategory.FEED_GROUPS,
            groupId,
            StructuredPreferenceRecordType.FEED_GROUP,
            group
        )
        tabletStore.upsert(
            StructuredPreferenceCategory.FEED_GROUPS,
            groupId,
            StructuredPreferenceRecordType.FEED_GROUP,
            group
        )
        val phoneMembership = membership(groupId, PHONE_CHANNEL_URL)
        val tabletMembership = membership(groupId, TABLET_CHANNEL_URL)
        phoneStore.upsertMembership(phoneMembership)
        tabletStore.upsertMembership(tabletMembership)

        synchronize(
            StructuredPreferenceCategory.FEED_GROUPS,
            phone,
            phoneStore,
            tablet,
            tabletStore
        )

        assertEquals(
            setOf(PHONE_CHANNEL_URL, TABLET_CHANNEL_URL),
            phoneStore.membershipUrls()
        )
        assertEquals(phoneStore.membershipUrls(), tabletStore.membershipUrls())

        val phoneMembershipId = StructuredPreferenceRecordId.feedGroupMembership(
            groupId,
            SERVICE_ID,
            PHONE_CHANNEL_URL
        )
        phoneStore.delete(
            StructuredPreferenceCategory.FEED_GROUPS,
            phoneMembershipId
        )
        synchronize(
            StructuredPreferenceCategory.FEED_GROUPS,
            phone,
            phoneStore,
            tablet,
            tabletStore
        )

        assertEquals(setOf(TABLET_CHANNEL_URL), phoneStore.membershipUrls())
        assertEquals(phoneStore.membershipUrls(), tabletStore.membershipUrls())
    }

    @Test
    fun `independent channel profile fields merge without replacing each other`() {
        val phoneStore = newStore()
        val tabletStore = newStore()
        val phone = StructuredPreferenceSyncEngine(phoneStore)
        val tablet = StructuredPreferenceSyncEngine(tabletStore)
        val speed = SyncedChannelProfileField(
            profileKey = PROFILE_KEY,
            field = ChannelProfileField.SPEED,
            speed = 1.5F
        )
        val caption = SyncedChannelProfileField(
            profileKey = PROFILE_KEY,
            field = ChannelProfileField.CAPTION,
            textValue = "en"
        )
        phoneStore.upsertProfile(speed)
        tabletStore.upsertProfile(caption)

        synchronize(
            StructuredPreferenceCategory.CHANNEL_PROFILES,
            phone,
            phoneStore,
            tablet,
            tabletStore
        )

        assertEquals(
            setOf(ChannelProfileField.SPEED, ChannelProfileField.CAPTION),
            phoneStore.profileFields()
        )
        assertEquals(phoneStore.profileFields(), tabletStore.profileFields())
    }

    @Test
    fun `home tab ordering uses the latest deterministic version`() {
        val phoneStore = newStore()
        val tabletStore = newStore()
        val phone = StructuredPreferenceSyncEngine(phoneStore)
        val tablet = StructuredPreferenceSyncEngine(tabletStore)
        val feed = SyncedHomeTab(SyncedHomeTabType.FEED)
        val subscriptions = SyncedHomeTab(SyncedHomeTabType.SUBSCRIPTIONS)
        phoneStore.upsertHomeTabs(listOf(feed, subscriptions))
        tabletStore.upsertHomeTabs(listOf(subscriptions, feed))

        synchronize(
            StructuredPreferenceCategory.HOME_TABS,
            phone,
            phoneStore,
            tablet,
            tabletStore
        )

        assertEquals(phoneStore.homeTabOrder(), tabletStore.homeTabOrder())
        assertEquals(2, phoneStore.homeTabOrder().size)
    }

    @Test
    fun `categories batch and acknowledge independently`() {
        val phoneStore = newStore()
        val tabletStore = newStore()
        val phone = StructuredPreferenceSyncEngine(phoneStore)
        val tablet = StructuredPreferenceSyncEngine(tabletStore)
        repeat(MAX_STRUCTURED_PREFERENCE_CHANGES_PER_BATCH + 3) { index ->
            val profile = SyncedChannelProfileField(
                profileKey = profileKey(index),
                field = ChannelProfileField.SPEED,
                speed = 1.25F
            )
            phoneStore.upsertProfile(profile)
        }
        phoneStore.upsertFilter(
            SyncedFilterSet(
                StructuredFilterId.SEARCH_SUGGESTIONS,
                listOf("show_local_search_suggestions")
            )
        )

        val rounds = synchronize(
            StructuredPreferenceCategory.CHANNEL_PROFILES,
            phone,
            phoneStore,
            tablet,
            tabletStore
        )

        assertTrue(rounds >= 2)
        assertEquals(
            MAX_STRUCTURED_PREFERENCE_CHANGES_PER_BATCH + 3,
            tabletStore.profileRecordCount()
        )
        assertTrue(
            tabletStore.liveRecords(
                StructuredPreferenceCategory.FILTERS,
                StructuredPreferenceRecordType.FILTER_SET
            ).isEmpty()
        )

        synchronize(
            StructuredPreferenceCategory.FILTERS,
            phone,
            phoneStore,
            tablet,
            tabletStore
        )

        val repeatRequest = phone.createRequest(
            tabletStore.localPeerId,
            StructuredPreferenceCategory.CHANNEL_PROFILES
        )
        assertTrue(repeatRequest.changes.isEmpty())
        assertFalse(repeatRequest.hasMore)
    }

    @Test
    fun `portable settings merge by semantic identity and latest update wins`() {
        val phoneStore = newStore()
        val tabletStore = newStore()
        val phone = StructuredPreferenceSyncEngine(phoneStore)
        val tablet = StructuredPreferenceSyncEngine(tabletStore)
        phoneStore.upsertSetting(
            SyncedPortableSetting(
                PortableSettingId.THEME,
                stringValue = "dark_theme"
            )
        )
        tabletStore.upsertSetting(
            SyncedPortableSetting(
                PortableSettingId.DEFAULT_RESOLUTION,
                stringValue = "1080p"
            )
        )

        synchronize(
            StructuredPreferenceCategory.SETTINGS,
            phone,
            phoneStore,
            tablet,
            tabletStore
        )

        assertEquals(
            setOf(
                PortableSettingId.THEME,
                PortableSettingId.DEFAULT_RESOLUTION
            ),
            phoneStore.settingIds()
        )
        assertEquals(phoneStore.settingIds(), tabletStore.settingIds())

        tabletStore.upsertSetting(
            SyncedPortableSetting(
                PortableSettingId.THEME,
                stringValue = "black_theme"
            )
        )
        synchronize(
            StructuredPreferenceCategory.SETTINGS,
            phone,
            phoneStore,
            tablet,
            tabletStore
        )

        assertEquals("black_theme", phoneStore.setting(PortableSettingId.THEME).stringValue)
        assertEquals(
            phoneStore.setting(PortableSettingId.THEME),
            tabletStore.setting(PortableSettingId.THEME)
        )
    }

    @Test
    fun `completed download metadata unions and deletion tombstones converge`() {
        val phoneStore = newStore()
        val tabletStore = newStore()
        val phone = StructuredPreferenceSyncEngine(phoneStore)
        val tablet = StructuredPreferenceSyncEngine(tabletStore)
        val phoneDownload = completedDownload(
            PHONE_DOWNLOAD_ID,
            phoneStore.localPeerId,
            "phone.mp4"
        )
        val tabletDownload = completedDownload(
            TABLET_DOWNLOAD_ID,
            tabletStore.localPeerId,
            "tablet.webm"
        )
        phoneStore.upsertDownload(phoneDownload)
        tabletStore.upsertDownload(tabletDownload)

        synchronize(
            StructuredPreferenceCategory.COMPLETED_DOWNLOADS,
            phone,
            phoneStore,
            tablet,
            tabletStore
        )

        assertEquals(
            setOf(PHONE_DOWNLOAD_ID, TABLET_DOWNLOAD_ID),
            phoneStore.downloadIds()
        )
        assertEquals(phoneStore.downloadIds(), tabletStore.downloadIds())

        phoneStore.delete(
            StructuredPreferenceCategory.COMPLETED_DOWNLOADS,
            PHONE_DOWNLOAD_ID
        )
        synchronize(
            StructuredPreferenceCategory.COMPLETED_DOWNLOADS,
            phone,
            phoneStore,
            tablet,
            tabletStore
        )

        assertEquals(setOf(TABLET_DOWNLOAD_ID), phoneStore.downloadIds())
        assertEquals(phoneStore.downloadIds(), tabletStore.downloadIds())
    }

    private fun TestStructuredPreferenceSyncStore.upsertMembership(
        membership: SyncedFeedGroupMembership
    ) {
        upsert(
            category = StructuredPreferenceCategory.FEED_GROUPS,
            recordId = StructuredPreferenceRecordId.feedGroupMembership(
                membership.groupRecordId,
                membership.serviceId,
                membership.subscriptionUrl
            ),
            recordType = StructuredPreferenceRecordType.FEED_GROUP_MEMBERSHIP,
            parentRecordId = membership.groupRecordId,
            record = SyncedStructuredPreferenceRecord(
                feedGroupMembership = membership
            )
        )
    }

    private fun TestStructuredPreferenceSyncStore.membershipUrls(): Set<String> {
        return liveRecords(
            StructuredPreferenceCategory.FEED_GROUPS,
            StructuredPreferenceRecordType.FEED_GROUP_MEMBERSHIP
        ).mapNotNull { it.feedGroupMembership?.subscriptionUrl }.toSet()
    }

    private fun TestStructuredPreferenceSyncStore.upsertProfile(
        field: SyncedChannelProfileField
    ) {
        upsert(
            category = StructuredPreferenceCategory.CHANNEL_PROFILES,
            recordId = StructuredPreferenceRecordId.channelProfileField(field),
            recordType = StructuredPreferenceRecordType.CHANNEL_PROFILE_FIELD,
            record = SyncedStructuredPreferenceRecord(
                channelProfileField = field
            )
        )
    }

    private fun TestStructuredPreferenceSyncStore.profileFields(): Set<ChannelProfileField> {
        return liveRecords(
            StructuredPreferenceCategory.CHANNEL_PROFILES,
            StructuredPreferenceRecordType.CHANNEL_PROFILE_FIELD
        ).mapNotNull { it.channelProfileField?.field }.toSet()
    }

    private fun TestStructuredPreferenceSyncStore.profileRecordCount(): Int {
        return liveRecords(
            StructuredPreferenceCategory.CHANNEL_PROFILES,
            StructuredPreferenceRecordType.CHANNEL_PROFILE_FIELD
        ).size
    }

    private fun TestStructuredPreferenceSyncStore.upsertHomeTabs(
        tabs: List<SyncedHomeTab>
    ) {
        tabs.forEach { tab ->
            upsert(
                category = StructuredPreferenceCategory.HOME_TABS,
                recordId = StructuredPreferenceRecordId.homeTab(tab),
                recordType = StructuredPreferenceRecordType.HOME_TAB,
                record = SyncedStructuredPreferenceRecord(homeTab = tab)
            )
        }
        val order = SyncedHomeTabOrder(
            tabs.map(StructuredPreferenceRecordId::homeTab)
        )
        upsert(
            category = StructuredPreferenceCategory.HOME_TABS,
            recordId = StructuredPreferenceRecordId.homeTabOrder(),
            recordType = StructuredPreferenceRecordType.HOME_TAB_ORDER,
            record = SyncedStructuredPreferenceRecord(homeTabOrder = order)
        )
    }

    private fun TestStructuredPreferenceSyncStore.homeTabOrder(): List<String> {
        return liveRecords(
            StructuredPreferenceCategory.HOME_TABS,
            StructuredPreferenceRecordType.HOME_TAB_ORDER
        ).single().homeTabOrder?.tabRecordIds.orEmpty()
    }

    private fun TestStructuredPreferenceSyncStore.upsertFilter(filter: SyncedFilterSet) {
        upsert(
            category = StructuredPreferenceCategory.FILTERS,
            recordId = StructuredPreferenceRecordId.filterSet(filter.filterId),
            recordType = StructuredPreferenceRecordType.FILTER_SET,
            record = SyncedStructuredPreferenceRecord(filterSet = filter)
        )
    }

    private fun TestStructuredPreferenceSyncStore.upsertSetting(
        setting: SyncedPortableSetting
    ) {
        upsert(
            category = StructuredPreferenceCategory.SETTINGS,
            recordId = StructuredPreferenceRecordId.portableSetting(setting.settingId),
            recordType = StructuredPreferenceRecordType.PORTABLE_SETTING,
            record = SyncedStructuredPreferenceRecord(portableSetting = setting)
        )
    }

    private fun TestStructuredPreferenceSyncStore.settingIds(): Set<PortableSettingId> {
        return liveRecords(
            StructuredPreferenceCategory.SETTINGS,
            StructuredPreferenceRecordType.PORTABLE_SETTING
        ).mapNotNull { it.portableSetting?.settingId }.toSet()
    }

    private fun TestStructuredPreferenceSyncStore.setting(
        id: PortableSettingId
    ): SyncedPortableSetting {
        return liveRecords(
            StructuredPreferenceCategory.SETTINGS,
            StructuredPreferenceRecordType.PORTABLE_SETTING
        ).mapNotNull(SyncedStructuredPreferenceRecord::portableSetting)
            .single { it.settingId == id }
    }

    private fun TestStructuredPreferenceSyncStore.upsertDownload(
        download: SyncedCompletedDownload
    ) {
        upsert(
            category = StructuredPreferenceCategory.COMPLETED_DOWNLOADS,
            recordId = download.syncId,
            recordType = StructuredPreferenceRecordType.COMPLETED_DOWNLOAD,
            record = SyncedStructuredPreferenceRecord(completedDownload = download)
        )
    }

    private fun TestStructuredPreferenceSyncStore.downloadIds(): Set<String> {
        return liveRecords(
            StructuredPreferenceCategory.COMPLETED_DOWNLOADS,
            StructuredPreferenceRecordType.COMPLETED_DOWNLOAD
        ).mapNotNull { it.completedDownload?.syncId }.toSet()
    }

    private fun completedDownload(
        syncId: String,
        ownerPeerId: String,
        displayName: String
    ) = SyncedCompletedDownload(
        syncId = syncId,
        ownerPeerId = ownerPeerId,
        sourceUrl = "https://example.com/watch/$syncId",
        displayName = displayName,
        mimeType = "video/mp4",
        sizeBytes = 1_024,
        completedAtEpochMillis = 1_000,
        mediaKind = "v"
    )

    private fun membership(
        groupId: String,
        url: String
    ) = SyncedFeedGroupMembership(groupId, SERVICE_ID, url)

    private fun synchronize(
        category: StructuredPreferenceCategory,
        initiator: StructuredPreferenceSyncEngine,
        initiatorStore: TestStructuredPreferenceSyncStore,
        responder: StructuredPreferenceSyncEngine,
        responderStore: TestStructuredPreferenceSyncStore
    ): Int {
        var rounds = 0
        while (true) {
            rounds += 1
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
                return rounds
            }
        }
    }

    private fun newStore() = TestStructuredPreferenceSyncStore(newPeerId())

    private fun newPeerId(): String {
        val privateKey = generateKeyPair(KeyType.ED25519).first
        return DeviceIdentity(privateKey).peerId.toBase58()
    }

    private fun profileKey(index: Int): String {
        return CHANNEL_PROFILE_PREFIX + SERVICE_ID + "." +
            index.toString(16).padStart(64, '0')
    }

    companion object {
        private const val SERVICE_ID = 0
        private const val PHONE_CHANNEL_URL = "https://example.com/channel/phone"
        private const val TABLET_CHANNEL_URL = "https://example.com/channel/tablet"
        private const val PHONE_DOWNLOAD_ID = "11111111-1111-4111-8111-111111111111"
        private const val TABLET_DOWNLOAD_ID = "22222222-2222-4222-8222-222222222222"
        private val PROFILE_KEY = CHANNEL_PROFILE_PREFIX + SERVICE_ID + "." + "a".repeat(64)
    }
}
