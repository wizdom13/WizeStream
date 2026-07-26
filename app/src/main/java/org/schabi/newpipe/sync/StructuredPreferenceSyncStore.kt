/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.LocalItem.LocalItemType
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.feed.model.FeedGroupSubscriptionEntity
import org.schabi.newpipe.database.sync.StructuredPreferenceSyncChangeEntity
import org.schabi.newpipe.database.sync.StructuredPreferenceSyncFeedGroupMapEntity
import org.schabi.newpipe.database.sync.StructuredPreferenceSyncLocalStateEntity
import org.schabi.newpipe.database.sync.StructuredPreferenceSyncOriginStateEntity
import org.schabi.newpipe.database.sync.StructuredPreferenceSyncPeerStateEntity
import org.schabi.newpipe.database.sync.StructuredPreferenceSyncRecordEntity
import org.schabi.newpipe.local.subscription.FeedGroupIcon
import org.schabi.newpipe.settings.tabs.Tab
import org.schabi.newpipe.settings.tabs.TabsJsonHelper

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
        PreferenceManager.getDefaultSharedPreferences(context)
) : StructuredPreferenceSyncStore {
    private val syncDao = database.structuredPreferenceSyncDAO()
    private val feedGroupDao = database.feedGroupDAO()
    private val subscriptionDao = database.subscriptionDAO()
    private val playlistDao = database.playlistDAO()
    private val playlistSyncDao = database.playlistSyncDAO()

    override fun reconcileLocal(category: StructuredPreferenceCategory) {
        database.runInTransaction {
            val currentSnapshot = snapshotHash(category)
            val localState = syncDao.getLocalState(category.name)
            if (localState?.snapshotHash == currentSnapshot) {
                return@runInTransaction
            }
            when (category) {
                StructuredPreferenceCategory.FEED_GROUPS ->
                    reconcileFeedGroups(bootstrap = localState == null)

                StructuredPreferenceCategory.HOME_TABS -> reconcileHomeTabs()

                StructuredPreferenceCategory.CHANNEL_PROFILES ->
                    reconcileChannelProfiles()

                StructuredPreferenceCategory.FILTERS -> reconcileFilters()
            }
            saveSnapshot(category)
        }
    }

    override fun getKnownRevisions(
        category: StructuredPreferenceCategory
    ): Map<String, Long> {
        return syncDao.getOriginStates(category.name).associate {
            it.originPeerId to it.contiguousRevision
        }
    }

    override fun getPendingChanges(
        category: StructuredPreferenceCategory,
        peerId: String,
        limit: Int
    ): StructuredPreferenceChangeBatch {
        require(limit > 0)
        val acknowledged = syncDao.getPeerStates(category.name, peerId).associate {
            it.originPeerId to it.acknowledgedRevision
        }
        val origins = (
            syncDao.getChangeOrigins(category.name) +
                getKnownRevisions(category).keys
            ).distinct()
        val pending = origins.flatMap { origin ->
            syncDao.getChangesAfter(
                category.name,
                origin,
                acknowledged[origin] ?: 0,
                limit + 1
            )
        }.map(::decodeChange)
            .sortedBy(StructuredPreferenceChange::versionStamp)
        return StructuredPreferenceChangeBatch(
            changes = pending.take(limit),
            hasMore = pending.size > limit || origins.any { origin ->
                syncDao.countChangesAfter(
                    category.name,
                    origin,
                    acknowledged[origin] ?: 0
                ) > pending.count { it.originPeerId == origin }
            }
        )
    }

    override fun acknowledgePeer(
        category: StructuredPreferenceCategory,
        peerId: String,
        knownRevisions: Map<String, Long>
    ) {
        StructuredPreferenceSyncValidation.validateKnownRevisions(knownRevisions)
        val localKnowledge = getKnownRevisions(category)
        knownRevisions.forEach { (originPeerId, revision) ->
            val safeRevision = minOf(revision, localKnowledge[originPeerId] ?: 0)
            val current = syncDao.getPeerStates(category.name, peerId)
                .firstOrNull { it.originPeerId == originPeerId }
                ?.acknowledgedRevision
                ?: 0
            if (safeRevision > current) {
                syncDao.upsertPeerState(
                    StructuredPreferenceSyncPeerStateEntity(
                        category = category.name,
                        peerId = peerId,
                        originPeerId = originPeerId,
                        acknowledgedRevision = safeRevision
                    )
                )
            }
        }
    }

    override fun applyChanges(
        category: StructuredPreferenceCategory,
        changes: List<StructuredPreferenceChange>
    ): StructuredPreferenceApplyResult {
        StructuredPreferenceSyncValidation.validateChanges(category, changes)
        return database.runInTransaction<StructuredPreferenceApplyResult> {
            var acceptedChanges = 0
            var affectedRecords = 0
            changes.forEach { change ->
                if (
                    syncDao.hasChange(
                        category.name,
                        change.originPeerId,
                        change.originRevision
                    )
                ) {
                    return@forEach
                }
                check(syncDao.insertChange(change.toEntity()) != -1L) {
                    "A structured preference revision was inserted concurrently"
                }
                acceptedChanges += 1
                advanceKnownRevision(category, change.originPeerId)
                val current = syncDao.getRecord(category.name, change.recordId)
                if (
                    current == null ||
                    change.versionStamp > current.versionStamp
                ) {
                    syncDao.upsertRecord(change.toRecordEntity())
                    affectedRecords += 1
                }
            }
            if (affectedRecords > 0) {
                materialize(category)
                saveSnapshot(category)
            }
            StructuredPreferenceApplyResult(
                acceptedChanges = acceptedChanges,
                affectedRecords = affectedRecords
            )
        }
    }

    override fun clearPeerKnowledge() {
        syncDao.deleteAllPeerStates()
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
            syncDao.getFeedGroupMapping(group.uid)
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
                    ).also(syncDao::upsertFeedGroupMapping)
                }
        }
        val desiredGroupIds = mappings.values.mapTo(hashSetOf()) {
            it.groupRecordId
        }
        groups.forEach { group ->
            val recordId = requireNotNull(mappings[group]).groupRecordId
            saveLocalUpsert(
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
        syncDao.getRecordsByType(
            StructuredPreferenceCategory.FEED_GROUPS.name,
            StructuredPreferenceRecordType.FEED_GROUP.name
        ).filterNot(StructuredPreferenceSyncRecordEntity::isDeleted)
            .filterNot { it.recordId in desiredGroupIds }
            .forEach(::saveLocalDelete)

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
                    saveLocalUpsert(
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
        syncDao.getRecordsByType(
            StructuredPreferenceCategory.FEED_GROUPS.name,
            StructuredPreferenceRecordType.FEED_GROUP_MEMBERSHIP.name
        ).filterNot(StructuredPreferenceSyncRecordEntity::isDeleted)
            .filterNot { it.recordId in desiredMemberships }
            .forEach(::saveLocalDelete)

        saveLocalUpsert(
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
            saveLocalUpsert(
                category = StructuredPreferenceCategory.HOME_TABS,
                recordId = recordId,
                recordType = StructuredPreferenceRecordType.HOME_TAB,
                record = SyncedStructuredPreferenceRecord(homeTab = tab)
            )
        }
        syncDao.getRecordsByType(
            StructuredPreferenceCategory.HOME_TABS.name,
            StructuredPreferenceRecordType.HOME_TAB.name
        ).filterNot(StructuredPreferenceSyncRecordEntity::isDeleted)
            .filterNot { it.recordId in desiredTabs }
            .forEach(::saveLocalDelete)
        saveLocalUpsert(
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
            saveLocalUpsert(
                category = StructuredPreferenceCategory.CHANNEL_PROFILES,
                recordId = recordId,
                recordType =
                    StructuredPreferenceRecordType.CHANNEL_PROFILE_FIELD,
                record = SyncedStructuredPreferenceRecord(
                    channelProfileField = field
                )
            )
        }
        syncDao.getRecordsByType(
            StructuredPreferenceCategory.CHANNEL_PROFILES.name,
            StructuredPreferenceRecordType.CHANNEL_PROFILE_FIELD.name
        ).filterNot(StructuredPreferenceSyncRecordEntity::isDeleted)
            .filterNot { it.recordId in desired }
            .forEach(::saveLocalDelete)
    }

    private fun reconcileFilters() {
        filterSpecs().forEach { spec ->
            val filter = SyncedFilterSet(
                filterId = spec.id,
                values = currentFilterValues(spec).sorted()
            )
            saveLocalUpsert(
                category = StructuredPreferenceCategory.FILTERS,
                recordId = StructuredPreferenceRecordId.filterSet(spec.id),
                recordType = StructuredPreferenceRecordType.FILTER_SET,
                record = SyncedStructuredPreferenceRecord(filterSet = filter)
            )
        }
    }

    private fun saveLocalUpsert(
        category: StructuredPreferenceCategory,
        recordId: String,
        recordType: StructuredPreferenceRecordType,
        record: SyncedStructuredPreferenceRecord,
        parentRecordId: String? = null
    ) {
        val current = syncDao.getRecord(category.name, recordId)
        if (
            current != null &&
            !current.isDeleted &&
            current.recordType == recordType.name &&
            current.parentRecordId == parentRecordId &&
            decodeRecord(current) == record
        ) {
            return
        }
        saveLocalChange(
            category = category,
            recordId = recordId,
            recordType = recordType,
            parentRecordId = parentRecordId,
            type = StructuredPreferenceChangeType.UPSERT,
            record = record
        )
    }

    private fun saveLocalDelete(current: StructuredPreferenceSyncRecordEntity) {
        saveLocalChange(
            category = StructuredPreferenceCategory.valueOf(current.category),
            recordId = current.recordId,
            recordType = StructuredPreferenceRecordType.valueOf(current.recordType),
            parentRecordId = current.parentRecordId,
            type = StructuredPreferenceChangeType.DELETE,
            record = decodeRecord(current)
        )
    }

    private fun saveLocalChange(
        category: StructuredPreferenceCategory,
        recordId: String,
        recordType: StructuredPreferenceRecordType,
        parentRecordId: String?,
        type: StructuredPreferenceChangeType,
        record: SyncedStructuredPreferenceRecord
    ) {
        val categoryName = category.name
        val current = syncDao.getRecord(categoryName, recordId)
        val originRevision = incrementVersion(
            syncDao.getOriginState(categoryName, localPeerId)
                ?.contiguousRevision
                ?: 0
        )
        val lamportVersion = incrementVersion(
            maxOf(
                syncDao.getMaximumLamportVersion(categoryName),
                current?.lamportVersion ?: 0
            )
        )
        val change = StructuredPreferenceChange(
            category = category,
            originPeerId = localPeerId,
            originRevision = originRevision,
            lamportVersion = lamportVersion,
            recordId = recordId,
            recordType = recordType,
            parentRecordId = parentRecordId,
            type = type,
            record = record
        )
        StructuredPreferenceSyncValidation.validateChanges(category, listOf(change))
        check(syncDao.insertChange(change.toEntity()) != -1L) {
            "The local structured preference revision already exists"
        }
        syncDao.upsertOriginState(
            StructuredPreferenceSyncOriginStateEntity(
                categoryName,
                localPeerId,
                originRevision
            )
        )
        syncDao.upsertRecord(change.toRecordEntity())
    }

    private fun materialize(category: StructuredPreferenceCategory) {
        when (category) {
            StructuredPreferenceCategory.FEED_GROUPS -> materializeFeedGroups()

            StructuredPreferenceCategory.HOME_TABS -> materializeHomeTabs()

            StructuredPreferenceCategory.CHANNEL_PROFILES ->
                materializeChannelProfiles()

            StructuredPreferenceCategory.FILTERS -> materializeFilters()
        }
    }

    private fun materializeFeedGroups() {
        val category = StructuredPreferenceCategory.FEED_GROUPS.name
        val metadataRecords = syncDao.getRecordsByType(
            category,
            StructuredPreferenceRecordType.FEED_GROUP.name
        )
        metadataRecords.filter(StructuredPreferenceSyncRecordEntity::isDeleted)
            .forEach { record ->
                syncDao.getFeedGroupMapping(record.recordId)?.let { mapping ->
                    feedGroupDao.delete(mapping.groupUid)
                }
            }
        metadataRecords.filterNot(StructuredPreferenceSyncRecordEntity::isDeleted)
            .forEach { record ->
                val data = decodeRecord(record).feedGroup
                    ?: throw StructuredPreferenceSyncException(
                        "Stored feed group metadata is invalid"
                    )
                val mapping = syncDao.getFeedGroupMapping(record.recordId)
                var group = mapping?.let { feedGroupDao.getGroupDirect(it.groupUid) }
                if (group == null) {
                    val uid = feedGroupDao.insert(
                        FeedGroupEntity(
                            uid = 0,
                            name = data.name,
                            icon = feedGroupIcon(data.iconId)
                        )
                    )
                    syncDao.upsertFeedGroupMapping(
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
                val mapping = syncDao.getFeedGroupMapping(groupRecord.recordId)
                    ?: return@forEach
                val subscriptionIds = syncDao.getChildRecords(
                    category,
                    groupRecord.recordId
                ).filterNot(StructuredPreferenceSyncRecordEntity::isDeleted)
                    .filter {
                        it.recordType ==
                            StructuredPreferenceRecordType.FEED_GROUP_MEMBERSHIP.name
                    }
                    .mapNotNull { record ->
                        val data = decodeRecord(record).feedGroupMembership
                            ?: return@mapNotNull null
                        subscriptions[data.serviceId to data.subscriptionUrl]?.uid
                    }
                feedGroupDao.updateSubscriptionsForGroup(
                    mapping.groupUid,
                    subscriptionIds
                )
            }

        val order = syncDao.getRecord(
            category,
            StructuredPreferenceRecordId.feedGroupOrder()
        )?.takeUnless(StructuredPreferenceSyncRecordEntity::isDeleted)
            ?.let(::decodeRecord)
            ?.feedGroupOrder
            ?.groupRecordIds
            .orEmpty()
        val orderedUids = order.mapNotNull { recordId ->
            syncDao.getFeedGroupMapping(recordId)?.groupUid
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
        val category = StructuredPreferenceCategory.HOME_TABS.name
        val records = syncDao.getRecordsByType(
            category,
            StructuredPreferenceRecordType.HOME_TAB.name
        ).filterNot(StructuredPreferenceSyncRecordEntity::isDeleted)
            .associateBy(StructuredPreferenceSyncRecordEntity::recordId)
        val order = syncDao.getRecord(
            category,
            StructuredPreferenceRecordId.homeTabOrder()
        )?.takeUnless(StructuredPreferenceSyncRecordEntity::isDeleted)
            ?.let(::decodeRecord)
            ?.homeTabOrder
            ?.tabRecordIds
            .orEmpty()
        val tabs = order.mapNotNull { recordId ->
            records[recordId]?.let(::decodeRecord)?.homeTab?.let(::toLocalHomeTab)
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
        val fields = syncDao.getRecordsByType(
            StructuredPreferenceCategory.CHANNEL_PROFILES.name,
            StructuredPreferenceRecordType.CHANNEL_PROFILE_FIELD.name
        )
        val editor = preferences.edit()
        preferences.all.keys
            .filter(::isChannelProfilePreferenceKey)
            .forEach(editor::remove)
        fields.filterNot(StructuredPreferenceSyncRecordEntity::isDeleted)
            .mapNotNull { decodeRecord(it).channelProfileField }
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
        syncDao.getRecordsByType(
            StructuredPreferenceCategory.FILTERS.name,
            StructuredPreferenceRecordType.FILTER_SET.name
        ).filterNot(StructuredPreferenceSyncRecordEntity::isDeleted)
            .mapNotNull { decodeRecord(it).filterSet }
            .forEach { filter ->
                val spec = specs[filter.filterId] ?: return@forEach
                editor.putStringSet(spec.preferenceKey, filter.values.toSet())
            }
        editor.commit()
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
                val recordId = syncDao.getFeedGroupMapping(tab.feedGroupId)
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
                val mapping = syncDao.getFeedGroupMapping(
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
                JSON.encodeToString(
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
                JSON.encodeToString(currentChannelProfileFields())

            StructuredPreferenceCategory.FILTERS -> JSON.encodeToString(
                filterSpecs().map { spec ->
                    FilterSnapshot(spec.id, currentFilterValues(spec).sorted())
                }
            )
        }
        return digest(snapshot)
    }

    private fun saveSnapshot(category: StructuredPreferenceCategory) {
        syncDao.upsertLocalState(
            StructuredPreferenceSyncLocalStateEntity(
                category = category.name,
                snapshotHash = snapshotHash(category)
            )
        )
    }

    private fun advanceKnownRevision(
        category: StructuredPreferenceCategory,
        originPeerId: String
    ) {
        var contiguous = syncDao.getOriginState(category.name, originPeerId)
            ?.contiguousRevision
            ?: 0
        while (
            contiguous < MAX_SYNC_REVISION &&
            syncDao.hasChange(category.name, originPeerId, contiguous + 1)
        ) {
            contiguous += 1
        }
        syncDao.upsertOriginState(
            StructuredPreferenceSyncOriginStateEntity(
                category.name,
                originPeerId,
                contiguous
            )
        )
    }

    private fun incrementVersion(value: Long): Long {
        if (value >= MAX_SYNC_REVISION) {
            throw StructuredPreferenceSyncException(
                "The structured preference synchronization revision limit was reached"
            )
        }
        return value + 1
    }

    private fun decodeChange(
        entity: StructuredPreferenceSyncChangeEntity
    ): StructuredPreferenceChange {
        val record = try {
            JSON.decodeFromString<SyncedStructuredPreferenceRecord>(entity.recordJson)
        } catch (error: Exception) {
            throw StructuredPreferenceSyncException(
                "Stored structured preference change data is invalid",
                error
            )
        }
        return StructuredPreferenceChange(
            category = StructuredPreferenceCategory.valueOf(entity.category),
            originPeerId = entity.originPeerId,
            originRevision = entity.originRevision,
            lamportVersion = entity.lamportVersion,
            recordId = entity.recordId,
            recordType = StructuredPreferenceRecordType.valueOf(entity.recordType),
            parentRecordId = entity.parentRecordId,
            type = StructuredPreferenceChangeType.valueOf(entity.changeType),
            record = record
        )
    }

    private fun decodeRecord(
        entity: StructuredPreferenceSyncRecordEntity
    ): SyncedStructuredPreferenceRecord {
        return try {
            JSON.decodeFromString(entity.recordJson)
        } catch (error: Exception) {
            throw StructuredPreferenceSyncException(
                "Stored structured preference record data is invalid",
                error
            )
        }
    }

    private fun StructuredPreferenceChange.toEntity() = StructuredPreferenceSyncChangeEntity(
        category = category.name,
        originPeerId = originPeerId,
        originRevision = originRevision,
        lamportVersion = lamportVersion,
        recordId = recordId,
        recordType = recordType.name,
        parentRecordId = parentRecordId,
        changeType = type.name,
        recordJson = JSON.encodeToString(requireNotNull(record))
    )

    private fun StructuredPreferenceChange.toRecordEntity() = StructuredPreferenceSyncRecordEntity(
        category = category.name,
        recordId = recordId,
        recordType = recordType.name,
        parentRecordId = parentRecordId,
        lamportVersion = lamportVersion,
        originPeerId = originPeerId,
        originRevision = originRevision,
        isDeleted = type == StructuredPreferenceChangeType.DELETE,
        recordJson = JSON.encodeToString(requireNotNull(record))
    )

    private val StructuredPreferenceSyncRecordEntity.versionStamp:
        StructuredPreferenceVersionStamp
        get() = StructuredPreferenceVersionStamp(
            lamportVersion,
            originPeerId,
            originRevision
        )

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
        private val JSON = Json {
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = false
        }

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
