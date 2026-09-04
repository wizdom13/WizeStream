/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.sync.StructuredPreferenceSyncFeedGroupMapEntity
import org.schabi.newpipe.database.sync.StructuredPreferenceSyncRecordEntity
import org.schabi.newpipe.local.subscription.FeedGroupIcon

internal class FeedGroupSyncAdapter(
    database: AppDatabase,
    private val recordRepository: StructuredPreferenceRecordRepository
) : StructuredPreferenceCategoryAdapter {
    override val category = StructuredPreferenceCategory.FEED_GROUPS

    private val feedGroupDao = database.feedGroupDAO()
    private val subscriptionDao = database.subscriptionDAO()

    override fun snapshotHash(): String {
        val subscriptions = subscriptionDao.getAllDirect().associateBy { it.uid }
        val snapshot = feedGroupDao.getAllDirect().map { group ->
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
        return structuredPreferenceDigest(
            STRUCTURED_PREFERENCE_JSON.encodeToString(snapshot)
        )
    }

    override fun reconcile(bootstrap: Boolean) {
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
                category = category,
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
            category,
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
                        category = category,
                        recordId = recordId,
                        recordType = StructuredPreferenceRecordType.FEED_GROUP_MEMBERSHIP,
                        parentRecordId = groupRecordId,
                        record = SyncedStructuredPreferenceRecord(
                            feedGroupMembership = membership
                        )
                    )
                }
        }
        recordRepository.getRecordsByType(
            category,
            StructuredPreferenceRecordType.FEED_GROUP_MEMBERSHIP
        ).filterNot(StructuredPreferenceSyncRecordEntity::isDeleted)
            .filterNot { it.recordId in desiredMemberships }
            .forEach(recordRepository::saveLocalDelete)

        recordRepository.saveLocalUpsert(
            category = category,
            recordId = StructuredPreferenceRecordId.feedGroupOrder(),
            recordType = StructuredPreferenceRecordType.FEED_GROUP_ORDER,
            record = SyncedStructuredPreferenceRecord(
                feedGroupOrder = SyncedFeedGroupOrder(
                    groups.map { requireNotNull(mappings[it]).groupRecordId }
                )
            )
        )
    }

    override fun materialize() {
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

    private fun feedGroupIcon(iconId: Int): FeedGroupIcon {
        return FeedGroupIcon.entries.firstOrNull { it.id == iconId }
            ?: throw StructuredPreferenceSyncException("Unknown feed group icon")
    }

    @Serializable
    private data class FeedGroupSnapshot(
        val uid: Long,
        val name: String,
        val iconId: Int,
        val sortOrder: Long,
        val memberships: List<String>
    )
}
