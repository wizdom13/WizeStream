/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

internal class TestStructuredPreferenceSyncStore(
    override val localPeerId: String
) : StructuredPreferenceSyncStore {
    private val journal =
        linkedMapOf<Triple<StructuredPreferenceCategory, String, Long>, StructuredPreferenceChange>()
    private val knownRevisions =
        linkedMapOf<StructuredPreferenceCategory, MutableMap<String, Long>>()
    private val peerKnowledge =
        linkedMapOf<Pair<StructuredPreferenceCategory, String>, MutableMap<String, Long>>()
    private val records =
        linkedMapOf<StructuredPreferenceCategory, MutableMap<String, StructuredPreferenceChange>>()
    private val localRevisions = linkedMapOf<StructuredPreferenceCategory, Long>()
    private var lamportVersion = 0L

    fun upsert(
        category: StructuredPreferenceCategory,
        recordId: String,
        recordType: StructuredPreferenceRecordType,
        record: SyncedStructuredPreferenceRecord,
        parentRecordId: String? = null
    ) {
        recordLocalChange(
            category,
            recordId,
            recordType,
            parentRecordId,
            StructuredPreferenceChangeType.UPSERT,
            record
        )
    }

    fun delete(
        category: StructuredPreferenceCategory,
        recordId: String
    ) {
        val current = requireNotNull(records[category]?.get(recordId))
        recordLocalChange(
            category,
            recordId,
            current.recordType,
            current.parentRecordId,
            StructuredPreferenceChangeType.DELETE,
            requireNotNull(current.record)
        )
    }

    fun liveRecords(
        category: StructuredPreferenceCategory,
        type: StructuredPreferenceRecordType
    ): List<SyncedStructuredPreferenceRecord> {
        return records[category].orEmpty().values
            .filter { it.recordType == type }
            .filter { it.type != StructuredPreferenceChangeType.DELETE }
            .map { requireNotNull(it.record) }
    }

    override fun reconcileLocal(category: StructuredPreferenceCategory) = Unit

    override fun getKnownRevisions(
        category: StructuredPreferenceCategory
    ): Map<String, Long> {
        return knownRevisions[category].orEmpty().toMap()
    }

    override fun getPendingChanges(
        category: StructuredPreferenceCategory,
        peerId: String,
        limit: Int
    ): StructuredPreferenceChangeBatch {
        val acknowledged = peerKnowledge[category to peerId].orEmpty()
        val pending = journal.values
            .filter { change ->
                change.category == category &&
                    change.originRevision >
                    (acknowledged[change.originPeerId] ?: 0)
            }
            .sortedBy(StructuredPreferenceChange::versionStamp)
        return StructuredPreferenceChangeBatch(
            changes = pending.take(limit),
            hasMore = pending.size > limit
        )
    }

    override fun acknowledgePeer(
        category: StructuredPreferenceCategory,
        peerId: String,
        knownRevisions: Map<String, Long>
    ) {
        val knowledge = peerKnowledge.getOrPut(category to peerId) {
            linkedMapOf()
        }
        val localKnowledge = this.knownRevisions[category].orEmpty()
        knownRevisions.forEach { (origin, revision) ->
            val safeRevision = minOf(revision, localKnowledge[origin] ?: 0)
            knowledge[origin] = maxOf(knowledge[origin] ?: 0, safeRevision)
        }
    }

    override fun applyChanges(
        category: StructuredPreferenceCategory,
        changes: List<StructuredPreferenceChange>
    ): StructuredPreferenceApplyResult {
        StructuredPreferenceSyncValidation.validateChanges(category, changes)
        var accepted = 0
        var affected = 0
        changes.forEach { change ->
            val changeId = Triple(
                change.category,
                change.originPeerId,
                change.originRevision
            )
            if (journal.containsKey(changeId)) {
                return@forEach
            }
            journal[changeId] = change
            accepted += 1
            lamportVersion = maxOf(lamportVersion, change.lamportVersion)
            advanceKnownRevision(category, change.originPeerId)
            val categoryRecords = records.getOrPut(category) { linkedMapOf() }
            val existing = categoryRecords[change.recordId]
            if (existing != null && change.versionStamp <= existing.versionStamp) {
                return@forEach
            }
            categoryRecords[change.recordId] = change
            affected += 1
        }
        return StructuredPreferenceApplyResult(accepted, affected)
    }

    override fun clearPeerKnowledge() {
        peerKnowledge.clear()
    }

    private fun recordLocalChange(
        category: StructuredPreferenceCategory,
        recordId: String,
        recordType: StructuredPreferenceRecordType,
        parentRecordId: String?,
        type: StructuredPreferenceChangeType,
        record: SyncedStructuredPreferenceRecord
    ) {
        val categoryRecords = records.getOrPut(category) { linkedMapOf() }
        val existing = categoryRecords[recordId]
        val localRevision = (localRevisions[category] ?: 0) + 1
        localRevisions[category] = localRevision
        lamportVersion = maxOf(
            lamportVersion,
            existing?.lamportVersion ?: 0
        ) + 1
        val change = StructuredPreferenceChange(
            category = category,
            originPeerId = localPeerId,
            originRevision = localRevision,
            lamportVersion = lamportVersion,
            recordId = recordId,
            recordType = recordType,
            parentRecordId = parentRecordId,
            type = type,
            record = record
        )
        StructuredPreferenceSyncValidation.validateChanges(category, listOf(change))
        journal[Triple(category, localPeerId, localRevision)] = change
        knownRevisions.getOrPut(category) { linkedMapOf() }[localPeerId] =
            localRevision
        categoryRecords[recordId] = change
    }

    private fun advanceKnownRevision(
        category: StructuredPreferenceCategory,
        originPeerId: String
    ) {
        val categoryKnowledge = knownRevisions.getOrPut(category) {
            linkedMapOf()
        }
        var contiguous = categoryKnowledge[originPeerId] ?: 0
        while (
            journal.containsKey(
                Triple(category, originPeerId, contiguous + 1)
            )
        ) {
            contiguous += 1
        }
        categoryKnowledge[originPeerId] = contiguous
    }
}
