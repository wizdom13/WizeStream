/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import java.sql.Connection
import java.sql.ResultSet

internal data class DesktopDesiredRecord(
    val recordId: String,
    val recordType: String,
    val parentRecordId: String? = null,
    val payloadJson: String
)

internal data class DesktopJournalChange(
    val originPeerId: String,
    val originRevision: Long,
    val lamportVersion: Long,
    val recordId: String,
    val recordType: String,
    val parentRecordId: String?,
    val changeType: String,
    val payloadJson: String?
)

internal data class DesktopJournalRecord(
    val recordId: String,
    val recordType: String,
    val parentRecordId: String?,
    val lamportVersion: Long,
    val originPeerId: String,
    val originRevision: Long,
    val isDeleted: Boolean,
    val payloadJson: String?
) {
    val versionStamp: ComparableVersion
        get() = ComparableVersion(lamportVersion, originPeerId, originRevision)
}

internal data class DesktopJournalBatch(
    val changes: List<DesktopJournalChange>,
    val hasMore: Boolean
)

internal data class DesktopApplyResult(
    val acceptedChanges: Int,
    val affectedRecordIds: Set<String>
)

internal data class ComparableVersion(
    val lamportVersion: Long,
    val originPeerId: String,
    val originRevision: Long
) : Comparable<ComparableVersion> {
    override fun compareTo(other: ComparableVersion): Int = compareValuesBy(
        this,
        other,
        ComparableVersion::lamportVersion,
        ComparableVersion::originPeerId,
        ComparableVersion::originRevision
    )
}

/**
 * SQLite implementation of the revision journal shared by every desktop data category.
 *
 * The schema mirrors Android's origin clocks, per-peer acknowledgements, immutable changes and
 * last-writer-wins materialized records. Category-specific stores remain responsible for wire
 * validation and for translating winning records into user-facing tables.
 */
