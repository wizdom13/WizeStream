/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import java.sql.Connection
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class DesktopHistorySyncStore(
    private val connection: Connection,
    override val localPeerId: String,
    private val journal: DesktopChangeJournal
) : HistorySyncStore {
    override fun reconcileLocal(category: HistorySyncCategory) {
        val desired = when (category) {
            HistorySyncCategory.WATCH -> desiredWatchRecords()
            HistorySyncCategory.SEARCH -> desiredSearchRecords()
            HistorySyncCategory.LEARNING_NOTES -> desiredLearningRecords()
        }.toMutableList()
        // Clear/query tombstones are journal state, not rows in the public desktop library.
        journal.records(namespace(category)).asSequence()
            .filterNot(DesktopJournalRecord::isDeleted)
            .filter { it.recordType.endsWith("TOMBSTONE") }
            .mapNotNull { record ->
                record.payloadJson?.let {
                    DesktopDesiredRecord(
                        record.recordId,
                        record.recordType,
                        record.parentRecordId,
                        it
                    )
                } ?: if (
                    record.recordType == HistoryRecordType.WATCH_ALL_TOMBSTONE.name ||
                    record.recordType == HistoryRecordType.PLAYBACK_ALL_TOMBSTONE.name ||
                    record.recordType == HistoryRecordType.SEARCH_ALL_TOMBSTONE.name
                ) {
                    DesktopDesiredRecord(record.recordId, record.recordType, payloadJson = NULL_RECORD)
                } else null
            }.forEach(desired::add)
        journal.reconcile(namespace(category), desired)
    }

    override fun getKnownRevisions(category: HistorySyncCategory): Map<String, Long> =
        journal.knownRevisions(namespace(category))

    override fun getPendingChanges(
        category: HistorySyncCategory,
        peerId: String,
        limit: Int
    ): HistoryChangeBatch {
        val batch = journal.pending(namespace(category), peerId, limit)
        return HistoryChangeBatch(batch.changes.map { toModel(category, it) }, batch.hasMore)
    }

    override fun acknowledgePeer(
        category: HistorySyncCategory,
        peerId: String,
        knownRevisions: Map<String, Long>
    ) {
        HistorySyncValidation.validateKnownRevisions(knownRevisions)
        journal.acknowledge(namespace(category), peerId, knownRevisions)
    }

    override fun applyChanges(
        category: HistorySyncCategory,
        changes: List<HistoryChange>
    ): HistoryApplyResult {
        HistorySyncValidation.validateChanges(category, changes)
        val applied = journal.apply(namespace(category), changes.map(::toJournal))
        when (category) {
            HistorySyncCategory.WATCH -> materializeWatch()
            HistorySyncCategory.SEARCH -> materializeSearch()
            HistorySyncCategory.LEARNING_NOTES -> applied.affectedRecordIds.forEach(
                ::materializeLearningNote
            )
        }
        return HistoryApplyResult(applied.acceptedChanges, applied.affectedRecordIds.size)
    }

    override fun clearPeerKnowledge() = journal.clearPeerKnowledge()

    private fun desiredWatchRecords(): List<DesktopDesiredRecord> = synchronized(connection) {
        buildList {
            connection.prepareStatement(
                """SELECT id, service_id, url, title, watched_at, stream_type, duration,
                    uploader, uploader_url, thumbnail_url FROM history ORDER BY watched_at, rowid"""
            ).use { statement ->
                statement.executeQuery().use { rows ->
                    while (rows.next()) {
                        val rowId = rows.getString(1) ?: UUID.randomUUID().toString().also { id ->
                            connection.prepareStatement(
                                """UPDATE history SET id=? WHERE service_id=? AND url=?
                                    AND watched_at=?"""
                            ).use { update ->
                                update.setString(1, id)
                                update.setInt(2, rows.getInt(2))
                                update.setString(3, rows.getString(3))
                                update.setLong(4, rows.getLong(5))
                                update.executeUpdate()
                            }
                        }
                        val stream = historyStream(rows, 2)
                        add(
                            desired(
                                rowId,
                                HistoryRecordType.WATCH_EVENT,
                                SyncedHistoryRecord(
                                    watchEvent = SyncedWatchEvent(
                                        stream,
                                        rows.getLong(5),
                                        1
                                    )
                                )
                            )
                        )
                    }
                }
            }
            connection.prepareStatement(
                """SELECT service_id, url, position_millis, updated_at FROM playback_state
                    ORDER BY service_id, url"""
            ).use { statement ->
                statement.executeQuery().use { rows ->
                    while (rows.next()) {
                        val stream = findStream(rows.getInt(1), rows.getString(2))
                        if (stream != null) {
                            add(
                                desired(
                                    HistoryRecordId.progress(stream.identity),
                                    HistoryRecordType.PLAYBACK_PROGRESS,
                                    SyncedHistoryRecord(
                                        playbackProgress = SyncedPlaybackProgress(
                                            stream,
                                            rows.getLong(3),
                                            rows.getLong(4)
                                        )
                                    )
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun desiredSearchRecords(): List<DesktopDesiredRecord> = synchronized(connection) {
        connection.prepareStatement(
            "SELECT id, service_id, query, searched_at FROM search_history ORDER BY searched_at, id"
        ).use { statement ->
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        add(
                            desired(
                                rows.getString(1),
                                HistoryRecordType.SEARCH_EVENT,
                                SyncedHistoryRecord(
                                    searchEvent = SyncedSearchEvent(
                                        rows.getInt(2),
                                        rows.getString(3),
                                        rows.getLong(4)
                                    )
                                )
                            )
                        )
                    }
                }
            }
        }
    }

    private fun desiredLearningRecords(): List<DesktopDesiredRecord> = synchronized(connection) {
        connection.prepareStatement(
            """SELECT id, service_id, url, title, position_seconds, note, created_at,
                updated_at, stream_type, duration, uploader, uploader_url, thumbnail_url
                FROM learning_notes ORDER BY created_at, id"""
        ).use { statement ->
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        val id = rows.getString(1)
                        val stream = SyncedHistoryStream(
                            serviceId = rows.getInt(2),
                            url = rows.getString(3).trim(),
                            title = rows.getString(4),
                            streamType = rows.getString(9),
                            duration = rows.getLong(10),
                            uploader = rows.getString(11),
                            uploaderUrl = rows.getString(12),
                            thumbnailUrl = rows.getString(13)
                        )
                        add(
                            desired(
                                id,
                                HistoryRecordType.LEARNING_NOTE,
                                SyncedHistoryRecord(
                                    learningNote = SyncedLearningNote(
                                        id,
                                        stream,
                                        rows.getLong(5) * 1_000,
                                        rows.getString(6).trim(),
                                        rows.getLong(7),
                                        rows.getLong(8)
                                    )
                                )
                            )
                        )
                    }
                }
            }
        }
    }

    private fun materializeWatch() {
        val records = journal.records(namespace(HistorySyncCategory.WATCH))
        val identities = records.mapNotNull { it.payloadJson?.takeUnless { value -> value == NULL_RECORD }
            ?.let(::decode)?.stream?.identity }.toSet()
        synchronized(connection) {
            identities.forEach { identity ->
                val matching = records.filter { record ->
                    record.payloadJson?.takeUnless { it == NULL_RECORD }
                        ?.let(::decode)?.stream?.identity == identity
                }
                val globalCutoff = records.firstOrNull {
                    it.recordId == HistoryRecordId.watchAllTombstone()
                }?.versionStamp
                val streamCutoff = records.firstOrNull {
                    it.recordId == HistoryRecordId.watchStreamTombstone(identity)
                }?.versionStamp
                val cutoff = listOfNotNull(globalCutoff, streamCutoff).maxOrNull()
                val events = matching.filter {
                    it.recordType == HistoryRecordType.WATCH_EVENT.name && !it.isDeleted &&
                        (cutoff == null || it.versionStamp > cutoff)
                }
                connection.prepareStatement("DELETE FROM history WHERE service_id=? AND url=?")
                    .use { statement ->
                        statement.setInt(1, identity.serviceId)
                        statement.setString(2, identity.url)
                        statement.executeUpdate()
                    }
                connection.prepareStatement(
                    """INSERT INTO history(service_id, url, title, watched_at, position_seconds,
                        id, stream_type, uploader, uploader_url, thumbnail_url)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""
                ).use { statement ->
                    events.forEach { record ->
                        val event = requireNotNull(decode(requireNotNull(record.payloadJson)).watchEvent)
                        statement.setInt(1, event.stream.serviceId)
                        statement.setString(2, event.stream.url)
                        statement.setString(3, event.stream.title)
                        statement.setLong(4, event.watchedAtEpochMillis)
                        statement.setLong(5, 0)
                        statement.setString(6, record.recordId)
                        statement.setString(7, event.stream.streamType)
                        statement.setString(8, event.stream.uploader)
                        statement.setString(9, event.stream.uploaderUrl)
                        statement.setString(10, event.stream.thumbnailUrl)
                        statement.addBatch()
                    }
                    statement.executeBatch()
                }
                val progress = records.firstOrNull {
                    it.recordId == HistoryRecordId.progress(identity)
                }
                val clear = records.firstOrNull {
                    it.recordId == HistoryRecordId.playbackAllTombstone()
                }
                val cleared = progress == null || progress.isDeleted ||
                    (clear != null && clear.versionStamp >= progress.versionStamp)
                if (cleared) {
                    connection.prepareStatement(
                        "DELETE FROM playback_state WHERE service_id=? AND url=?"
                    ).use { statement ->
                        statement.setInt(1, identity.serviceId)
                        statement.setString(2, identity.url)
                        statement.executeUpdate()
                    }
                } else {
                    val value = requireNotNull(
                        decode(requireNotNull(progress.payloadJson)).playbackProgress
                    )
                    connection.prepareStatement(
                        """INSERT INTO playback_state(service_id, url, position_millis, updated_at)
                            VALUES (?, ?, ?, ?) ON CONFLICT(service_id, url) DO UPDATE SET
                            position_millis=excluded.position_millis, updated_at=excluded.updated_at"""
                    ).use { statement ->
                        statement.setInt(1, identity.serviceId)
                        statement.setString(2, identity.url)
                        statement.setLong(3, value.progressMillis)
                        statement.setLong(4, value.updatedAtEpochMillis)
                        statement.executeUpdate()
                    }
                }
            }
        }
    }

    private fun materializeSearch() {
        val records = journal.records(namespace(HistorySyncCategory.SEARCH))
        val globalCutoff = records.firstOrNull {
            it.recordId == HistoryRecordId.searchAllTombstone()
        }?.versionStamp
        val queryCutoffs = records.filter {
            it.recordType == HistoryRecordType.SEARCH_QUERY_TOMBSTONE.name
        }.associate { record ->
            requireNotNull(decode(requireNotNull(record.payloadJson)).searchQueryTombstone).query to
                record.versionStamp
        }
        val events = records.filter {
            it.recordType == HistoryRecordType.SEARCH_EVENT.name && !it.isDeleted &&
                (globalCutoff == null || it.versionStamp > globalCutoff)
        }.filter { record ->
            val event = requireNotNull(decode(requireNotNull(record.payloadJson)).searchEvent)
            val cutoff = queryCutoffs[event.query]
            cutoff == null || record.versionStamp > cutoff
        }.sortedBy { requireNotNull(decode(requireNotNull(it.payloadJson)).searchEvent).searchedAtEpochMillis }
        synchronized(connection) {
            connection.createStatement().use { it.executeUpdate("DELETE FROM search_history") }
            connection.prepareStatement(
                "INSERT INTO search_history(id, service_id, query, searched_at) VALUES (?, ?, ?, ?)"
            ).use { statement ->
                events.forEach { record ->
                    val event = requireNotNull(decode(requireNotNull(record.payloadJson)).searchEvent)
                    statement.setString(1, record.recordId)
                    statement.setInt(2, event.serviceId)
                    statement.setString(3, event.query)
                    statement.setLong(4, event.searchedAtEpochMillis)
                    statement.addBatch()
                }
                statement.executeBatch()
            }
        }
    }

    private fun materializeLearningNote(noteId: String) {
        val record = journal.record(namespace(HistorySyncCategory.LEARNING_NOTES), noteId) ?: return
        synchronized(connection) {
            if (record.isDeleted) {
                connection.prepareStatement("DELETE FROM learning_notes WHERE id=?").use { statement ->
                    statement.setString(1, noteId)
                    statement.executeUpdate()
                }
                return
            }
            val note = requireNotNull(decode(requireNotNull(record.payloadJson)).learningNote)
            connection.prepareStatement(
                """INSERT INTO learning_notes(id, service_id, url, position_seconds, note,
                    created_at, updated_at, title, stream_type, duration, uploader,
                    uploader_url, thumbnail_url) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET service_id=excluded.service_id, url=excluded.url,
                    position_seconds=excluded.position_seconds, note=excluded.note,
                    created_at=excluded.created_at, updated_at=excluded.updated_at,
                    title=excluded.title, stream_type=excluded.stream_type,
                    duration=excluded.duration, uploader=excluded.uploader,
                    uploader_url=excluded.uploader_url, thumbnail_url=excluded.thumbnail_url"""
            ).use { statement ->
                statement.setString(1, note.noteId)
                statement.setInt(2, note.stream.serviceId)
                statement.setString(3, note.stream.url)
                statement.setLong(4, note.timestampMillis / 1_000)
                statement.setString(5, note.noteText)
                statement.setLong(6, note.createdAtEpochMillis)
                statement.setLong(7, note.updatedAtEpochMillis)
                statement.setString(8, note.stream.title)
                statement.setString(9, note.stream.streamType)
                statement.setLong(10, note.stream.duration)
                statement.setString(11, note.stream.uploader)
                statement.setString(12, note.stream.uploaderUrl)
                statement.setString(13, note.stream.thumbnailUrl)
                statement.executeUpdate()
            }
        }
    }

    private fun findStream(serviceId: Int, url: String): SyncedHistoryStream? =
        connection.prepareStatement(
            """SELECT service_id, url, title, stream_type, duration, uploader,
                uploader_url, thumbnail_url FROM history WHERE service_id=? AND url=?
                ORDER BY watched_at DESC LIMIT 1"""
        ).use { statement ->
            statement.setInt(1, serviceId)
            statement.setString(2, url)
            statement.executeQuery().use { rows ->
                if (!rows.next()) null else SyncedHistoryStream(
                    rows.getInt(1), rows.getString(2), rows.getString(3), rows.getString(4),
                    rows.getLong(5), rows.getString(6), rows.getString(7), rows.getString(8)
                )
            }
        }

    private fun historyStream(rows: java.sql.ResultSet, start: Int) = SyncedHistoryStream(
        serviceId = rows.getInt(start),
        url = rows.getString(start + 1).trim(),
        title = rows.getString(start + 2),
        streamType = rows.getString(start + 4),
        duration = rows.getLong(start + 5),
        uploader = rows.getString(start + 6),
        uploaderUrl = rows.getString(start + 7),
        thumbnailUrl = rows.getString(start + 8)
    )

    private fun desired(
        recordId: String,
        type: HistoryRecordType,
        record: SyncedHistoryRecord
    ) = DesktopDesiredRecord(recordId, type.name, payloadJson = JSON.encodeToString(record))

    private fun toModel(category: HistorySyncCategory, change: DesktopJournalChange) =
        HistoryChange(
            category = category,
            originPeerId = change.originPeerId,
            originRevision = change.originRevision,
            lamportVersion = change.lamportVersion,
            recordId = change.recordId,
            recordType = HistoryRecordType.valueOf(change.recordType),
            type = HistoryChangeType.valueOf(change.changeType),
            record = change.payloadJson?.takeUnless { it == NULL_RECORD }?.let(::decode)
        )

    private fun toJournal(change: HistoryChange): DesktopJournalChange {
        val payload = change.record?.let { JSON.encodeToString(it) }
            ?: journal.record(namespace(change.category), change.recordId)?.payloadJson
            ?: NULL_RECORD
        return DesktopJournalChange(
            change.originPeerId,
            change.originRevision,
            change.lamportVersion,
            change.recordId,
            change.recordType.name,
            null,
            change.type.name,
            payload
        )
    }

    private fun decode(value: String): SyncedHistoryRecord = try {
        JSON.decodeFromString(value)
    } catch (error: Exception) {
        throw HistorySyncException("Stored history synchronization data is malformed", error)
    }

    private val SyncedHistoryRecord.stream: SyncedHistoryStream?
        get() = watchEvent?.stream ?: playbackProgress?.stream ?:
            watchStreamTombstone?.stream ?: learningNote?.stream

    private fun namespace(category: HistorySyncCategory) = "history:${category.name}"

    companion object {
        private const val NULL_RECORD = "{}"
        private val JSON = Json {
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = false
        }
    }
}
