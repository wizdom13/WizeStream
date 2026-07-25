/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

internal class TestPlaylistSyncStore(
    override val localPeerId: String
) : PlaylistSyncStore {
    private val journal = linkedMapOf<Pair<String, Long>, PlaylistChange>()
    private val knownRevisions = linkedMapOf<String, Long>()
    private val peerKnowledge = linkedMapOf<String, MutableMap<String, Long>>()
    private val records = linkedMapOf<String, PlaylistChange>()
    private var localRevision = 0L
    private var lamportVersion = 0L

    override fun reconcileLocalPlaylists() = Unit

    override fun getKnownRevisions(): Map<String, Long> = knownRevisions.toMap()

    override fun getPendingChanges(
        peerId: String,
        limit: Int
    ): PlaylistChangeBatch {
        val acknowledged = peerKnowledge[peerId].orEmpty()
        val pending = journal.values
            .filter { change ->
                change.originRevision > (acknowledged[change.originPeerId] ?: 0)
            }
            .sortedBy(PlaylistChange::versionStamp)
        return PlaylistChangeBatch(
            changes = pending.take(limit),
            hasMore = pending.size > limit
        )
    }

    override fun acknowledgePeer(
        peerId: String,
        knownRevisions: Map<String, Long>
    ) {
        val knowledge = peerKnowledge.getOrPut(peerId) { linkedMapOf() }
        knownRevisions.forEach { (origin, revision) ->
            val safeRevision = minOf(revision, this.knownRevisions[origin] ?: 0)
            knowledge[origin] = maxOf(knowledge[origin] ?: 0, safeRevision)
        }
    }

    override fun applyChanges(changes: List<PlaylistChange>): PlaylistApplyResult {
        PlaylistSyncValidation.validateChanges(changes)
        var accepted = 0
        val affected = linkedSetOf<String>()
        changes.forEach { change ->
            val changeId = change.originPeerId to change.originRevision
            if (journal.containsKey(changeId)) {
                return@forEach
            }
            journal[changeId] = change
            accepted += 1
            lamportVersion = maxOf(lamportVersion, change.lamportVersion)
            advanceKnownRevision(change.originPeerId)
            val existing = records[change.recordId]
            if (existing != null && change.versionStamp <= existing.versionStamp) {
                return@forEach
            }
            records[change.recordId] = change.copy(
                record = change.record ?: existing?.record
            )
            affected += change.parentRecordId ?: change.recordId
        }
        return PlaylistApplyResult(accepted, affected.size)
    }

    override fun clearPeerKnowledge() {
        peerKnowledge.clear()
    }

    fun createLocalPlaylist(name: String, urls: List<String>): String {
        val playlistId = PlaylistRecordId.local()
        upsert(
            playlistId,
            PlaylistRecordType.LOCAL_PLAYLIST,
            record = SyncedPlaylistRecord(
                localPlaylist = SyncedLocalPlaylist(
                    name = name,
                    isThumbnailPermanent = false,
                    displayIndex = 0
                )
            )
        )
        val itemIds = urls.map { url ->
            PlaylistRecordId.item().also { itemId ->
                upsert(
                    itemId,
                    PlaylistRecordType.LOCAL_PLAYLIST_ITEM,
                    parentRecordId = playlistId,
                    record = SyncedPlaylistRecord(
                        localItem = SyncedPlaylistItem(
                            playlistRecordId = playlistId,
                            stream = testStream(url)
                        )
                    )
                )
            }
        }
        updateOrder(playlistId, itemIds)
        return playlistId
    }

    fun renameLocalPlaylist(playlistId: String, name: String) {
        val current = records.getValue(playlistId).record?.localPlaylist
            ?: error("Playlist does not exist")
        upsert(
            playlistId,
            PlaylistRecordType.LOCAL_PLAYLIST,
            record = SyncedPlaylistRecord(
                localPlaylist = current.copy(name = name)
            )
        )
    }

    fun addLocalItem(playlistId: String, url: String): String {
        val currentOrder = orderedItemIds(playlistId)
        val itemId = PlaylistRecordId.item()
        upsert(
            itemId,
            PlaylistRecordType.LOCAL_PLAYLIST_ITEM,
            parentRecordId = playlistId,
            record = SyncedPlaylistRecord(
                localItem = SyncedPlaylistItem(
                    playlistRecordId = playlistId,
                    stream = testStream(url)
                )
            )
        )
        updateOrder(playlistId, currentOrder + itemId)
        return itemId
    }

    fun reorderLocalPlaylist(playlistId: String, itemIds: List<String>) {
        updateOrder(playlistId, itemIds)
    }

    fun deleteLocalPlaylist(playlistId: String) {
        delete(records.getValue(playlistId))
        records.values
            .filter { it.parentRecordId == playlistId && it.type != PlaylistChangeType.DELETE }
            .toList()
            .forEach(::delete)
    }

    fun bookmarkRemotePlaylist(serviceId: Int, url: String, name: String) {
        val playlist = SyncedRemotePlaylist(
            serviceId = serviceId,
            url = url,
            name = name,
            thumbnailUrl = null,
            uploader = null,
            displayIndex = 0,
            streamCount = 1
        )
        upsert(
            PlaylistRecordId.remote(serviceId, url),
            PlaylistRecordType.REMOTE_PLAYLIST,
            record = SyncedPlaylistRecord(remotePlaylist = playlist)
        )
    }

    fun deleteRemotePlaylist(serviceId: Int, url: String) {
        delete(records.getValue(PlaylistRecordId.remote(serviceId, url)))
    }

    fun playlistName(playlistId: String): String? {
        return records[playlistId]
            ?.takeUnless { it.type == PlaylistChangeType.DELETE }
            ?.record
            ?.localPlaylist
            ?.name
    }

    fun playlistUrls(playlistId: String): List<String> {
        return orderedItemIds(playlistId).mapNotNull { itemId ->
            records[itemId]
                ?.takeUnless { it.type == PlaylistChangeType.DELETE }
                ?.record
                ?.localItem
                ?.stream
                ?.url
        }
    }

    fun playlistItemIds(playlistId: String): List<String> = orderedItemIds(playlistId)

    fun hasLocalPlaylist(playlistId: String): Boolean {
        return records[playlistId]?.type == PlaylistChangeType.UPSERT
    }

    val remotePlaylistUrls: Set<String>
        get() = records.values.mapNotNull { change ->
            change.record
                ?.remotePlaylist
                ?.takeIf { change.type == PlaylistChangeType.UPSERT }
                ?.url
        }.toSet()

    private fun updateOrder(playlistId: String, itemIds: List<String>) {
        upsert(
            PlaylistRecordId.order(playlistId),
            PlaylistRecordType.LOCAL_PLAYLIST_ORDER,
            parentRecordId = playlistId,
            record = SyncedPlaylistRecord(
                localOrder = SyncedPlaylistOrder(playlistId, itemIds)
            )
        )
    }

    private fun orderedItemIds(playlistId: String): List<String> {
        val liveItems = records.values
            .filter {
                it.recordType == PlaylistRecordType.LOCAL_PLAYLIST_ITEM &&
                    it.parentRecordId == playlistId &&
                    it.type == PlaylistChangeType.UPSERT
            }
            .associateBy(PlaylistChange::recordId)
        val requestedOrder = records[PlaylistRecordId.order(playlistId)]
            ?.takeUnless { it.type == PlaylistChangeType.DELETE }
            ?.record
            ?.localOrder
            ?.itemRecordIds
            .orEmpty()
        val result = requestedOrder.mapNotNull(liveItems::get)
            .map(PlaylistChange::recordId)
            .toMutableList()
        val included = result.toHashSet()
        result += liveItems.values
            .filterNot { it.recordId in included }
            .sortedBy(PlaylistChange::versionStamp)
            .map(PlaylistChange::recordId)
        return result
    }

    private fun upsert(
        recordId: String,
        recordType: PlaylistRecordType,
        parentRecordId: String? = null,
        record: SyncedPlaylistRecord
    ) {
        recordLocalChange(
            recordId,
            recordType,
            parentRecordId,
            PlaylistChangeType.UPSERT,
            record
        )
    }

    private fun delete(current: PlaylistChange) {
        recordLocalChange(
            current.recordId,
            current.recordType,
            current.parentRecordId,
            PlaylistChangeType.DELETE,
            current.record
        )
    }

    private fun recordLocalChange(
        recordId: String,
        recordType: PlaylistRecordType,
        parentRecordId: String?,
        type: PlaylistChangeType,
        record: SyncedPlaylistRecord?
    ) {
        val existing = records[recordId]
        localRevision += 1
        lamportVersion = maxOf(
            lamportVersion,
            existing?.lamportVersion ?: 0
        ) + 1
        val change = PlaylistChange(
            originPeerId = localPeerId,
            originRevision = localRevision,
            lamportVersion = lamportVersion,
            recordId = recordId,
            recordType = recordType,
            parentRecordId = parentRecordId,
            type = type,
            record = record
        )
        PlaylistSyncValidation.validateChanges(listOf(change))
        journal[localPeerId to localRevision] = change
        knownRevisions[localPeerId] = localRevision
        records[recordId] = change
    }

    private fun advanceKnownRevision(originPeerId: String) {
        var revision = knownRevisions[originPeerId] ?: 0
        while (journal.containsKey(originPeerId to revision + 1)) {
            revision += 1
        }
        knownRevisions[originPeerId] = revision
    }

    private fun testStream(url: String) = SyncedStream(
        serviceId = 0,
        url = url,
        title = url,
        streamType = "VIDEO_STREAM",
        duration = 60,
        uploader = "Uploader"
    )
}