internal class DesktopChangeJournal(
    private val connection: Connection,
    private val localPeerId: String
) {
    fun reconcile(
        namespace: String,
        desiredRecords: Collection<DesktopDesiredRecord>
    ): Set<String> = transaction {
        val desired = desiredRecords.associateBy(DesktopDesiredRecord::recordId)
        val current = records(namespace).associateBy(DesktopJournalRecord::recordId)
        val changed = linkedSetOf<String>()
        desired.values.sortedBy(DesktopDesiredRecord::recordId).forEach { record ->
            val existing = current[record.recordId]
            if (
                existing == null || existing.isDeleted ||
                existing.recordType != record.recordType ||
                existing.parentRecordId != record.parentRecordId ||
                existing.payloadJson != record.payloadJson
            ) {
                appendLocal(namespace, record, "UPSERT", existing)
                changed += record.recordId
            }
        }
        current.values.asSequence()
            .filterNot(DesktopJournalRecord::isDeleted)
            .filterNot { it.recordId in desired }
            .sortedBy(DesktopJournalRecord::recordId)
            .forEach { record ->
                appendLocal(
                    namespace,
                    DesktopDesiredRecord(
                        record.recordId,
                        record.recordType,
                        record.parentRecordId,
                        requireNotNull(record.payloadJson)
                    ),
                    "DELETE",
                    record
                )
                changed += record.recordId
            }
        changed
    }

    fun knownRevisions(namespace: String): Map<String, Long> = synchronized(connection) {
        connection.prepareStatement(
            "SELECT origin_peer_id, contiguous_revision FROM sync_origins " +
                "WHERE namespace=? AND contiguous_revision>0 ORDER BY origin_peer_id"
        ).use { statement ->
            statement.setString(1, namespace)
            statement.executeQuery().use { rows ->
                buildMap {
                    while (rows.next()) put(rows.getString(1), rows.getLong(2))
                }
            }
        }
    }

    fun pending(namespace: String, peerId: String, limit: Int): DesktopJournalBatch =
        synchronized(connection) {
            require(limit > 0)
            connection.prepareStatement(
                """SELECT c.origin_peer_id, c.origin_revision, c.lamport_version,
                    c.record_id, c.record_type, c.parent_record_id, c.change_type, c.payload_json
                    FROM sync_changes c
                    LEFT JOIN sync_peers p ON p.namespace=c.namespace AND p.peer_id=?
                      AND p.origin_peer_id=c.origin_peer_id
                    WHERE c.namespace=? AND c.origin_revision>COALESCE(p.acknowledged_revision, 0)
                    ORDER BY c.lamport_version, c.origin_peer_id, c.origin_revision
                    LIMIT ?"""
            ).use { statement ->
                statement.setString(1, peerId)
                statement.setString(2, namespace)
                statement.setInt(3, limit + 1)
                statement.executeQuery().use { rows ->
                    val values = buildList {
                        while (rows.next()) add(rows.toChange())
                    }
                    DesktopJournalBatch(values.take(limit), values.size > limit)
                }
            }
        }

    fun acknowledge(namespace: String, peerId: String, knownRevisions: Map<String, Long>) {
        transaction {
            val local = knownRevisions(namespace)
            knownRevisions.forEach { (origin, claimed) ->
                val safe = minOf(claimed, local[origin] ?: 0L)
                if (safe <= 0) return@forEach
                connection.prepareStatement(
                    """INSERT INTO sync_peers(namespace, peer_id, origin_peer_id,
                        acknowledged_revision) VALUES (?, ?, ?, ?)
                        ON CONFLICT(namespace, peer_id, origin_peer_id) DO UPDATE SET
                        acknowledged_revision=max(sync_peers.acknowledged_revision,
                        excluded.acknowledged_revision)"""
                ).use { statement ->
                    statement.setString(1, namespace)
                    statement.setString(2, peerId)
                    statement.setString(3, origin)
                    statement.setLong(4, safe)
                    statement.executeUpdate()
                }
            }
        }
    }

    fun apply(namespace: String, changes: List<DesktopJournalChange>): DesktopApplyResult =
        transaction {
            val maximumLamport = maximumLamport(namespace)
            if (changes.any { it.lamportVersion > maximumLamport + MAX_REMOTE_LAMPORT_ADVANCE }) {
                throw IllegalArgumentException("A synchronization change advances the logical clock too far")
            }
            var accepted = 0
            val affected = linkedSetOf<String>()
            changes.forEach { change ->
                val inserted = connection.prepareStatement(
                    """INSERT OR IGNORE INTO sync_changes(namespace, origin_peer_id,
                        origin_revision, lamport_version, record_id, record_type,
                        parent_record_id, change_type, payload_json)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"""
                ).use { statement ->
                    statement.setString(1, namespace)
                    statement.setString(2, change.originPeerId)
                    statement.setLong(3, change.originRevision)
                    statement.setLong(4, change.lamportVersion)
                    statement.setString(5, change.recordId)
                    statement.setString(6, change.recordType)
                    statement.setString(7, change.parentRecordId)
                    statement.setString(8, change.changeType)
                    statement.setString(9, change.payloadJson)
                    statement.executeUpdate() == 1
                }
                if (!inserted) return@forEach
                accepted += 1
                advanceContiguousRevision(namespace, change.originPeerId)
                val current = record(namespace, change.recordId)
                val incoming = ComparableVersion(
                    change.lamportVersion,
                    change.originPeerId,
                    change.originRevision
                )
                if (current == null || incoming > current.versionStamp) {
                    upsertRecord(
                        namespace,
                        change,
                        change.payloadJson ?: current?.payloadJson
                    )
                    affected += change.recordId
                }
            }
            DesktopApplyResult(accepted, affected)
        }

    fun records(namespace: String): List<DesktopJournalRecord> = synchronized(connection) {
        connection.prepareStatement(
            """SELECT record_id, record_type, parent_record_id, lamport_version,
                origin_peer_id, origin_revision, is_deleted, payload_json
                FROM sync_records WHERE namespace=?"""
        ).use { statement ->
            statement.setString(1, namespace)
            statement.executeQuery().use { rows ->
                buildList { while (rows.next()) add(rows.toRecord()) }
            }
        }
    }

    fun record(namespace: String, recordId: String): DesktopJournalRecord? =
        synchronized(connection) {
            connection.prepareStatement(
                """SELECT record_id, record_type, parent_record_id, lamport_version,
                    origin_peer_id, origin_revision, is_deleted, payload_json
                    FROM sync_records WHERE namespace=? AND record_id=?"""
            ).use { statement ->
                statement.setString(1, namespace)
                statement.setString(2, recordId)
                statement.executeQuery().use { rows -> if (rows.next()) rows.toRecord() else null }
            }
        }

    fun clearPeerKnowledge() {
        synchronized(connection) {
            connection.createStatement().use { it.executeUpdate("DELETE FROM sync_peers") }
        }
    }

    private fun appendLocal(
        namespace: String,
        desired: DesktopDesiredRecord,
        changeType: String,
        current: DesktopJournalRecord?
    ) {
        val revision = (knownRevision(namespace, localPeerId) + 1).also(::requireValidRevision)
        val lamport = (maxOf(maximumLamport(namespace), current?.lamportVersion ?: 0) + 1)
            .also(::requireValidRevision)
        val change = DesktopJournalChange(
            localPeerId,
            revision,
            lamport,
            desired.recordId,
            desired.recordType,
            desired.parentRecordId,
            changeType,
            desired.payloadJson
        )
        connection.prepareStatement(
            """INSERT INTO sync_changes(namespace, origin_peer_id, origin_revision,
                lamport_version, record_id, record_type, parent_record_id, change_type,
                payload_json) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"""
        ).use { statement ->
            statement.setString(1, namespace)
            statement.setString(2, localPeerId)
            statement.setLong(3, revision)
            statement.setLong(4, lamport)
            statement.setString(5, desired.recordId)
            statement.setString(6, desired.recordType)
            statement.setString(7, desired.parentRecordId)
            statement.setString(8, changeType)
            statement.setString(9, desired.payloadJson)
            check(statement.executeUpdate() == 1)
        }
        setKnownRevision(namespace, localPeerId, revision)
        upsertRecord(namespace, change, desired.payloadJson)
    }

    private fun upsertRecord(
        namespace: String,
        change: DesktopJournalChange,
        payloadJson: String?
    ) {
        connection.prepareStatement(
            """INSERT INTO sync_records(namespace, record_id, record_type,
                parent_record_id, lamport_version, origin_peer_id, origin_revision,
                is_deleted, payload_json) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(namespace, record_id) DO UPDATE SET
                record_type=excluded.record_type, parent_record_id=excluded.parent_record_id,
                lamport_version=excluded.lamport_version, origin_peer_id=excluded.origin_peer_id,
                origin_revision=excluded.origin_revision, is_deleted=excluded.is_deleted,
                payload_json=excluded.payload_json"""
        ).use { statement ->
            statement.setString(1, namespace)
            statement.setString(2, change.recordId)
            statement.setString(3, change.recordType)
            statement.setString(4, change.parentRecordId)
            statement.setLong(5, change.lamportVersion)
            statement.setString(6, change.originPeerId)
            statement.setLong(7, change.originRevision)
            statement.setInt(8, if (change.changeType == "DELETE") 1 else 0)
            statement.setString(9, payloadJson)
            statement.executeUpdate()
        }
    }

    private fun maximumLamport(namespace: String): Long = connection.prepareStatement(
        "SELECT COALESCE(MAX(lamport_version), 0) FROM sync_changes WHERE namespace=?"
    ).use { statement ->
        statement.setString(1, namespace)
        statement.executeQuery().use { rows -> rows.next(); rows.getLong(1) }
    }

    private fun knownRevision(namespace: String, origin: String): Long =
        connection.prepareStatement(
            "SELECT contiguous_revision FROM sync_origins WHERE namespace=? AND origin_peer_id=?"
        ).use { statement ->
            statement.setString(1, namespace)
            statement.setString(2, origin)
            statement.executeQuery().use { rows -> if (rows.next()) rows.getLong(1) else 0L }
        }

    private fun setKnownRevision(namespace: String, origin: String, revision: Long) {
        connection.prepareStatement(
            """INSERT INTO sync_origins(namespace, origin_peer_id, contiguous_revision)
                VALUES (?, ?, ?) ON CONFLICT(namespace, origin_peer_id) DO UPDATE SET
                contiguous_revision=excluded.contiguous_revision"""
        ).use { statement ->
            statement.setString(1, namespace)
            statement.setString(2, origin)
            statement.setLong(3, revision)
            statement.executeUpdate()
        }
    }

    private fun advanceContiguousRevision(namespace: String, origin: String) {
        var revision = knownRevision(namespace, origin)
        while (revision < MAX_SYNC_REVISION && hasChange(namespace, origin, revision + 1)) {
            revision += 1
        }
        setKnownRevision(namespace, origin, revision)
    }

    private fun hasChange(namespace: String, origin: String, revision: Long): Boolean =
        connection.prepareStatement(
            """SELECT 1 FROM sync_changes WHERE namespace=? AND origin_peer_id=?
                AND origin_revision=? LIMIT 1"""
        ).use { statement ->
            statement.setString(1, namespace)
            statement.setString(2, origin)
            statement.setLong(3, revision)
            statement.executeQuery().use(ResultSet::next)
        }

    private fun requireValidRevision(value: Long) {
        if (value !in 1..MAX_SYNC_REVISION) {
            throw IllegalStateException("The synchronization journal version is exhausted")
        }
    }

    private fun ResultSet.toChange() = DesktopJournalChange(
        getString(1),
        getLong(2),
        getLong(3),
        getString(4),
        getString(5),
        getString(6),
        getString(7),
        getString(8)
    )

    private fun ResultSet.toRecord() = DesktopJournalRecord(
        getString(1),
        getString(2),
        getString(3),
        getLong(4),
        getString(5),
        getLong(6),
        getInt(7) != 0,
        getString(8)
    )

    private fun <T> transaction(block: () -> T): T = synchronized(connection) {
        val wasAutoCommit = connection.autoCommit
        if (wasAutoCommit) connection.autoCommit = false
        try {
            block().also { if (wasAutoCommit) connection.commit() }
        } catch (error: Exception) {
            if (wasAutoCommit) connection.rollback()
            throw error
        } finally {
            if (wasAutoCommit) connection.autoCommit = true
        }
    }

    companion object {
        private const val MAX_REMOTE_LAMPORT_ADVANCE = 1_000_000L
    }
}
