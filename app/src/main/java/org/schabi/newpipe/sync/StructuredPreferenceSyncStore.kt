/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import java.security.MessageDigest
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.LocalItem.LocalItemType
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.feed.model.FeedGroupSubscriptionEntity
import org.schabi.newpipe.database.sync.StructuredPreferenceSyncFeedGroupMapEntity
import org.schabi.newpipe.database.sync.StructuredPreferenceSyncRecordEntity
import org.schabi.newpipe.local.subscription.FeedGroupIcon
import org.schabi.newpipe.settings.tabs.Tab
import org.schabi.newpipe.settings.tabs.TabsJsonHelper
import org.schabi.newpipe.streams.io.StoredFileHelper
import us.shandian.giga.get.sqlite.FinishedMissionStore

internal interface StructuredPreferenceSyncStore {
    val localPeerId: String

    fun reconcileLocal(category: StructuredPreferenceCategory)

    fun getKnownRevisions(category: StructuredPreferenceCategory): Map<String, Long>

    fun getPendingChanges(
        category: StructuredPreferenceCategory,
        peerId: String,
        limit: Int
    ): StructuredPreferenceChangeBatch

    fun acknowledgePeer(
        category: StructuredPreferenceCategory,
        peerId: String,
        knownRevisions: Map<String, Long>
    )

    fun applyChanges(
        category: StructuredPreferenceCategory,
        changes: List<StructuredPreferenceChange>
    ): StructuredPreferenceApplyResult

    fun clearPeerKnowledge()
}

