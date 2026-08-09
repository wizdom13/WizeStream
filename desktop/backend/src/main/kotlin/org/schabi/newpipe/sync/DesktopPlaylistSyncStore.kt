/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import java.sql.Connection
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class DesktopPlaylistSyncStore(
    private val connection: Connection,
    override val localPeerId: String,
    private val journal: DesktopChangeJournal
) : PlaylistSyncStore {
    override fun reconcileLocalPlaylists() {
        val desired = mutableListOf<DesktopDesiredRecord>()
        synchronized(connection) {
            connection.prepareStatement(
                """SELECT id, name, is_thumbnail_permanent, thumbnail_service_id,
                    thumbnail_url, display_index FROM playlists ORDER BY display_index, id"""
            ).use { statement ->
                statement.executeQuery().use { playlists ->
                    while (playlists.next()) {
                        val playlistId = playlists.getString(1)
                        requireCanonicalUuid(playlistId)
                        val metadata = SyncedLocalPlaylist(
                            name = playlists.getString(2),
                            isThumbnailPermanent = playlists.getInt(3) != 0,
                            thumbnailServiceId = playlists.getInt(4).takeUnless { playlists.wasNull() },
                            thumbnailUrl = playlists.getString(5),
                            displayIndex = playlists.getLong(6)
                        )
                        desired += desired(
                            playlistId,
                            PlaylistRecordType.LOCAL_PLAYLIST,
                            null,
                            SyncedPlaylistRecord(localPlaylist = metadata)
                        )
                        val itemIds = mutableListOf<String>()
                        connection.prepareStatement(
                            """SELECT position, item_id, service_id, url, title, duration,
                                stream_type, uploader, uploader_url, thumbnail_url
                                FROM playlist_items WHERE playlist_id=? ORDER BY position"""
                        ).use { itemStatement ->
                            itemStatement.setString(1, playlistId)
                            itemStatement.executeQuery().use { items ->
                                while (items.next()) {
                                    val position = items.getInt(1)
                                    val itemId = items.getString(2) ?: UUID.randomUUID().toString().also { value ->
                                        connection.prepareStatement(
                                            "UPDATE playlist_items SET item_id=? WHERE playlist_id=? AND position=?"
                                        ).use { update ->
                                            update.setString(1, value)
                                            update.setString(2, playlistId)
                                            update.setInt(3, position)
                                            update.executeUpdate()
                                        }
                                    }
                                    requireCanonicalUuid(itemId)
                                    itemIds += itemId
                                    val stream = SyncedStream(
                                        serviceId = items.getInt(3),
                                        url = items.getString(4).trim(),
                                        title = items.getString(5),
                                        duration = items.getLong(6),
                                        streamType = items.getString(7),
                                        uploader = items.getString(8),
                                        uploaderUrl = items.getString(9),
                                        thumbnailUrl = items.getString(10)
                                    )
                                    desired += desired(
                                        itemId,
                                        PlaylistRecordType.LOCAL_PLAYLIST_ITEM,
                                        playlistId,
                                        SyncedPlaylistRecord(
                                            localItem = SyncedPlaylistItem(playlistId, stream)
                                        )
                                    )
                                }
                            }
                        }
                        desired += desired(
                            PlaylistRecordId.order(playlistId),
                            PlaylistRecordType.LOCAL_PLAYLIST_ORDER,
                            playlistId,
                            SyncedPlaylistRecord(
                                localOrder = SyncedPlaylistOrder(playlistId, itemIds)
                            )
                        )
                    }
                }
            }
            connection.prepareStatement(
                """SELECT record_id, service_id, url, name, thumbnail_url, uploader,
                    display_index, stream_count FROM remote_playlists ORDER BY display_index, record_id"""
            ).use { statement ->
                statement.executeQuery().use { rows ->
                    while (rows.next()) {
                        val remote = SyncedRemotePlaylist(
                            serviceId = rows.getInt(2),
                            url = rows.getString(3).trim(),
                            name = rows.getString(4),
                            thumbnailUrl = rows.getString(5),
                            uploader = rows.getString(6),
                            displayIndex = rows.getLong(7),
                            streamCount = rows.getLong(8).takeUnless { rows.wasNull() }
                        )
                        desired += desired(
                            rows.getString(1),
                            PlaylistRecordType.REMOTE_PLAYLIST,
                            null,
                            SyncedPlaylistRecord(remotePlaylist = remote)
                        )
                    }
                }
            }
        }
        journal.reconcile(NAMESPACE, desired)
    }

    override fun getKnownRevisions(): Map<String, Long> = journal.knownRevisions(NAMESPACE)

    override fun getPendingChanges(peerId: String, limit: Int): PlaylistChangeBatch {
        val batch = journal.pending(NAMESPACE, peerId, limit)
        return PlaylistChangeBatch(batch.changes.map(::toModel), batch.hasMore)
    }

    override fun acknowledgePeer(peerId: String, knownRevisions: Map<String, Long>) {
        PlaylistSyncValidation.validateKnownRevisions(knownRevisions)
        journal.acknowledge(NAMESPACE, peerId, knownRevisions)
    }

    override fun applyChanges(changes: List<PlaylistChange>): PlaylistApplyResult {
        PlaylistSyncValidation.validateChanges(changes)
        val applied = journal.apply(NAMESPACE, changes.map(::toJournal))
        val localPlaylists = linkedSetOf<String>()
        val remotePlaylists = linkedSetOf<String>()
        applied.affectedRecordIds.forEach { recordId ->
            val record = requireNotNull(journal.record(NAMESPACE, recordId))
            when (PlaylistRecordType.valueOf(record.recordType)) {
                PlaylistRecordType.LOCAL_PLAYLIST -> localPlaylists += record.recordId
                PlaylistRecordType.LOCAL_PLAYLIST_ITEM,
                PlaylistRecordType.LOCAL_PLAYLIST_ORDER -> localPlaylists += requireNotNull(
                    record.parentRecordId
                )
                PlaylistRecordType.REMOTE_PLAYLIST -> remotePlaylists += record.recordId
            }
        }
        localPlaylists.forEach(::materializeLocalPlaylist)
        remotePlaylists.forEach(::materializeRemotePlaylist)
        return PlaylistApplyResult(
            applied.acceptedChanges,
            localPlaylists.size + remotePlaylists.size
        )
    }

    override fun clearPeerKnowledge() = journal.clearPeerKnowledge()

    private fun materializeLocalPlaylist(playlistId: String) {
        val playlistRecord = journal.record(NAMESPACE, playlistId) ?: return
        synchronized(connection) {
            if (playlistRecord.isDeleted) {
                connection.prepareStatement("DELETE FROM playlists WHERE id=?").use { statement ->
                    statement.setString(1, playlistId)
                    statement.executeUpdate()
                }
                return
            }
            val metadata = decode(requireNotNull(playlistRecord.payloadJson)).localPlaylist
                ?: throw PlaylistSyncException("Stored local playlist metadata is invalid")
            connection.prepareStatement(
                """INSERT INTO playlists(id, name, created_at, is_thumbnail_permanent,
                    thumbnail_service_id, thumbnail_url, display_index) VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET name=excluded.name,
                    is_thumbnail_permanent=excluded.is_thumbnail_permanent,
                    thumbnail_service_id=excluded.thumbnail_service_id,
                    thumbnail_url=excluded.thumbnail_url, display_index=excluded.display_index"""
            ).use { statement ->
                statement.setString(1, playlistId)
                statement.setString(2, metadata.name)
                statement.setLong(3, System.currentTimeMillis())
                statement.setInt(4, if (metadata.isThumbnailPermanent) 1 else 0)
                statement.setObject(5, metadata.thumbnailServiceId)
                statement.setString(6, metadata.thumbnailUrl)
                statement.setLong(7, metadata.displayIndex)
                statement.executeUpdate()
            }
            val children = journal.records(NAMESPACE).filter {
                it.parentRecordId == playlistId && !it.isDeleted &&
                    it.recordType == PlaylistRecordType.LOCAL_PLAYLIST_ITEM.name
            }.associateBy(DesktopJournalRecord::recordId)
            val order = journal.record(NAMESPACE, PlaylistRecordId.order(playlistId))
                ?.takeUnless(DesktopJournalRecord::isDeleted)
                ?.payloadJson?.let(::decode)?.localOrder?.itemRecordIds.orEmpty()
            val ordered = order.mapNotNull(children::get).toMutableList()
            val included = ordered.mapTo(hashSetOf(), DesktopJournalRecord::recordId)
            ordered += children.values.filterNot { it.recordId in included }
                .sortedBy(DesktopJournalRecord::versionStamp)
            connection.prepareStatement("DELETE FROM playlist_items WHERE playlist_id=?")
                .use { statement ->
                    statement.setString(1, playlistId)
                    statement.executeUpdate()
                }
            connection.prepareStatement(
                """INSERT INTO playlist_items(playlist_id, position, service_id, url, title,
                    duration, item_id, stream_type, uploader, uploader_url, thumbnail_url)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""
            ).use { statement ->
                ordered.forEachIndexed { index, record ->
                    val item = decode(requireNotNull(record.payloadJson)).localItem
                        ?: throw PlaylistSyncException("Stored playlist item data is invalid")
                    statement.setString(1, playlistId)
                    statement.setInt(2, index)
                    statement.setInt(3, item.stream.serviceId)
                    statement.setString(4, item.stream.url)
                    statement.setString(5, item.stream.title)
                    statement.setLong(6, item.stream.duration)
                    statement.setString(7, record.recordId)
                    statement.setString(8, item.stream.streamType)
                    statement.setString(9, item.stream.uploader)
                    statement.setString(10, item.stream.uploaderUrl)
                    statement.setString(11, item.stream.thumbnailUrl)
                    statement.addBatch()
                }
                statement.executeBatch()
            }
        }
    }

    private fun materializeRemotePlaylist(recordId: String) {
        val record = journal.record(NAMESPACE, recordId) ?: return
        val remote = record.payloadJson?.let(::decode)?.remotePlaylist
            ?: throw PlaylistSyncException("Stored remote playlist metadata is invalid")
        synchronized(connection) {
            if (record.isDeleted) {
                connection.prepareStatement("DELETE FROM remote_playlists WHERE record_id=?")
                    .use { statement ->
                        statement.setString(1, recordId)
                        statement.executeUpdate()
                    }
                return
            }
            connection.prepareStatement(
                """INSERT INTO remote_playlists(record_id, service_id, url, name, thumbnail_url,
                    uploader, display_index, stream_count) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(record_id) DO UPDATE SET service_id=excluded.service_id,
                    url=excluded.url, name=excluded.name, thumbnail_url=excluded.thumbnail_url,
                    uploader=excluded.uploader, display_index=excluded.display_index,
                    stream_count=excluded.stream_count"""
            ).use { statement ->
                statement.setString(1, recordId)
                statement.setInt(2, remote.serviceId)
                statement.setString(3, remote.url)
                statement.setString(4, remote.name)
                statement.setString(5, remote.thumbnailUrl)
                statement.setString(6, remote.uploader)
                statement.setLong(7, remote.displayIndex)
                statement.setObject(8, remote.streamCount)
                statement.executeUpdate()
            }
        }
    }

    private fun desired(
        recordId: String,
        type: PlaylistRecordType,
        parent: String?,
        record: SyncedPlaylistRecord
    ) = DesktopDesiredRecord(recordId, type.name, parent, JSON.encodeToString(record))

    private fun toModel(change: DesktopJournalChange) = PlaylistChange(
        originPeerId = change.originPeerId,
        originRevision = change.originRevision,
        lamportVersion = change.lamportVersion,
        recordId = change.recordId,
        recordType = PlaylistRecordType.valueOf(change.recordType),
        parentRecordId = change.parentRecordId,
        type = PlaylistChangeType.valueOf(change.changeType),
        record = change.payloadJson?.let(::decode)
    )

    private fun toJournal(change: PlaylistChange): DesktopJournalChange {
        val payload = change.record?.let { JSON.encodeToString(it) }
            ?: journal.record(NAMESPACE, change.recordId)?.payloadJson
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

    private fun decode(value: String): SyncedPlaylistRecord = try {
        JSON.decodeFromString(value)
    } catch (error: Exception) {
        throw PlaylistSyncException("Stored playlist synchronization data is malformed", error)
    }

    private fun requireCanonicalUuid(value: String) {
        if (runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false).not()) {
            throw PlaylistSyncException("Desktop playlist IDs must be canonical UUIDs")
        }
    }

    companion object {
        private const val NAMESPACE = "playlists"
        private val JSON = Json {
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = false
        }
    }
}
