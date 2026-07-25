/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import android.content.Context
import java.util.ArrayDeque
import java.util.concurrent.Callable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.sync.PlaylistSyncChangeEntity
import org.schabi.newpipe.database.sync.PlaylistSyncLocalMapEntity
import org.schabi.newpipe.database.sync.PlaylistSyncOriginStateEntity
import org.schabi.newpipe.database.sync.PlaylistSyncPeerStateEntity
import org.schabi.newpipe.database.sync.PlaylistSyncRecordEntity

internal interface PlaylistSyncStore {
    val localPeerId: String

    fun reconcileLocalPlaylists()

    fun getKnownRevisions(): Map<String, Long>

    fun getPendingChanges(peerId: String, limit: Int): PlaylistChangeBatch

    fun acknowledgePeer(peerId: String, knownRevisions: Map<String, Long>)

    fun applyChanges(changes: List<PlaylistChange>): PlaylistApplyResult

    fun clearPeerKnowledge()
}

internal class RoomPlaylistSyncStore internal constructor(
    private val database: AppDatabase,
    override val localPeerId: String
) : PlaylistSyncStore {
    private val syncDao = database.playlistSyncDAO()
    private val playlistDao = database.playlistDAO()
    private val playlistStreamDao = database.playlistStreamDAO()
    private val remotePlaylistDao = database.playlistRemoteDAO()
    private val streamDao = database.streamDAO()

    override fun reconcileLocalPlaylists() {
        database.runInTransaction {
            val localPlaylists = playlistDao.getAllDirect()
            val localPlaylistIds = localPlaylists.mapTo(hashSetOf(), PlaylistEntity::uid)
            localPlaylists.forEach(::reconcileLocalPlaylist)

            syncDao.getAllLocalMappings()
                .filterNot { it.playlistUid in localPlaylistIds }
                .forEach { mapping ->
                    deleteLocalPlaylistRecords(mapping.playlistRecordId)
                }

            val remotePlaylists = remotePlaylistDao.getAllDirect()
                .filter { playlist ->
                    playlist.serviceId >= 0 &&
                        !playlist.url.isNullOrBlank() &&
                        requireNotNull(playlist.url).length <= MAX_PLAYLIST_URL_LENGTH
                }
            val liveRemoteIds = remotePlaylists.mapTo(hashSetOf()) { playlist ->
                PlaylistRecordId.remote(
                    playlist.serviceId,
                    requireNotNull(playlist.url)
                )
            }
            remotePlaylists.forEach { playlist ->
                val synced = SyncedRemotePlaylist.from(playlist)
                saveLocalUpsert(
                    recordId = PlaylistRecordId.remote(synced.serviceId, synced.url),
                    recordType = PlaylistRecordType.REMOTE_PLAYLIST,
                    parentRecordId = null,
                    record = SyncedPlaylistRecord(remotePlaylist = synced)
                )
            }

            syncDao.getRecordsByType(PlaylistRecordType.REMOTE_PLAYLIST.name)
                .filterNot(PlaylistSyncRecordEntity::isDeleted)
                .filterNot { it.recordId in liveRemoteIds }
                .forEach { record ->
                    saveLocalDelete(record, decodeRecord(record))
                }
        }
    }

    override fun getKnownRevisions(): Map<String, Long> {
        return syncDao.getAllOriginStates()
            .filter { it.contiguousRevision > 0 }
            .associate { it.originPeerId to it.contiguousRevision }
    }

    override fun getPendingChanges(
        peerId: String,
        limit: Int
    ): PlaylistChangeBatch {
        require(limit in 1..MAX_PLAYLIST_CHANGES_PER_BATCH)
        val peerKnowledge = syncDao.getPeerStates(peerId)
            .associate { it.originPeerId to it.acknowledgedRevision }
        val origins = syncDao.getChangeOrigins().sorted()
        val candidates = origins.flatMap { origin ->
            syncDao.getChangesAfter(
                origin,
                peerKnowledge[origin] ?: 0,
                limit
            )
        }.map(::toModel)
            .sortedBy(PlaylistChange::versionStamp)
            .take(limit)
        val pendingCount = origins.sumOf { origin ->
            syncDao.countChangesAfter(origin, peerKnowledge[origin] ?: 0)
        }
        return PlaylistChangeBatch(
            changes = candidates,
            hasMore = pendingCount > candidates.size.toLong()
        )
    }

    override fun acknowledgePeer(
        peerId: String,
        knownRevisions: Map<String, Long>
    ) {
        PlaylistSyncValidation.validateKnownRevisions(knownRevisions)
        database.runInTransaction {
            val localKnowledge = getKnownRevisions()
            val existing = syncDao.getPeerStates(peerId)
                .associate { it.originPeerId to it.acknowledgedRevision }
            knownRevisions.forEach { (origin, claimedRevision) ->
                val safeRevision = minOf(claimedRevision, localKnowledge[origin] ?: 0)
                val acknowledgedRevision = maxOf(existing[origin] ?: 0, safeRevision)
                if (acknowledgedRevision > 0) {
                    syncDao.upsertPeerState(
                        PlaylistSyncPeerStateEntity(
                            peerId = peerId,
                            originPeerId = origin,
                            acknowledgedRevision = acknowledgedRevision
                        )
                    )
                }
            }
        }
    }

    override fun applyChanges(changes: List<PlaylistChange>): PlaylistApplyResult {
        PlaylistSyncValidation.validateChanges(changes)
        return database.runInTransaction(
            Callable {
                val maximumAcceptedLamport = minOf(
                    syncDao.getMaximumLamportVersion() + MAX_REMOTE_LAMPORT_ADVANCE,
                    MAX_SYNC_REVISION
                )
                if (changes.any { it.lamportVersion > maximumAcceptedLamport }) {
                    throw PlaylistSyncException(
                        "A playlist change advances the logical clock too far"
                    )
                }

                var accepted = 0
                val affectedLocalPlaylists = linkedSetOf<String>()
                val affectedRemotePlaylists = linkedSetOf<String>()
                changes.forEach { change ->
                    if (syncDao.insertChange(change.toEntity()) == -1L) {
                        return@forEach
                    }
                    accepted += 1
                    advanceContiguousRevision(change.originPeerId)

                    val currentRecord = syncDao.getRecord(change.recordId)
                    if (
                        currentRecord != null &&
                        change.versionStamp <= currentRecord.versionStamp
                    ) {
                        return@forEach
                    }
                    syncDao.upsertRecord(change.toRecordEntity(currentRecord))
                    when (change.recordType) {
                        PlaylistRecordType.LOCAL_PLAYLIST ->
                            affectedLocalPlaylists += change.recordId

                        PlaylistRecordType.LOCAL_PLAYLIST_ITEM,
                        PlaylistRecordType.LOCAL_PLAYLIST_ORDER ->
                            affectedLocalPlaylists += requireNotNull(change.parentRecordId)

                        PlaylistRecordType.REMOTE_PLAYLIST ->
                            affectedRemotePlaylists += change.recordId
                    }
                }

                affectedLocalPlaylists.forEach(::materializeLocalPlaylist)
                affectedRemotePlaylists.forEach(::materializeRemotePlaylist)
                PlaylistApplyResult(
                    acceptedChanges = accepted,
                    changedPlaylists =
                        affectedLocalPlaylists.size + affectedRemotePlaylists.size
                )
            }
        )
    }

    override fun clearPeerKnowledge() {
        syncDao.deleteAllPeerStates()
    }

    private fun reconcileLocalPlaylist(playlist: PlaylistEntity) {
        val mapping = syncDao.getLocalMapping(playlist.uid)
            ?: PlaylistSyncLocalMapEntity(
                playlistRecordId = PlaylistRecordId.local(),
                playlistUid = playlist.uid
            ).also(syncDao::upsertLocalMapping)
        val playlistRecordId = mapping.playlistRecordId
        val thumbnail = if (
            playlist.isThumbnailPermanent &&
            playlist.thumbnailStreamId != PlaylistEntity.DEFAULT_THUMBNAIL_ID
        ) {
            streamDao.getStreamDirect(playlist.thumbnailStreamId)
        } else {
            null
        }
        val syncedPlaylist = SyncedLocalPlaylist(
            name = playlist.name?.take(MAX_PLAYLIST_NAME_LENGTH),
            isThumbnailPermanent = playlist.isThumbnailPermanent && thumbnail != null,
            thumbnailServiceId = thumbnail?.serviceId,
            thumbnailUrl = thumbnail?.url?.trim(),
            displayIndex = playlist.displayIndex
        )
        saveLocalUpsert(
            recordId = playlistRecordId,
            recordType = PlaylistRecordType.LOCAL_PLAYLIST,
            parentRecordId = null,
            record = SyncedPlaylistRecord(localPlaylist = syncedPlaylist)
        )

        val streams = playlistStreamDao.getOrderedStreamsDirect(playlist.uid)
        if (streams.size > MAX_PLAYLIST_ITEMS) {
            throw PlaylistSyncException(
                "Playlist “${playlist.name.orEmpty()}” has too many items to synchronize"
            )
        }
        val liveItemRecords = syncDao.getChildRecords(playlistRecordId)
            .filterNot(PlaylistSyncRecordEntity::isDeleted)
            .filter {
                it.recordType == PlaylistRecordType.LOCAL_PLAYLIST_ITEM.name
            }
        val orderedExisting = orderItemRecords(playlistRecordId, liveItemRecords)
        val reusableItems = linkedMapOf<StreamIdentity, ArrayDeque<PlaylistSyncRecordEntity>>()
        orderedExisting.forEach { record ->
            val item = decodeRecord(record)?.localItem ?: return@forEach
            reusableItems.getOrPut(item.stream.identity) { ArrayDeque() }.addLast(record)
        }

        val usedRecordIds = hashSetOf<String>()
        val desiredOrder = ArrayList<String>(streams.size)
        streams.forEach { stream ->
            val existing = reusableItems[stream.identity]?.pollFirst()
            val itemRecordId = existing?.recordId ?: PlaylistRecordId.item()
            usedRecordIds += itemRecordId
            desiredOrder += itemRecordId
            saveLocalUpsert(
                recordId = itemRecordId,
                recordType = PlaylistRecordType.LOCAL_PLAYLIST_ITEM,
                parentRecordId = playlistRecordId,
                record = SyncedPlaylistRecord(
                    localItem = SyncedPlaylistItem(
                        playlistRecordId = playlistRecordId,
                        stream = SyncedStream.from(stream)
                    )
                )
            )
        }
        liveItemRecords.filterNot { it.recordId in usedRecordIds }
            .forEach { record ->
                saveLocalDelete(record, decodeRecord(record))
            }

        saveLocalUpsert(
            recordId = PlaylistRecordId.order(playlistRecordId),
            recordType = PlaylistRecordType.LOCAL_PLAYLIST_ORDER,
            parentRecordId = playlistRecordId,
            record = SyncedPlaylistRecord(
                localOrder = SyncedPlaylistOrder(
                    playlistRecordId = playlistRecordId,
                    itemRecordIds = desiredOrder
                )
            )
        )
    }

    private fun deleteLocalPlaylistRecords(playlistRecordId: String) {
        syncDao.getRecord(playlistRecordId)
            ?.takeUnless(PlaylistSyncRecordEntity::isDeleted)
            ?.let { record ->
                saveLocalDelete(record, decodeRecord(record))
            }
        syncDao.getChildRecords(playlistRecordId)
            .filterNot(PlaylistSyncRecordEntity::isDeleted)
            .forEach { record ->
                saveLocalDelete(record, decodeRecord(record))
            }
    }

    private fun saveLocalUpsert(
        recordId: String,
        recordType: PlaylistRecordType,
        parentRecordId: String?,
        record: SyncedPlaylistRecord
    ) {
        val current = syncDao.getRecord(recordId)
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
            recordId = recordId,
            recordType = recordType,
            parentRecordId = parentRecordId,
            type = PlaylistChangeType.UPSERT,
            record = record,
            currentRecord = current
        )
    }

    private fun saveLocalDelete(
        currentRecord: PlaylistSyncRecordEntity,
        record: SyncedPlaylistRecord?
    ) {
        if (currentRecord.isDeleted) {
            return
        }
        saveLocalChange(
            recordId = currentRecord.recordId,
            recordType = currentRecord.parsedRecordType,
            parentRecordId = currentRecord.parentRecordId,
            type = PlaylistChangeType.DELETE,
            record = record,
            currentRecord = currentRecord
        )
    }

    private fun saveLocalChange(
        recordId: String,
        recordType: PlaylistRecordType,
        parentRecordId: String?,
        type: PlaylistChangeType,
        record: SyncedPlaylistRecord?,
        currentRecord: PlaylistSyncRecordEntity?
    ) {
        val originState = syncDao.getOriginState(localPeerId)
        val originRevision = incrementVersion(
            originState?.contiguousRevision ?: 0
        )
        val lamportVersion = incrementVersion(
            maxOf(
                syncDao.getMaximumLamportVersion(),
                currentRecord?.lamportVersion ?: 0
            )
        )
        val change = PlaylistChange(
            originPeerId = localPeerId,
            originRevision = originRevision,
            lamportVersion = lamportVersion,
            recordId = recordId,
            recordType = recordType,
            parentRecordId = parentRecordId,
            type = type,
            record = record
        )
        PlaylistSyncValidation.validateChanges(listOf(change))
        check(syncDao.insertChange(change.toEntity()) != -1L) {
            "The local playlist revision already exists"
        }
        syncDao.upsertOriginState(
            PlaylistSyncOriginStateEntity(localPeerId, originRevision)
        )
        syncDao.upsertRecord(change.toRecordEntity(currentRecord))
    }

    private fun materializeLocalPlaylist(playlistRecordId: String) {
        val playlistRecord = syncDao.getRecord(playlistRecordId) ?: return
        val mapping = syncDao.getLocalMapping(playlistRecordId)
        if (playlistRecord.isDeleted) {
            mapping?.let { playlistDao.deletePlaylist(it.playlistUid) }
            return
        }
        val metadata = decodeRecord(playlistRecord)?.localPlaylist
            ?: throw PlaylistSyncException("Stored local playlist metadata is invalid")
        var playlistUid = mapping?.playlistUid
        var playlist = playlistUid?.let(playlistDao::getPlaylistDirect)
        if (playlist == null) {
            playlistUid = playlistDao.insert(
                PlaylistEntity(
                    name = metadata.name,
                    isThumbnailPermanent = false,
                    thumbnailStreamId = PlaylistEntity.DEFAULT_THUMBNAIL_ID,
                    displayIndex = metadata.displayIndex
                )
            )
            syncDao.upsertLocalMapping(
                PlaylistSyncLocalMapEntity(playlistRecordId, playlistUid)
            )
            playlist = requireNotNull(playlistDao.getPlaylistDirect(playlistUid))
        }

        val liveItemRecords = syncDao.getChildRecords(playlistRecordId)
            .filterNot(PlaylistSyncRecordEntity::isDeleted)
            .filter { it.recordType == PlaylistRecordType.LOCAL_PLAYLIST_ITEM.name }
        val orderedItemRecords = orderItemRecords(playlistRecordId, liveItemRecords)
        val syncedItems = orderedItemRecords.map { record ->
            decodeRecord(record)?.localItem
                ?: throw PlaylistSyncException("Stored playlist item data is invalid")
        }
        val streamIds = streamDao.upsertAll(
            syncedItems.map { it.stream.toEntity() }
        )
        val thumbnailId = syncedItems.indices.firstOrNull { index ->
            metadata.thumbnailServiceId == syncedItems[index].stream.serviceId &&
                metadata.thumbnailUrl == syncedItems[index].stream.url
        }?.let(streamIds::get)

        playlist.name = metadata.name
        playlist.displayIndex = metadata.displayIndex
        playlist.isThumbnailPermanent = metadata.isThumbnailPermanent && thumbnailId != null
        playlist.thumbnailStreamId = thumbnailId
            ?: streamIds.firstOrNull()
            ?: PlaylistEntity.DEFAULT_THUMBNAIL_ID
        playlistDao.update(playlist)

        val materializedPlaylistUid = requireNotNull(playlistUid)
        playlistStreamDao.deleteBatch(materializedPlaylistUid)
        if (streamIds.isNotEmpty()) {
            playlistStreamDao.insertAll(
                streamIds.mapIndexed { index, streamId ->
                    org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity(
                        playlistUid = materializedPlaylistUid,
                        streamUid = streamId,
                        index = index
                    )
                }
            )
        }
    }

    private fun materializeRemotePlaylist(recordId: String) {
        val record = syncDao.getRecord(recordId) ?: return
        val remote = decodeRecord(record)?.remotePlaylist
            ?: throw PlaylistSyncException("Stored remote playlist metadata is invalid")
        val existingId = remotePlaylistDao.getPlaylistIdInternal(
            remote.serviceId.toLong(),
            remote.url
        )
        if (record.isDeleted) {
            existingId?.let(remotePlaylistDao::deletePlaylist)
        } else {
            remotePlaylistDao.upsert(remote.toEntity())
        }
    }

    private fun orderItemRecords(
        playlistRecordId: String,
        liveItemRecords: List<PlaylistSyncRecordEntity>
    ): List<PlaylistSyncRecordEntity> {
        val byId = liveItemRecords.associateBy(PlaylistSyncRecordEntity::recordId)
        val order = syncDao.getRecord(PlaylistRecordId.order(playlistRecordId))
            ?.takeUnless(PlaylistSyncRecordEntity::isDeleted)
            ?.let(::decodeRecord)
            ?.localOrder
            ?.itemRecordIds
            .orEmpty()
        val ordered = order.mapNotNull(byId::get).toMutableList()
        val included = ordered.mapTo(hashSetOf(), PlaylistSyncRecordEntity::recordId)
        ordered += liveItemRecords
            .filterNot { it.recordId in included }
            .sortedWith(
                compareBy<PlaylistSyncRecordEntity>(
                    PlaylistSyncRecordEntity::lamportVersion,
                    PlaylistSyncRecordEntity::originPeerId,
                    PlaylistSyncRecordEntity::originRevision,
                    PlaylistSyncRecordEntity::recordId
                )
            )
        return ordered
    }

    private fun incrementVersion(value: Long): Long {
        if (value >= MAX_SYNC_REVISION) {
            throw PlaylistSyncException("The playlist journal version is exhausted")
        }
        return value + 1
    }

    private fun advanceContiguousRevision(originPeerId: String) {
        var contiguous = syncDao.getOriginState(originPeerId)?.contiguousRevision ?: 0
        while (
            contiguous < MAX_SYNC_REVISION &&
            syncDao.hasChange(originPeerId, contiguous + 1)
        ) {
            contiguous += 1
        }
        syncDao.upsertOriginState(
            PlaylistSyncOriginStateEntity(originPeerId, contiguous)
        )
    }

    private fun toModel(entity: PlaylistSyncChangeEntity): PlaylistChange {
        return PlaylistChange(
            originPeerId = entity.originPeerId,
            originRevision = entity.originRevision,
            lamportVersion = entity.lamportVersion,
            recordId = entity.recordId,
            recordType = parseRecordType(entity.recordType),
            parentRecordId = entity.parentRecordId,
            type = try {
                PlaylistChangeType.valueOf(entity.changeType)
            } catch (error: IllegalArgumentException) {
                throw PlaylistSyncException(
                    "The local playlist journal contains an invalid change",
                    error
                )
            },
            record = entity.recordJson?.let(PlaylistRecordCodec::decode)
        )
    }

    private fun PlaylistChange.toEntity() = PlaylistSyncChangeEntity(
        originPeerId = originPeerId,
        originRevision = originRevision,
        lamportVersion = lamportVersion,
        recordId = recordId,
        recordType = recordType.name,
        parentRecordId = parentRecordId,
        changeType = type.name,
        recordJson = record?.let(PlaylistRecordCodec::encode)
    )

    private fun PlaylistChange.toRecordEntity(
        currentRecord: PlaylistSyncRecordEntity?
    ) = PlaylistSyncRecordEntity(
        recordId = recordId,
        recordType = recordType.name,
        parentRecordId = parentRecordId,
        lamportVersion = lamportVersion,
        originPeerId = originPeerId,
        originRevision = originRevision,
        isDeleted = type == PlaylistChangeType.DELETE,
        recordJson = record?.let(PlaylistRecordCodec::encode)
            ?: currentRecord?.recordJson
    )

    private fun decodeRecord(entity: PlaylistSyncRecordEntity): SyncedPlaylistRecord? {
        return entity.recordJson?.let(PlaylistRecordCodec::decode)
    }

    private fun parseRecordType(value: String): PlaylistRecordType {
        return try {
            PlaylistRecordType.valueOf(value)
        } catch (error: IllegalArgumentException) {
            throw PlaylistSyncException(
                "The local playlist journal contains an invalid record type",
                error
            )
        }
    }

    private val PlaylistSyncRecordEntity.parsedRecordType: PlaylistRecordType
        get() = parseRecordType(recordType)

    private val PlaylistSyncRecordEntity.versionStamp: PlaylistVersionStamp
        get() = PlaylistVersionStamp(
            lamportVersion,
            originPeerId,
            originRevision
        )

    private val StreamEntity.identity: StreamIdentity
        get() = StreamIdentity(serviceId, url.trim())

    private val SyncedStream.identity: StreamIdentity
        get() = StreamIdentity(serviceId, url)

    private data class StreamIdentity(
        val serviceId: Int,
        val url: String
    )

    companion object {
        private const val MAX_REMOTE_LAMPORT_ADVANCE = 1_000_000L

        @Volatile
        private var instance: RoomPlaylistSyncStore? = null

        fun get(context: Context): RoomPlaylistSyncStore {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val applicationContext = context.applicationContext
                    val stateRepository = AndroidSyncStateRepository(applicationContext)
                    RoomPlaylistSyncStore(
                        database = NewPipeDatabase.getInstance(applicationContext),
                        localPeerId = stateRepository.loadOrCreateIdentity().peerId.toBase58()
                    )
                }.also { instance = it }
            }
        }
    }
}

private object PlaylistRecordCodec {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = false
    }

    fun encode(record: SyncedPlaylistRecord): String = json.encodeToString(record)

    fun decode(value: String): SyncedPlaylistRecord {
        return try {
            json.decodeFromString(value)
        } catch (error: Exception) {
            throw PlaylistSyncException("Stored playlist synchronization data is malformed", error)
        }
    }
}
