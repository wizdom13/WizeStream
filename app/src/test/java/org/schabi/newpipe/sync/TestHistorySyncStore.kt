/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import org.schabi.newpipe.database.learning.model.LearningNoteEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamType

internal class TestHistorySyncStore(
    override val localPeerId: String
) : HistorySyncStore {
    private val journal =
        linkedMapOf<Triple<HistorySyncCategory, String, Long>, HistoryChange>()
    private val knownRevisions =
        linkedMapOf<HistorySyncCategory, MutableMap<String, Long>>()
    private val peerKnowledge =
        linkedMapOf<Pair<HistorySyncCategory, String>, MutableMap<String, Long>>()
    private val records =
        linkedMapOf<HistorySyncCategory, MutableMap<String, HistoryChange>>()
    private val streams = linkedMapOf<Long, SyncedHistoryStream>()
    private val localRevisions = linkedMapOf<HistorySyncCategory, Long>()
    private var lamportVersion = 0L

    private val watchEvents = mutableListOf<SyncedWatchEvent>()
    private val progress = linkedMapOf<HistoryStreamIdentity, SyncedPlaybackProgress>()
    private val searchEvents = mutableListOf<SyncedSearchEvent>()

    val searchQueries: List<String>
        get() = searchEvents
            .sortedBy(SyncedSearchEvent::searchedAtEpochMillis)
            .map(SyncedSearchEvent::query)

    fun registerStream(streamId: Long, url: String) {
        streams[streamId] = SyncedHistoryStream.from(
            StreamEntity(
                serviceId = SERVICE_ID,
                url = url,
                title = url,
                streamType = StreamType.VIDEO_STREAM,
                duration = 180,
                uploader = "Uploader"
            )
        )
    }

    fun progressMillis(url: String): Long? {
        return progress[HistoryStreamIdentity(SERVICE_ID, url)]?.progressMillis
    }

    fun repeatCount(url: String): Long {
        return watchEvents
            .filter { it.stream.identity == HistoryStreamIdentity(SERVICE_ID, url) }
            .sumOf(SyncedWatchEvent::repeatCount)
    }

    override fun reconcileLocal(category: HistorySyncCategory) = Unit

    override fun recordWatchEvent(
        streamId: Long,
        watchedAtEpochMillis: Long,
        repeatCount: Long
    ) {
        val stream = requireStream(streamId)
        recordLocalChange(
            category = HistorySyncCategory.WATCH,
            recordId = HistoryRecordId.watchEvent(),
            recordType = HistoryRecordType.WATCH_EVENT,
            type = HistoryChangeType.UPSERT,
            record = SyncedHistoryRecord(
                watchEvent = SyncedWatchEvent(
                    stream,
                    watchedAtEpochMillis,
                    repeatCount
                )
            )
        )
    }

    override fun recordProgress(
        streamId: Long,
        progressMillis: Long,
        updatedAtEpochMillis: Long
    ) {
        val stream = requireStream(streamId)
        recordLocalChange(
            category = HistorySyncCategory.WATCH,
            recordId = HistoryRecordId.progress(stream.identity),
            recordType = HistoryRecordType.PLAYBACK_PROGRESS,
            type = HistoryChangeType.UPSERT,
            record = SyncedHistoryRecord(
                playbackProgress = SyncedPlaybackProgress(
                    stream,
                    progressMillis,
                    updatedAtEpochMillis
                )
            )
        )
    }

    override fun recordWatchStreamDelete(streamId: Long) {
        val stream = requireStream(streamId)
        recordLocalChange(
            category = HistorySyncCategory.WATCH,
            recordId = HistoryRecordId.watchStreamTombstone(stream.identity),
            recordType = HistoryRecordType.WATCH_STREAM_TOMBSTONE,
            type = HistoryChangeType.UPSERT,
            record = SyncedHistoryRecord(
                watchStreamTombstone = SyncedWatchStreamTombstone(stream)
            )
        )
        recordLocalChange(
            category = HistorySyncCategory.WATCH,
            recordId = HistoryRecordId.progress(stream.identity),
            recordType = HistoryRecordType.PLAYBACK_PROGRESS,
            type = HistoryChangeType.DELETE,
            record = SyncedHistoryRecord(
                playbackProgress = SyncedPlaybackProgress(
                    stream,
                    0,
                    System.currentTimeMillis()
                )
            )
        )
    }

    override fun recordWatchAllDelete() {
        recordLocalChange(
            category = HistorySyncCategory.WATCH,
            recordId = HistoryRecordId.watchAllTombstone(),
            recordType = HistoryRecordType.WATCH_ALL_TOMBSTONE,
            type = HistoryChangeType.UPSERT,
            record = null
        )
    }

    override fun recordProgressAllDelete() {
        recordLocalChange(
            category = HistorySyncCategory.WATCH,
            recordId = HistoryRecordId.playbackAllTombstone(),
            recordType = HistoryRecordType.PLAYBACK_ALL_TOMBSTONE,
            type = HistoryChangeType.UPSERT,
            record = null
        )
    }

    override fun recordSearch(
        serviceId: Int,
        query: String,
        searchedAtEpochMillis: Long
    ) {
        recordLocalChange(
            category = HistorySyncCategory.SEARCH,
            recordId = HistoryRecordId.searchEvent(),
            recordType = HistoryRecordType.SEARCH_EVENT,
            type = HistoryChangeType.UPSERT,
            record = SyncedHistoryRecord(
                searchEvent = SyncedSearchEvent(
                    serviceId,
                    query.trim(),
                    searchedAtEpochMillis
                )
            )
        )
    }

    override fun recordSearchDelete(query: String) {
        val canonicalQuery = query.trim()
        recordLocalChange(
            category = HistorySyncCategory.SEARCH,
            recordId = HistoryRecordId.searchQueryTombstone(canonicalQuery),
            recordType = HistoryRecordType.SEARCH_QUERY_TOMBSTONE,
            type = HistoryChangeType.UPSERT,
            record = SyncedHistoryRecord(
                searchQueryTombstone = SyncedSearchQueryTombstone(canonicalQuery)
            )
        )
    }

    override fun recordSearchAllDelete() {
        recordLocalChange(
            category = HistorySyncCategory.SEARCH,
            recordId = HistoryRecordId.searchAllTombstone(),
            recordType = HistoryRecordType.SEARCH_ALL_TOMBSTONE,
            type = HistoryChangeType.UPSERT,
            record = null
        )
    }

    override fun recordLearningNoteUpsert(noteId: String) = Unit

    override fun recordLearningNoteDelete(note: LearningNoteEntity) = Unit

    override fun getKnownRevisions(
        category: HistorySyncCategory
    ): Map<String, Long> {
        return knownRevisions[category].orEmpty().toMap()
    }

    override fun getPendingChanges(
        category: HistorySyncCategory,
        peerId: String,
        limit: Int
    ): HistoryChangeBatch {
        val acknowledged = peerKnowledge[category to peerId].orEmpty()
        val pending = journal.values
            .filter { change ->
                change.category == category &&
                    change.originRevision >
                    (acknowledged[change.originPeerId] ?: 0)
            }
            .sortedBy(HistoryChange::versionStamp)
        return HistoryChangeBatch(
            changes = pending.take(limit),
            hasMore = pending.size > limit
        )
    }

    override fun acknowledgePeer(
        category: HistorySyncCategory,
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
        category: HistorySyncCategory,
        changes: List<HistoryChange>
    ): HistoryApplyResult {
        HistorySyncValidation.validateChanges(category, changes)
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
        materialize()
        return HistoryApplyResult(accepted, affected)
    }

    override fun clearPeerKnowledge() {
        peerKnowledge.clear()
    }

    private fun recordLocalChange(
        category: HistorySyncCategory,
        recordId: String,
        recordType: HistoryRecordType,
        type: HistoryChangeType,
        record: SyncedHistoryRecord?
    ) {
        val categoryRecords = records.getOrPut(category) { linkedMapOf() }
        val existing = categoryRecords[recordId]
        val localRevision = (localRevisions[category] ?: 0) + 1
        localRevisions[category] = localRevision
        lamportVersion = maxOf(
            lamportVersion,
            existing?.lamportVersion ?: 0
        ) + 1
        val change = HistoryChange(
            category = category,
            originPeerId = localPeerId,
            originRevision = localRevision,
            lamportVersion = lamportVersion,
            recordId = recordId,
            recordType = recordType,
            type = type,
            record = record
        )
        HistorySyncValidation.validateChanges(category, listOf(change))
        journal[Triple(category, localPeerId, localRevision)] = change
        knownRevisions.getOrPut(category) { linkedMapOf() }[localPeerId] =
            localRevision
        categoryRecords[recordId] = change
        materialize()
    }

    private fun materialize() {
        materializeWatch()
        materializeSearch()
    }

    private fun materializeWatch() {
        val watchRecords = records[HistorySyncCategory.WATCH].orEmpty().values
        val globalWatchCutoff = watchRecords.firstOrNull {
            it.recordId == HistoryRecordId.watchAllTombstone()
        }?.versionStamp
        val streamCutoffs = watchRecords
            .filter { it.recordType == HistoryRecordType.WATCH_STREAM_TOMBSTONE }
            .associate { change ->
                requireNotNull(change.record?.watchStreamTombstone)
                    .stream.identity to change.versionStamp
            }
        watchEvents.clear()
        watchEvents += watchRecords
            .filter { it.recordType == HistoryRecordType.WATCH_EVENT }
            .filter { it.type != HistoryChangeType.DELETE }
            .filter { globalWatchCutoff == null || it.versionStamp > globalWatchCutoff }
            .filter { change ->
                val event = requireNotNull(change.record?.watchEvent)
                val cutoff = streamCutoffs[event.stream.identity]
                cutoff == null || change.versionStamp > cutoff
            }
            .map { requireNotNull(it.record?.watchEvent) }

        val progressCutoff = watchRecords.firstOrNull {
            it.recordId == HistoryRecordId.playbackAllTombstone()
        }?.versionStamp
        progress.clear()
        watchRecords
            .filter { it.recordType == HistoryRecordType.PLAYBACK_PROGRESS }
            .filter { it.type != HistoryChangeType.DELETE }
            .filter { progressCutoff == null || it.versionStamp > progressCutoff }
            .forEach { change ->
                val item = requireNotNull(change.record?.playbackProgress)
                progress[item.stream.identity] = item
            }
    }

    private fun materializeSearch() {
        val searchRecords = records[HistorySyncCategory.SEARCH].orEmpty().values
        val globalCutoff = searchRecords.firstOrNull {
            it.recordId == HistoryRecordId.searchAllTombstone()
        }?.versionStamp
        val queryCutoffs = searchRecords
            .filter { it.recordType == HistoryRecordType.SEARCH_QUERY_TOMBSTONE }
            .associate { change ->
                requireNotNull(change.record?.searchQueryTombstone)
                    .query to change.versionStamp
            }
        searchEvents.clear()
        searchEvents += searchRecords
            .filter { it.recordType == HistoryRecordType.SEARCH_EVENT }
            .filter { it.type != HistoryChangeType.DELETE }
            .filter { globalCutoff == null || it.versionStamp > globalCutoff }
            .filter { change ->
                val event = requireNotNull(change.record?.searchEvent)
                val cutoff = queryCutoffs[event.query]
                cutoff == null || change.versionStamp > cutoff
            }
            .map { requireNotNull(it.record?.searchEvent) }
    }

    private fun advanceKnownRevision(
        category: HistorySyncCategory,
        originPeerId: String
    ) {
        val categoryRevisions = knownRevisions.getOrPut(category) {
            linkedMapOf()
        }
        var revision = categoryRevisions[originPeerId] ?: 0
        while (
            journal.containsKey(
                Triple(category, originPeerId, revision + 1)
            )
        ) {
            revision += 1
        }
        categoryRevisions[originPeerId] = revision
    }

    private fun requireStream(streamId: Long): SyncedHistoryStream {
        return requireNotNull(streams[streamId]) {
            "Test stream $streamId is not registered"
        }
    }

    companion object {
        private const val SERVICE_ID = 0
    }
}