internal class RoomStructuredPreferenceSyncStore internal constructor(
    private val context: Context,
    private val database: AppDatabase,
    override val localPeerId: String,
    private val preferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context),
    private val finishedMissionStore: FinishedMissionStore = FinishedMissionStore(context)
) : StructuredPreferenceSyncStore {
    private val recordRepository = StructuredPreferenceRecordRepository(database, localPeerId)
    private val feedGroupDao = database.feedGroupDAO()
    private val subscriptionDao = database.subscriptionDAO()
    private val playlistDao = database.playlistDAO()
    private val playlistSyncDao = database.playlistSyncDAO()

    override fun reconcileLocal(category: StructuredPreferenceCategory) {
        database.runInTransaction {
            val currentSnapshot = snapshotHash(category)
            val hasSnapshot = recordRepository.hasSnapshot(category)
            val isCurrent = recordRepository.isSnapshotCurrent(category, currentSnapshot)
            if (isCurrent) {
                return@runInTransaction
            }
            when (category) {
                StructuredPreferenceCategory.FEED_GROUPS ->
                    reconcileFeedGroups(bootstrap = !hasSnapshot)

                StructuredPreferenceCategory.HOME_TABS -> reconcileHomeTabs()

                StructuredPreferenceCategory.CHANNEL_PROFILES ->
                    reconcileChannelProfiles()

                StructuredPreferenceCategory.FILTERS -> reconcileFilters()

                StructuredPreferenceCategory.SETTINGS -> reconcileSettings()

                StructuredPreferenceCategory.COMPLETED_DOWNLOADS ->
                    reconcileCompletedDownloads()
            }
            recordRepository.saveSnapshot(category, snapshotHash(category))
        }
    }

    override fun getKnownRevisions(
        category: StructuredPreferenceCategory
    ): Map<String, Long> {
        return recordRepository.getKnownRevisions(category)
    }

    override fun getPendingChanges(
        category: StructuredPreferenceCategory,
        peerId: String,
        limit: Int
    ): StructuredPreferenceChangeBatch {
        return recordRepository.getPendingChanges(category, peerId, limit)
    }

    override fun acknowledgePeer(
        category: StructuredPreferenceCategory,
        peerId: String,
        knownRevisions: Map<String, Long>
    ) {
        recordRepository.acknowledgePeer(category, peerId, knownRevisions)
    }

    override fun applyChanges(
        category: StructuredPreferenceCategory,
        changes: List<StructuredPreferenceChange>
    ): StructuredPreferenceApplyResult {
        return recordRepository.applyChanges(category, changes) {
            materialize(category)
            recordRepository.saveSnapshot(category, snapshotHash(category))
        }
    }

    override fun clearPeerKnowledge() {
        recordRepository.clearPeerKnowledge()
    }

    internal fun getCompletedDownloadMetadata(): List<SyncedCompletedDownload> {
        return recordRepository.getRecordsByType(
            StructuredPreferenceCategory.COMPLETED_DOWNLOADS,
            StructuredPreferenceRecordType.COMPLETED_DOWNLOAD
        ).filterNot(StructuredPreferenceSyncRecordEntity::isDeleted)
            .mapNotNull { record ->
                recordRepository.decodeRecord(record).completedDownload
            }
            .sortedByDescending(SyncedCompletedDownload::completedAtEpochMillis)
    }

    private fun reconcileFeedGroups(bootstrap: Boolean) {
        val groups = feedGroupDao.getAllDirect()
        if (groups.size > MAX_FEED_GROUPS) {
            throw StructuredPreferenceSyncException(
                "There are too many feed groups to synchronize"
            )
        }
        val duplicateOrdinals = linkedMapOf<Pair<String, Int>, Int>()
        val mappings = groups.associateWith { group ->
            recordRepository.getFeedGroupMapping(group.uid)
                ?: run {
                    val identity = group.name.trim() to group.icon.id
                    val ordinal = duplicateOrdinals[identity] ?: 0
                    duplicateOrdinals[identity] = ordinal + 1
                    val recordId = if (bootstrap) {
                        StructuredPreferenceRecordId.initialFeedGroup(
                            group.name,
                            group.icon.id,
                            ordinal
                        )
                    } else {
                        StructuredPreferenceRecordId.feedGroup()
                    }
                    StructuredPreferenceSyncFeedGroupMapEntity(
                        groupRecordId = recordId,
                        groupUid = group.uid
                    ).also(recordRepository::saveFeedGroupMapping)
                }
        }
        val desiredGroupIds = mappings.values.mapTo(hashSetOf()) {
            it.groupRecordId
        }
        groups.forEach { group ->
            val recordId = requireNotNull(mappings[group]).groupRecordId
            recordRepository.saveLocalUpsert(
                category = StructuredPreferenceCategory.FEED_GROUPS,
                recordId = recordId,
                recordType = StructuredPreferenceRecordType.FEED_GROUP,
                record = SyncedStructuredPreferenceRecord(
                    feedGroup = SyncedFeedGroup(
                        name = group.name.trim().take(MAX_STRUCTURED_NAME_LENGTH),
                        iconId = group.icon.id
                    )
                )
            )
        }
        recordRepository.getRecordsByType(
            StructuredPreferenceCategory.FEED_GROUPS,
            StructuredPreferenceRecordType.FEED_GROUP
        ).filterNot(StructuredPreferenceSyncRecordEntity::isDeleted)
            .filterNot { it.recordId in desiredGroupIds }
            .forEach(recordRepository::saveLocalDelete)

        val subscriptionsById = subscriptionDao.getAllDirect().associateBy { it.uid }
        val desiredMemberships = linkedMapOf<String, Pair<String, SyncedFeedGroupMembership>>()
        groups.forEach { group ->
            val groupRecordId = requireNotNull(mappings[group]).groupRecordId
            feedGroupDao.getSubscriptionIdsForDirect(group.uid)
                .mapNotNull(subscriptionsById::get)
                .forEach { subscription ->
                    val url = subscription.url?.trim().orEmpty()
                    if (url.isEmpty()) {
                        return@forEach
                    }
                    val membership = SyncedFeedGroupMembership(
                        groupRecordId = groupRecordId,
                        serviceId = subscription.serviceId,
                        subscriptionUrl = url
                    )
                    val recordId = StructuredPreferenceRecordId.feedGroupMembership(
                        groupRecordId,
                        membership.serviceId,
                        membership.subscriptionUrl
                    )
                    desiredMemberships[recordId] = groupRecordId to membership
                    recordRepository.saveLocalUpsert(
                        category = StructuredPreferenceCategory.FEED_GROUPS,
                        recordId = recordId,
                        recordType =
                            StructuredPreferenceRecordType.FEED_GROUP_MEMBERSHIP,
                        parentRecordId = groupRecordId,
                        record = SyncedStructuredPreferenceRecord(
                            feedGroupMembership = membership
                        )
                    )
                }
        }
        recordRepository.getRecordsByType(
            StructuredPreferenceCategory.FEED_GROUPS,
            StructuredPreferenceRecordType.FEED_GROUP_MEMBERSHIP
        ).filterNot(StructuredPreferenceSyncRecordEntity::isDeleted)
            .filterNot { it.recordId in desiredMemberships }
            .forEach(recordRepository::saveLocalDelete)

        recordRepository.saveLocalUpsert(
            category = StructuredPreferenceCategory.FEED_GROUPS,
            recordId = StructuredPreferenceRecordId.feedGroupOrder(),
            recordType = StructuredPreferenceRecordType.FEED_GROUP_ORDER,
            record = SyncedStructuredPreferenceRecord(
                feedGroupOrder = SyncedFeedGroupOrder(
                    groups.map { requireNotNull(mappings[it]).groupRecordId }
                )
            )
        )
    }

    private fun reconcileHomeTabs() {
        val tabs = currentTabs()
        if (tabs.size > MAX_HOME_TABS) {
            throw StructuredPreferenceSyncException(
                "There are too many home tabs to synchronize"
            )
        }
        val desiredTabs = tabs.mapNotNull(::toSyncedHomeTab)
            .associateBy(StructuredPreferenceRecordId::homeTab)
        desiredTabs.forEach { (recordId, tab) ->
            recordRepository.saveLocalUpsert(
                category = StructuredPreferenceCategory.HOME_TABS,
                recordId = recordId,
                recordType = StructuredPreferenceRecordType.HOME_TAB,
                record = SyncedStructuredPreferenceRecord(homeTab = tab)
            )
        }
        recordRepository.getRecordsByType(
            StructuredPreferenceCategory.HOME_TABS,
            StructuredPreferenceRecordType.HOME_TAB
        ).filterNot(StructuredPreferenceSyncRecordEntity::isDeleted)
            .filterNot { it.recordId in desiredTabs }
            .forEach(recordRepository::saveLocalDelete)
        recordRepository.saveLocalUpsert(
            category = StructuredPreferenceCategory.HOME_TABS,
            recordId = StructuredPreferenceRecordId.homeTabOrder(),
            recordType = StructuredPreferenceRecordType.HOME_TAB_ORDER,
            record = SyncedStructuredPreferenceRecord(
                homeTabOrder = SyncedHomeTabOrder(
                    tabs.mapNotNull(::toSyncedHomeTab)
                        .map(StructuredPreferenceRecordId::homeTab)
                )
            )
        )
    }

    private fun reconcileChannelProfiles() {
        val desired = currentChannelProfileFields().associateBy {
            StructuredPreferenceRecordId.channelProfileField(it)
        }
        desired.forEach { (recordId, field) ->
            recordRepository.saveLocalUpsert(
                category = StructuredPreferenceCategory.CHANNEL_PROFILES,
                recordId = recordId,
                recordType =
                    StructuredPreferenceRecordType.CHANNEL_PROFILE_FIELD,
                record = SyncedStructuredPreferenceRecord(
                    channelProfileField = field
                )
            )
        }
        recordRepository.getRecordsByType(
            StructuredPreferenceCategory.CHANNEL_PROFILES,
            StructuredPreferenceRecordType.CHANNEL_PROFILE_FIELD
        ).filterNot(StructuredPreferenceSyncRecordEntity::isDeleted)
            .filterNot { it.recordId in desired }
            .forEach(recordRepository::saveLocalDelete)
    }

    private fun reconcileFilters() {
        filterSpecs().forEach { spec ->
            val filter = SyncedFilterSet(
                filterId = spec.id,
                values = currentFilterValues(spec).sorted()
            )
            recordRepository.saveLocalUpsert(
                category = StructuredPreferenceCategory.FILTERS,
                recordId = StructuredPreferenceRecordId.filterSet(spec.id),
                recordType = StructuredPreferenceRecordType.FILTER_SET,
                record = SyncedStructuredPreferenceRecord(filterSet = filter)
            )
        }
    }

    private fun reconcileSettings() {
        val desired = portableSettingSpecs(context).mapNotNull { spec ->
            currentPortableSetting(spec)?.let { setting ->
                StructuredPreferenceRecordId.portableSetting(setting.settingId) to setting
            }
        }.toMap()
        desired.forEach { (recordId, setting) ->
            recordRepository.saveLocalUpsert(
                category = StructuredPreferenceCategory.SETTINGS,
                recordId = recordId,
                recordType = StructuredPreferenceRecordType.PORTABLE_SETTING,
                record = SyncedStructuredPreferenceRecord(portableSetting = setting)
            )
        }
        recordRepository.getRecordsByType(
            StructuredPreferenceCategory.SETTINGS,
            StructuredPreferenceRecordType.PORTABLE_SETTING
        ).filterNot(StructuredPreferenceSyncRecordEntity::isDeleted)
            .filterNot { it.recordId in desired }
            .forEach(recordRepository::saveLocalDelete)
    }

    private fun reconcileCompletedDownloads() {
        val desired = currentCompletedDownloads().associateBy(
            SyncedCompletedDownload::syncId
        )
        desired.forEach { (recordId, download) ->
            recordRepository.saveLocalUpsert(
                category = StructuredPreferenceCategory.COMPLETED_DOWNLOADS,
                recordId = recordId,
                recordType = StructuredPreferenceRecordType.COMPLETED_DOWNLOAD,
                record = SyncedStructuredPreferenceRecord(
                    completedDownload = download
                )
            )
        }
        recordRepository.getRecordsByType(
            StructuredPreferenceCategory.COMPLETED_DOWNLOADS,
            StructuredPreferenceRecordType.COMPLETED_DOWNLOAD
        ).filterNot(StructuredPreferenceSyncRecordEntity::isDeleted)
            .filter { record ->
                recordRepository.decodeRecord(record).completedDownload?.ownerPeerId == localPeerId
            }
            .filterNot { it.recordId in desired }
            .forEach(recordRepository::saveLocalDelete)
    }

    private fun materialize(category: StructuredPreferenceCategory) {
        when (category) {
            StructuredPreferenceCategory.FEED_GROUPS -> materializeFeedGroups()

            StructuredPreferenceCategory.HOME_TABS -> materializeHomeTabs()

            StructuredPreferenceCategory.CHANNEL_PROFILES ->
                materializeChannelProfiles()

            StructuredPreferenceCategory.FILTERS -> materializeFilters()

            StructuredPreferenceCategory.SETTINGS -> materializeSettings()

            StructuredPreferenceCategory.COMPLETED_DOWNLOADS -> Unit
        }
    }

    private fun materializeFeedGroups() {
        val category = StructuredPreferenceCategory.FEED_GROUPS
        val metadataRecords = recordRepository.getRecordsByType(
            category,
            StructuredPreferenceRecordType.FEED_GROUP
        )
        metadataRecords.filter(StructuredPreferenceSyncRecordEntity::isDeleted)
            .forEach { record ->
                recordRepository.getFeedGroupMapping(record.recordId)?.let { mapping ->
                    feedGroupDao.delete(mapping.groupUid)
                }
            }
        metadataRecords.filterNot(StructuredPreferenceSyncRecordEntity::isDeleted)
            .forEach { record ->
                val data = recordRepository.decodeRecord(record).feedGroup
                    ?: throw StructuredPreferenceSyncException(
                        "Stored feed group metadata is invalid"
                    )
                val mapping = recordRepository.getFeedGroupMapping(record.recordId)
                var group = mapping?.let { feedGroupDao.getGroupDirect(it.groupUid) }
                if (group == null) {
                    val uid = feedGroupDao.insert(
                        FeedGroupEntity(
                            uid = 0,
                            name = data.name,
                            icon = feedGroupIcon(data.iconId)
                        )
                    )
                    recordRepository.saveFeedGroupMapping(
                        StructuredPreferenceSyncFeedGroupMapEntity(
                            groupRecordId = record.recordId,
                            groupUid = uid
                        )
                    )
                    group = requireNotNull(feedGroupDao.getGroupDirect(uid))
                }
                group.name = data.name
                group.icon = feedGroupIcon(data.iconId)
                feedGroupDao.update(group)
            }

        val subscriptions = subscriptionDao.getAllDirect().associateBy {
            it.serviceId to it.url?.trim()
        }
        metadataRecords.filterNot(StructuredPreferenceSyncRecordEntity::isDeleted)
            .forEach { groupRecord ->
                val mapping = recordRepository.getFeedGroupMapping(groupRecord.recordId)
                    ?: return@forEach
                val subscriptionIds = recordRepository.getChildRecords(
                    category,
                    groupRecord.recordId
                ).filterNot(StructuredPreferenceSyncRecordEntity::isDeleted)
                    .filter {
                        it.recordType ==
                            StructuredPreferenceRecordType.FEED_GROUP_MEMBERSHIP.name
                    }
                    .mapNotNull { record ->
                        val data = recordRepository.decodeRecord(record).feedGroupMembership
                            ?: return@mapNotNull null
                        subscriptions[data.serviceId to data.subscriptionUrl]?.uid
                    }
                feedGroupDao.updateSubscriptionsForGroup(
                    mapping.groupUid,
                    subscriptionIds
                )
            }

        val order = recordRepository.getRecord(
            category,
            StructuredPreferenceRecordId.feedGroupOrder()
        )?.takeUnless(StructuredPreferenceSyncRecordEntity::isDeleted)
            ?.let(recordRepository::decodeRecord)
            ?.feedGroupOrder
            ?.groupRecordIds
            .orEmpty()
        val orderedUids = order.mapNotNull { recordId ->
            recordRepository.getFeedGroupMapping(recordId)?.groupUid
        }
        val remainingUids = feedGroupDao.getAllDirect()
            .map(FeedGroupEntity::uid)
            .filterNot(orderedUids::contains)
        feedGroupDao.updateOrder(
            (orderedUids + remainingUids).mapIndexed { index, uid ->
                uid to index.toLong()
            }.toMap()
        )
    }

    private fun materializeHomeTabs() {
        val category = StructuredPreferenceCategory.HOME_TABS
        val records = recordRepository.getRecordsByType(
            category,
            StructuredPreferenceRecordType.HOME_TAB
        ).filterNot(StructuredPreferenceSyncRecordEntity::isDeleted)
            .associateBy(StructuredPreferenceSyncRecordEntity::recordId)
        val order = recordRepository.getRecord(
            category,
            StructuredPreferenceRecordId.homeTabOrder()
        )?.takeUnless(StructuredPreferenceSyncRecordEntity::isDeleted)
            ?.let(recordRepository::decodeRecord)
            ?.homeTabOrder
            ?.tabRecordIds
            .orEmpty()
        val tabs = order.mapNotNull { recordId ->
            records[recordId]?.let(recordRepository::decodeRecord)?.homeTab
                ?.let(::toLocalHomeTab)
        }
        if (tabs.isNotEmpty()) {
            preferences.edit()
                .putString(
                    context.getString(R.string.saved_tabs_key),
                    TabsJsonHelper.getJsonToSave(tabs)
                )
                .commit()
        }
    }

    private fun materializeChannelProfiles() {
        val fields = recordRepository.getRecordsByType(
            StructuredPreferenceCategory.CHANNEL_PROFILES,
            StructuredPreferenceRecordType.CHANNEL_PROFILE_FIELD
        )
        val editor = preferences.edit()
        preferences.all.keys
            .filter(::isChannelProfilePreferenceKey)
            .forEach(editor::remove)
        fields.filterNot(StructuredPreferenceSyncRecordEntity::isDeleted)
            .mapNotNull { recordRepository.decodeRecord(it).channelProfileField }
            .forEach { field ->
                val key = field.profileKey + field.field.preferenceSuffix
                when (field.field) {
                    ChannelProfileField.SPEED ->
                        editor.putFloat(key, requireNotNull(field.speed))

                    ChannelProfileField.QUALITY ->
                        editor.putString(key, requireNotNull(field.textValue))

                    ChannelProfileField.CAPTION ->
                        editor.putString(key, field.textValue.orEmpty())
                }
            }
        editor.commit()
    }

    private fun materializeFilters() {
        val specs = filterSpecs().associateBy(FilterSpec::id)
        val editor = preferences.edit()
        recordRepository.getRecordsByType(
            StructuredPreferenceCategory.FILTERS,
            StructuredPreferenceRecordType.FILTER_SET
        ).filterNot(StructuredPreferenceSyncRecordEntity::isDeleted)
            .mapNotNull { recordRepository.decodeRecord(it).filterSet }
            .forEach { filter ->
                val spec = specs[filter.filterId] ?: return@forEach
                editor.putStringSet(spec.preferenceKey, filter.values.toSet())
            }
        editor.commit()
    }

    private fun materializeSettings() {
        val specs = portableSettingSpecs(context).associateBy(PortableSettingSpec::id)
        val editor = preferences.edit()
        recordRepository.getRecordsByType(
            StructuredPreferenceCategory.SETTINGS,
            StructuredPreferenceRecordType.PORTABLE_SETTING
        ).forEach { entity ->
            val setting = recordRepository.decodeRecord(entity).portableSetting
                ?: throw StructuredPreferenceSyncException(
                    "Stored portable setting data is invalid"
                )
            val spec = specs[setting.settingId]
                ?: throw StructuredPreferenceSyncException(
                    "Stored portable setting is not allowlisted"
                )
            if (entity.isDeleted) {
                editor.remove(spec.preferenceKey)
            } else {
                when (setting.settingId.valueType) {
                    PortableSettingValueType.BOOLEAN ->
                        editor.putBoolean(
                            spec.preferenceKey,
                            requireNotNull(setting.booleanValue)
                        )

                    PortableSettingValueType.STRING ->
                        editor.putString(
                            spec.preferenceKey,
                            requireNotNull(setting.stringValue)
                        )

                    PortableSettingValueType.FLOAT ->
                        editor.putFloat(
                            spec.preferenceKey,
                            requireNotNull(setting.floatValue)
                        )
                }
            }
        }
        editor.apply()
    }

    private fun toSyncedHomeTab(tab: Tab): SyncedHomeTab? {
        return when (tab) {
            is Tab.BlankTab -> SyncedHomeTab(SyncedHomeTabType.BLANK)

            is Tab.DefaultKioskTab -> SyncedHomeTab(SyncedHomeTabType.DEFAULT_KIOSK)

            is Tab.SubscriptionsTab -> SyncedHomeTab(SyncedHomeTabType.SUBSCRIPTIONS)

            is Tab.FeedTab -> SyncedHomeTab(SyncedHomeTabType.FEED)

            is Tab.BookmarksTab -> SyncedHomeTab(SyncedHomeTabType.BOOKMARKS)

            is Tab.HistoryTab -> SyncedHomeTab(SyncedHomeTabType.HISTORY)

            is Tab.DownloadsTab -> SyncedHomeTab(SyncedHomeTabType.DOWNLOADS)

            is Tab.KioskTab -> SyncedHomeTab(
                type = SyncedHomeTabType.KIOSK,
                serviceId = tab.kioskServiceId,
                kioskId = tab.kioskId
            )

            is Tab.ChannelTab -> SyncedHomeTab(
                type = SyncedHomeTabType.CHANNEL,
                serviceId = tab.channelServiceId,
                url = tab.channelUrl.trim(),
                name = tab.channelName.take(MAX_STRUCTURED_NAME_LENGTH)
            )

            is Tab.PlaylistTab -> {
                if (tab.playlistType == LocalItemType.PLAYLIST_LOCAL_ITEM) {
                    val recordId = playlistSyncDao.getLocalMapping(tab.playlistId)
                        ?.playlistRecordId
                        ?: return null
                    SyncedHomeTab(
                        type = SyncedHomeTabType.LOCAL_PLAYLIST,
                        name = tab.playlistName.take(MAX_STRUCTURED_NAME_LENGTH),
                        linkedRecordId = recordId
                    )
                } else {
                    SyncedHomeTab(
                        type = SyncedHomeTabType.REMOTE_PLAYLIST,
                        serviceId = tab.playlistServiceId,
                        url = tab.playlistUrl.trim(),
                        name = tab.playlistName.take(MAX_STRUCTURED_NAME_LENGTH)
                    )
                }
            }

            is Tab.FeedGroupTab -> {
                val recordId = recordRepository.getFeedGroupMapping(tab.feedGroupId)
                    ?.groupRecordId
                    ?: return null
                SyncedHomeTab(
                    type = SyncedHomeTabType.FEED_GROUP,
                    name = tab.feedGroupName.take(MAX_STRUCTURED_NAME_LENGTH),
                    linkedRecordId = recordId
                )
            }

            else -> null
        }
    }

    private fun toLocalHomeTab(tab: SyncedHomeTab): Tab? {
        return when (tab.type) {
            SyncedHomeTabType.BLANK -> Tab.Type.BLANK.tab

            SyncedHomeTabType.DEFAULT_KIOSK -> Tab.Type.DEFAULT_KIOSK.tab

            SyncedHomeTabType.SUBSCRIPTIONS -> Tab.Type.SUBSCRIPTIONS.tab

            SyncedHomeTabType.FEED -> Tab.Type.FEED.tab

            SyncedHomeTabType.BOOKMARKS -> Tab.Type.BOOKMARKS.tab

            SyncedHomeTabType.HISTORY -> Tab.Type.HISTORY.tab

            SyncedHomeTabType.DOWNLOADS -> Tab.Type.DOWNLOADS.tab

            SyncedHomeTabType.KIOSK -> Tab.KioskTab(
                requireNotNull(tab.serviceId),
                requireNotNull(tab.kioskId)
            )

            SyncedHomeTabType.CHANNEL -> Tab.ChannelTab(
                requireNotNull(tab.serviceId),
                requireNotNull(tab.url),
                requireNotNull(tab.name)
            )

            SyncedHomeTabType.LOCAL_PLAYLIST -> {
                val mapping = playlistSyncDao.getLocalMapping(
                    requireNotNull(tab.linkedRecordId)
                ) ?: return null
                val playlist = playlistDao.getPlaylistDirect(mapping.playlistUid)
                    ?: return null
                Tab.PlaylistTab(
                    mapping.playlistUid,
                    playlist.name ?: requireNotNull(tab.name)
                )
            }

            SyncedHomeTabType.REMOTE_PLAYLIST -> Tab.PlaylistTab(
                requireNotNull(tab.serviceId),
                requireNotNull(tab.url),
                requireNotNull(tab.name)
            )

            SyncedHomeTabType.FEED_GROUP -> {
                val mapping = recordRepository.getFeedGroupMapping(
                    requireNotNull(tab.linkedRecordId)
                ) ?: return null
                val group = feedGroupDao.getGroupDirect(mapping.groupUid)
                    ?: return null
                Tab.FeedGroupTab(
                    group.uid,
                    group.name,
                    group.icon.drawableResource
                )
            }
        }
    }

    private fun currentTabs(): List<Tab> {
        val savedTabs = preferences.getString(
            context.getString(R.string.saved_tabs_key),
            null
        )
        return try {
            TabsJsonHelper.getTabsFromJson(savedTabs)
        } catch (_: TabsJsonHelper.InvalidJsonException) {
            TabsJsonHelper.getDefaultTabs()
        }
    }

    private fun currentChannelProfileFields(): List<SyncedChannelProfileField> {
        return preferences.all.entries.mapNotNull { (key, value) ->
            if (!isChannelProfilePreferenceKey(key)) {
                return@mapNotNull null
            }
            val field = when {
                key.endsWith(SPEED_SUFFIX) -> ChannelProfileField.SPEED
                key.endsWith(QUALITY_SUFFIX) -> ChannelProfileField.QUALITY
                key.endsWith(CAPTION_SUFFIX) -> ChannelProfileField.CAPTION
                else -> return@mapNotNull null
            }
            val profileKey = key.removeSuffix(field.preferenceSuffix)
            when (field) {
                ChannelProfileField.SPEED -> (value as? Float)?.let {
                    SyncedChannelProfileField(
                        profileKey = profileKey,
                        field = field,
                        speed = it
                    )
                }

                ChannelProfileField.QUALITY -> (value as? String)
                    ?.takeIf(String::isNotBlank)
                    ?.let {
                        SyncedChannelProfileField(
                            profileKey = profileKey,
                            field = field,
                            textValue = it.take(MAX_FILTER_VALUE_LENGTH)
                        )
                    }

                ChannelProfileField.CAPTION -> (value as? String)?.let {
                    SyncedChannelProfileField(
                        profileKey = profileKey,
                        field = field,
                        textValue = it.take(MAX_FILTER_VALUE_LENGTH).ifEmpty { null }
                    )
                }
            }
        }.sortedWith(
            compareBy(
                SyncedChannelProfileField::profileKey,
                SyncedChannelProfileField::field
            )
        )
    }

    private fun isChannelProfilePreferenceKey(key: String): Boolean {
        return key.startsWith(CHANNEL_PROFILE_PREFIX) &&
            (
                key.endsWith(SPEED_SUFFIX) ||
                    key.endsWith(QUALITY_SUFFIX) ||
                    key.endsWith(CAPTION_SUFFIX)
                )
    }

    private val ChannelProfileField.preferenceSuffix: String
        get() = when (this) {
            ChannelProfileField.SPEED -> SPEED_SUFFIX
            ChannelProfileField.QUALITY -> QUALITY_SUFFIX
            ChannelProfileField.CAPTION -> CAPTION_SUFFIX
        }

    private fun filterSpecs(): List<FilterSpec> = listOf(
        FilterSpec(
            StructuredFilterId.CHANNEL_TABS,
            context.getString(R.string.show_channel_tabs_key),
            R.array.show_channel_tabs_value_list
        ),
        FilterSpec(
            StructuredFilterId.FEED_CHANNEL_TABS,
            context.getString(R.string.feed_fetch_channel_tabs_key),
            R.array.feed_fetch_channel_tabs_value_list
        ),
        FilterSpec(
            StructuredFilterId.SEARCH_SUGGESTIONS,
            context.getString(R.string.show_search_suggestions_key),
            R.array.show_search_suggestions_value_list
        )
    )

    private fun currentFilterValues(spec: FilterSpec): Set<String> {
        val defaults = context.resources.getStringArray(spec.defaultValuesResource).toSet()
        return preferences.getStringSet(spec.preferenceKey, defaults)?.toSet() ?: defaults
    }

    private fun snapshotHash(category: StructuredPreferenceCategory): String {
        val snapshot = when (category) {
            StructuredPreferenceCategory.FEED_GROUPS -> {
                val subscriptions = subscriptionDao.getAllDirect().associateBy { it.uid }
                STRUCTURED_PREFERENCE_JSON.encodeToString(
                    feedGroupDao.getAllDirect().map { group ->
                        FeedGroupSnapshot(
                            uid = group.uid,
                            name = group.name,
                            iconId = group.icon.id,
                            sortOrder = group.sortOrder,
                            memberships = feedGroupDao
                                .getSubscriptionIdsForDirect(group.uid)
                                .mapNotNull(subscriptions::get)
                                .map {
                                    "${it.serviceId}\u0000${it.url?.trim().orEmpty()}"
                                }
                                .sorted()
                        )
                    }
                )
            }

            StructuredPreferenceCategory.HOME_TABS ->
                TabsJsonHelper.getJsonToSave(currentTabs())

            StructuredPreferenceCategory.CHANNEL_PROFILES ->
                STRUCTURED_PREFERENCE_JSON.encodeToString(currentChannelProfileFields())

            StructuredPreferenceCategory.FILTERS -> STRUCTURED_PREFERENCE_JSON.encodeToString(
                filterSpecs().map { spec ->
                    FilterSnapshot(spec.id, currentFilterValues(spec).sorted())
                }
            )

            StructuredPreferenceCategory.SETTINGS -> STRUCTURED_PREFERENCE_JSON.encodeToString(
                portableSettingSpecs(context).mapNotNull(::currentPortableSetting)
            )

            StructuredPreferenceCategory.COMPLETED_DOWNLOADS ->
                STRUCTURED_PREFERENCE_JSON.encodeToString(currentCompletedDownloads())
        }
        return digest(snapshot)
    }

    private fun currentPortableSetting(
        spec: PortableSettingSpec
    ): SyncedPortableSetting? {
        val value = preferences.all[spec.preferenceKey] ?: return null
        return when (spec.id.valueType) {
            PortableSettingValueType.BOOLEAN -> (value as? Boolean)?.let {
                SyncedPortableSetting(spec.id, booleanValue = it)
            }

            PortableSettingValueType.STRING -> (value as? String)
                ?.trim()
                ?.takeIf { it.isNotEmpty() && it.length <= MAX_PORTABLE_SETTING_VALUE_LENGTH }
                ?.let {
                    SyncedPortableSetting(spec.id, stringValue = it)
                }

            PortableSettingValueType.FLOAT -> (value as? Float)
                ?.takeIf { it.isFinite() }
                ?.let {
                    SyncedPortableSetting(spec.id, floatValue = it)
                }
        }
    }

    private fun currentCompletedDownloads(): List<SyncedCompletedDownload> {
        return finishedMissionStore.loadCompletedDownloadMetadata().mapNotNull { mission ->
            val syncId = mission.syncId?.takeIf(::isCanonicalUuid)
                ?: return@mapNotNull null
            val sourceUrl = mission.source?.trim()
                ?.takeIf { it.isNotEmpty() && it.length <= MAX_STRUCTURED_URL_LENGTH }
                ?: return@mapNotNull null
            val displayName = mission.displayName?.trim()
                ?.take(MAX_DOWNLOAD_DISPLAY_NAME_LENGTH)
                ?.takeIf(String::isNotEmpty)
                ?: "download"
            val mimeType = mission.mimeType?.trim()
                ?.take(MAX_DOWNLOAD_MIME_TYPE_LENGTH)
                ?.takeIf(String::isNotEmpty)
                ?: StoredFileHelper.DEFAULT_MIME
            val mediaKind = mission.kind.toString()
                .takeIf { it[0] in SUPPORTED_LOCAL_DOWNLOAD_KINDS }
                ?: "?"
            SyncedCompletedDownload(
                syncId = syncId,
                ownerPeerId = localPeerId,
                sourceUrl = sourceUrl,
                displayName = displayName,
                mimeType = mimeType,
                sizeBytes = mission.length.coerceAtLeast(0),
                completedAtEpochMillis = mission.timestamp.coerceAtLeast(1),
                mediaKind = mediaKind
            )
        }.sortedBy(SyncedCompletedDownload::syncId)
    }

    private fun isCanonicalUuid(value: String): Boolean {
        return runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)
    }

    private fun feedGroupIcon(iconId: Int): FeedGroupIcon {
        return FeedGroupIcon.entries.firstOrNull { it.id == iconId }
            ?: throw StructuredPreferenceSyncException("Unknown feed group icon")
    }

    private fun digest(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    @Serializable
    private data class FeedGroupSnapshot(
        val uid: Long,
        val name: String,
        val iconId: Int,
        val sortOrder: Long,
        val memberships: List<String>
    )

    @Serializable
    private data class FilterSnapshot(
        val id: StructuredFilterId,
        val values: List<String>
    )

    private data class FilterSpec(
        val id: StructuredFilterId,
        val preferenceKey: String,
        val defaultValuesResource: Int
    )

    companion object {
        private const val SPEED_SUFFIX = ".speed"
        private const val QUALITY_SUFFIX = ".quality"
        private const val CAPTION_SUFFIX = ".caption"
        private val SUPPORTED_LOCAL_DOWNLOAD_KINDS = setOf('a', 'v', 's', '?')

        @Volatile
        private var instance: RoomStructuredPreferenceSyncStore? = null

        fun get(context: Context): RoomStructuredPreferenceSyncStore {
            return instance ?: synchronized(this) {
                instance ?: RoomStructuredPreferenceSyncStore(
                    context = context.applicationContext,
                    database = NewPipeDatabase.getInstance(context),
                    localPeerId = AndroidSyncStateRepository(context)
                        .loadOrCreateIdentity()
                        .peerId
                        .toBase58()
                ).also { instance = it }
            }
        }
    }
}
