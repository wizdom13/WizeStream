/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import java.sql.Connection
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Lossless desktop store for portable settings and structured library metadata.
 *
 * Some records (for example Android home-tab layout) have no desktop UI yet. They are still kept
 * as typed, validated v1 payloads so the desktop can relay them without interpreting or dropping
 * data. Settings exposed by the desktop later can be materialized from this same table.
 */
internal class DesktopStructuredPreferenceSyncStore(
    private val connection: Connection,
    override val localPeerId: String,
    private val journal: DesktopChangeJournal
) : StructuredPreferenceSyncStore {
    fun recordCompletedDownload(
        sourceUrl: String,
        displayName: String,
        mimeType: String,
        sizeBytes: Long,
        completedAtEpochMillis: Long,
        mediaKind: String,
        requestedSyncId: String? = null
    ): String {
        val syncId = requestedSyncId?.let { UUID.fromString(it).toString() } ?: UUID.randomUUID().toString()
        val download = SyncedCompletedDownload(
            syncId,
            localPeerId,
            sourceUrl,
            displayName,
            mimeType,
            sizeBytes,
            completedAtEpochMillis,
            mediaKind
        )
        val record = SyncedStructuredPreferenceRecord(completedDownload = download)
        StructuredPreferenceSyncValidation.validateChanges(
            StructuredPreferenceCategory.COMPLETED_DOWNLOADS,
            listOf(
                StructuredPreferenceChange(
                    StructuredPreferenceCategory.COMPLETED_DOWNLOADS,
                    localPeerId,
                    1,
                    1,
                    syncId,
                    StructuredPreferenceRecordType.COMPLETED_DOWNLOAD,
                    null,
                    StructuredPreferenceChangeType.UPSERT,
                    record
                )
            )
        )
        synchronized(connection) {
            connection.prepareStatement(
                """INSERT INTO portable_records(category, record_id, record_type,
                    parent_record_id, payload_json) VALUES (?, ?, ?, NULL, ?)
                    ON CONFLICT(category, record_id) DO UPDATE SET
                    record_type=excluded.record_type,
                    parent_record_id=NULL,
                    payload_json=excluded.payload_json"""
            ).use { statement ->
                statement.setString(1, StructuredPreferenceCategory.COMPLETED_DOWNLOADS.name)
                statement.setString(2, syncId)
                statement.setString(3, StructuredPreferenceRecordType.COMPLETED_DOWNLOAD.name)
                statement.setString(4, JSON.encodeToString(record))
                statement.executeUpdate()
            }
        }
        return syncId
    }

    override fun reconcileLocal(category: StructuredPreferenceCategory) {
        val desired = synchronized(connection) {
            connection.prepareStatement(
                """SELECT record_id, record_type, parent_record_id, payload_json
                    FROM portable_records WHERE category=? ORDER BY record_id"""
            ).use { statement ->
                statement.setString(1, category.name)
                statement.executeQuery().use { rows ->
                    buildList {
                        while (rows.next()) {
                            val payload = rows.getString(4)
                            val decoded = decode(payload)
                            StructuredPreferenceSyncValidation.validateChanges(
                                category,
                                listOf(
                                    StructuredPreferenceChange(
                                        category,
                                        localPeerId,
                                        1,
                                        1,
                                        rows.getString(1),
                                        StructuredPreferenceRecordType.valueOf(rows.getString(2)),
                                        rows.getString(3),
                                        StructuredPreferenceChangeType.UPSERT,
                                        decoded
                                    )
                                )
                            )
                            add(
                                DesktopDesiredRecord(
                                    rows.getString(1),
                                    rows.getString(2),
                                    rows.getString(3),
                                    payload
                                )
                            )
                        }
                    }
                }
            }
        }
        journal.reconcile(namespace(category), desired)
    }

    override fun getKnownRevisions(
        category: StructuredPreferenceCategory
    ): Map<String, Long> = journal.knownRevisions(namespace(category))

    override fun getPendingChanges(
        category: StructuredPreferenceCategory,
        peerId: String,
        limit: Int
    ): StructuredPreferenceChangeBatch {
        val batch = journal.pending(namespace(category), peerId, limit)
        return StructuredPreferenceChangeBatch(
            batch.changes.map { toModel(category, it) },
            batch.hasMore
        )
    }

    override fun acknowledgePeer(
        category: StructuredPreferenceCategory,
        peerId: String,
        knownRevisions: Map<String, Long>
    ) {
        StructuredPreferenceSyncValidation.validateKnownRevisions(knownRevisions)
        journal.acknowledge(namespace(category), peerId, knownRevisions)
    }

    override fun applyChanges(
        category: StructuredPreferenceCategory,
        changes: List<StructuredPreferenceChange>
    ): StructuredPreferenceApplyResult {
        StructuredPreferenceSyncValidation.validateChanges(category, changes)
        val applied = journal.apply(namespace(category), changes.map(::toJournal))
        applied.affectedRecordIds.forEach { materialize(category, it) }
        return StructuredPreferenceApplyResult(
            applied.acceptedChanges,
            applied.affectedRecordIds.size
        )
    }

    override fun clearPeerKnowledge() = journal.clearPeerKnowledge()

    private fun materialize(category: StructuredPreferenceCategory, recordId: String) {
        val record = journal.record(namespace(category), recordId) ?: return
        synchronized(connection) {
            if (record.isDeleted) {
                connection.prepareStatement(
                    "DELETE FROM portable_records WHERE category=? AND record_id=?"
                ).use { statement ->
                    statement.setString(1, category.name)
                    statement.setString(2, recordId)
                    statement.executeUpdate()
                }
                return
            }
            val payload = requireNotNull(record.payloadJson)
            decode(payload)
            connection.prepareStatement(
                """INSERT INTO portable_records(category, record_id, record_type,
                    parent_record_id, payload_json) VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT(category, record_id) DO UPDATE SET
                    record_type=excluded.record_type,
                    parent_record_id=excluded.parent_record_id,
                    payload_json=excluded.payload_json"""
            ).use { statement ->
                statement.setString(1, category.name)
                statement.setString(2, record.recordId)
                statement.setString(3, record.recordType)
                statement.setString(4, record.parentRecordId)
                statement.setString(5, payload)
                statement.executeUpdate()
            }
        }
    }

    private fun toModel(
        category: StructuredPreferenceCategory,
        change: DesktopJournalChange
    ) = StructuredPreferenceChange(
        category = category,
        originPeerId = change.originPeerId,
        originRevision = change.originRevision,
        lamportVersion = change.lamportVersion,
        recordId = change.recordId,
        recordType = StructuredPreferenceRecordType.valueOf(change.recordType),
        parentRecordId = change.parentRecordId,
        type = StructuredPreferenceChangeType.valueOf(change.changeType),
        record = change.payloadJson?.let(::decode)
    )

    private fun toJournal(change: StructuredPreferenceChange): DesktopJournalChange {
        val payload = change.record?.let { JSON.encodeToString(it) }
            ?: journal.record(namespace(change.category), change.recordId)?.payloadJson
        return DesktopJournalChange(
            change.originPeerId,
            change.originRevision,
            change.lamportVersion,
            change.recordId,
            change.recordType.name,
            change.parentRecordId,
            change.type.name,
            payload
        )
    }

    private fun decode(value: String): SyncedStructuredPreferenceRecord = try {
        JSON.decodeFromString(value)
    } catch (error: Exception) {
        throw StructuredPreferenceSyncException(
            "Stored structured preference synchronization data is malformed",
            error
        )
    }

    private fun namespace(category: StructuredPreferenceCategory) =
        "structured:${category.name}"

    companion object {
        private val JSON = Json {
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = false
        }
    }
}
