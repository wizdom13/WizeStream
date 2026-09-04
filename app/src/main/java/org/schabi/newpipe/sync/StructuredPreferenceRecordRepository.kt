/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.sync.StructuredPreferenceSyncChangeEntity
import org.schabi.newpipe.database.sync.StructuredPreferenceSyncFeedGroupMapEntity
import org.schabi.newpipe.database.sync.StructuredPreferenceSyncLocalStateEntity
import org.schabi.newpipe.database.sync.StructuredPreferenceSyncOriginStateEntity
import org.schabi.newpipe.database.sync.StructuredPreferenceSyncPeerStateEntity
import org.schabi.newpipe.database.sync.StructuredPreferenceSyncRecordEntity

/**
 * Owns the persisted change log, materialized records and peer revision knowledge used by every
 * structured preference category.
 *
 * Category adapters remain responsible for reading and writing their application data. Keeping
 * the generic synchronization bookkeeping here prevents each adapter from implementing Lamport
 * ordering, contiguous revisions and entity serialization independently.
 */
internal class StructuredPreferenceRecordRepository(
    private val database: AppDatabase,
    private val localPeerId: String
) {
    private val syncDao = database.structuredPreferenceSyncDAO()

    fun hasSnapshot(category: StructuredPreferenceCategory): Boolean {
        return syncDao.getLocalState(category.name) != null
    }

    fun isSnapshotCurrent(category: StructuredPreferenceCategory, snapshotHash: String): Boolean {
        return syncDao.getLocalState(category.name)?.snapshotHash == snapshotHash
    }

    fun saveSnapshot(category: StructuredPreferenceCategory, snapshotHash: String) {
        syncDao.upsertLocalState(
            StructuredPreferenceSyncLocalStateEntity(
                category = category.name,
                snapshotHash = snapshotHash
            )
        )
    }

    fun getKnownRevisions(category: StructuredPreferenceCategory): Map<String, Long> {
        return syncDao.getOriginStates(category.name).associate {
            it.originPeerId to it.contiguousRevision
        }
    }

    fun getPendingChanges(
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

    fun acknowledgePeer(
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

    fun applyChanges(
        category: StructuredPreferenceCategory,
        changes: List<StructuredPreferenceChange>,
        materializeChangedRecords: () -> Unit
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
                if (current == null || change.versionStamp > current.versionStamp) {
                    syncDao.upsertRecord(change.toRecordEntity())
                    affectedRecords += 1
                }
            }
            if (affectedRecords > 0) {
                materializeChangedRecords()
            }
            StructuredPreferenceApplyResult(
                acceptedChanges = acceptedChanges,
                affectedRecords = affectedRecords
            )
        }
    }

    fun clearPeerKnowledge() {
        syncDao.deleteAllPeerStates()
    }

    fun getRecord(
        category: StructuredPreferenceCategory,
        recordId: String
    ): StructuredPreferenceSyncRecordEntity? {
        return syncDao.getRecord(category.name, recordId)
    }

    fun getRecordsByType(
        category: StructuredPreferenceCategory,
        recordType: StructuredPreferenceRecordType
    ): List<StructuredPreferenceSyncRecordEntity> {
        return syncDao.getRecordsByType(category.name, recordType.name)
    }

    fun getChildRecords(
        category: StructuredPreferenceCategory,
        parentRecordId: String
    ): List<StructuredPreferenceSyncRecordEntity> {
        return syncDao.getChildRecords(category.name, parentRecordId)
    }

    fun getFeedGroupMapping(recordId: String): StructuredPreferenceSyncFeedGroupMapEntity? {
        return syncDao.getFeedGroupMapping(recordId)
    }

    fun getFeedGroupMapping(groupUid: Long): StructuredPreferenceSyncFeedGroupMapEntity? {
        return syncDao.getFeedGroupMapping(groupUid)
    }

    fun saveFeedGroupMapping(mapping: StructuredPreferenceSyncFeedGroupMapEntity) {
        syncDao.upsertFeedGroupMapping(mapping)
    }

    fun saveLocalUpsert(
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

    fun saveLocalDelete(current: StructuredPreferenceSyncRecordEntity) {
        saveLocalChange(
            category = StructuredPreferenceCategory.valueOf(current.category),
            recordId = current.recordId,
            recordType = StructuredPreferenceRecordType.valueOf(current.recordType),
            parentRecordId = current.parentRecordId,
            type = StructuredPreferenceChangeType.DELETE,
            record = decodeRecord(current)
        )
    }

    fun decodeRecord(
        entity: StructuredPreferenceSyncRecordEntity
    ): SyncedStructuredPreferenceRecord {
        return try {
            STRUCTURED_PREFERENCE_JSON.decodeFromString(entity.recordJson)
        } catch (error: Exception) {
            throw StructuredPreferenceSyncException(
                "Stored structured preference record data is invalid",
                error
            )
        }
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
            STRUCTURED_PREFERENCE_JSON.decodeFromString<SyncedStructuredPreferenceRecord>(
                entity.recordJson
            )
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

    private fun StructuredPreferenceChange.toEntity() = StructuredPreferenceSyncChangeEntity(
        category = category.name,
        originPeerId = originPeerId,
        originRevision = originRevision,
        lamportVersion = lamportVersion,
        recordId = recordId,
        recordType = recordType.name,
        parentRecordId = parentRecordId,
        changeType = type.name,
        recordJson = STRUCTURED_PREFERENCE_JSON.encodeToString(requireNotNull(record))
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
        recordJson = STRUCTURED_PREFERENCE_JSON.encodeToString(requireNotNull(record))
    )

    private val StructuredPreferenceSyncRecordEntity.versionStamp:
        StructuredPreferenceVersionStamp
        get() = StructuredPreferenceVersionStamp(
            lamportVersion,
            originPeerId,
            originRevision
        )
}

internal val STRUCTURED_PREFERENCE_JSON = Json {
    encodeDefaults = true
    explicitNulls = false
    ignoreUnknownKeys = false
}
