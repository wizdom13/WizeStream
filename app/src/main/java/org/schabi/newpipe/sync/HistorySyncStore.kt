/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import android.content.Context
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.Callable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.history.model.SearchHistoryEntry
import org.schabi.newpipe.database.history.model.StreamHistoryEntity
import org.schabi.newpipe.database.learning.model.LearningNoteEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.stream.model.StreamStateEntity
import org.schabi.newpipe.database.sync.HistorySyncChangeEntity
import org.schabi.newpipe.database.sync.HistorySyncOriginStateEntity
import org.schabi.newpipe.database.sync.HistorySyncPeerStateEntity
import org.schabi.newpipe.database.sync.HistorySyncRecordEntity

internal interface HistorySyncStore {
    val localPeerId: String

    fun reconcileLocal(category: HistorySyncCategory)

    fun recordWatchEvent(
        streamId: Long,
        watchedAtEpochMillis: Long,
        repeatCount: Long
    )

    fun recordProgress(
        streamId: Long,
        progressMillis: Long,
        updatedAtEpochMillis: Long
    )

    fun recordWatchStreamDelete(streamId: Long)

    fun recordWatchAllDelete()

    fun recordProgressAllDelete()

    fun recordSearch(
        serviceId: Int,
        query: String,
        searchedAtEpochMillis: Long
    )

    fun recordSearchDelete(query: String)

    fun recordSearchAllDelete()

    fun recordLearningNoteUpsert(noteId: String)

    fun recordLearningNoteDelete(note: LearningNoteEntity)

    fun getKnownRevisions(category: HistorySyncCategory): Map<String, Long>

    fun getPendingChanges(
        category: HistorySyncCategory,
        peerId: String,
        limit: Int
    ): HistoryChangeBatch

    fun acknowledgePeer(
        category: HistorySyncCategory,
        peerId: String,
        knownRevisions: Map<String, Long>
    )

    fun applyChanges(
        category: HistorySyncCategory,
        changes: List<HistoryChange>
    ): HistoryApplyResult

    fun clearPeerKnowledge()
}

internal class RoomHistorySyncStore internal constructor(
    private val database: AppDatabase,
    override val localPeerId: String
) : HistorySyncStore {
    private val syncDao = database.historySyncDAO()
    private val searchHistoryDao = database.searchHistoryDAO()
    private val streamDao = database.streamDAO()
    private val streamHistoryDao = database.streamHistoryDAO()
    private val streamStateDao = database.streamStateDAO()
    private val learningNoteDao = database.learningNoteDAO()

    override fun reconcileLocal(category: HistorySyncCategory) {
        database.runInTransaction {
            ensureInitialized(category)
        }
    }

    override fun recordWatchEvent(
        streamId: Long,
        watchedAtEpochMillis: Long,
        repeatCount: Long
    ) {
        database.runInTransaction {
            val stream = requireStream(streamId)
            if (stream.isDeviceLocalHistoryStream()) {
                return@runInTransaction
            }
            ensureInitialized(HistorySyncCategory.WATCH)
            saveLocalChange(
                category = HistorySyncCategory.WATCH,
                recordId = HistoryRecordId.watchEvent(),
                recordType = HistoryRecordType.WATCH_EVENT,
                type = HistoryChangeType.UPSERT,
                record = SyncedHistoryRecord(
                    watchEvent = SyncedWatchEvent(
                        stream = SyncedHistoryStream.from(stream),
                        watchedAtEpochMillis = watchedAtEpochMillis,
                        repeatCount = repeatCount
                    )
                )
            )
        }
    }

    override fun recordProgress(
        streamId: Long,
        progressMillis: Long,
        updatedAtEpochMillis: Long
    ) {
        database.runInTransaction {
            val entity = requireStream(streamId)
            if (entity.isDeviceLocalHistoryStream()) {
                return@runInTransaction
            }
            ensureInitialized(HistorySyncCategory.WATCH)
            val stream = SyncedHistoryStream.from(entity)
            val recordId = HistoryRecordId.progress(stream.identity)
            val current = syncDao.getRecord(HistorySyncCategory.WATCH.name, recordId)
            val currentProgress = current?.takeUnless(HistorySyncRecordEntity::isDeleted)
                ?.let(::decodeRecord)
                ?.playbackProgress
            if (currentProgress?.progressMillis == progressMillis) {
                return@runInTransaction
            }
            saveLocalChange(
                category = HistorySyncCategory.WATCH,
                recordId = recordId,
                recordType = HistoryRecordType.PLAYBACK_PROGRESS,
                type = HistoryChangeType.UPSERT,
                record = SyncedHistoryRecord(
                    playbackProgress = SyncedPlaybackProgress(
                        stream = stream,
                        progressMillis = progressMillis,
                        updatedAtEpochMillis = updatedAtEpochMillis
                    )
                )
            )
        }
    }

    override fun recordWatchStreamDelete(streamId: Long) {
        database.runInTransaction {
            val entity = requireStream(streamId)
            if (entity.isDeviceLocalHistoryStream()) {
                return@runInTransaction
            }
            ensureInitialized(HistorySyncCategory.WATCH)
            val stream = SyncedHistoryStream.from(entity)
            saveLocalChange(
                category = HistorySyncCategory.WATCH,
                recordId = HistoryRecordId.watchStreamTombstone(stream.identity),
                recordType = HistoryRecordType.WATCH_STREAM_TOMBSTONE,
                type = HistoryChangeType.UPSERT,
                record = SyncedHistoryRecord(
                    watchStreamTombstone = SyncedWatchStreamTombstone(stream)
                )
            )
            saveLocalChange(
                category = HistorySyncCategory.WATCH,
                recordId = HistoryRecordId.progress(stream.identity),
                recordType = HistoryRecordType.PLAYBACK_PROGRESS,
                type = HistoryChangeType.DELETE,
                record = SyncedHistoryRecord(
                    playbackProgress = SyncedPlaybackProgress(
                        stream = stream,
                        progressMillis = 0,
                        updatedAtEpochMillis = System.currentTimeMillis()
                    )
                )
            )
        }
    }

    override fun recordWatchAllDelete() {
        database.runInTransaction {
            ensureInitialized(HistorySyncCategory.WATCH)
            saveLocalChange(
                category = HistorySyncCategory.WATCH,
                recordId = HistoryRecordId.watchAllTombstone(),
                recordType = HistoryRecordType.WATCH_ALL_TOMBSTONE,
                type = HistoryChangeType.UPSERT,
                record = null
            )
        }
    }

    override fun recordProgressAllDelete() {
        database.runInTransaction {
            ensureInitialized(HistorySyncCategory.WATCH)
            saveLocalChange(
                category = HistorySyncCategory.WATCH,
                recordId = HistoryRecordId.playbackAllTombstone(),
                recordType = HistoryRecordType.PLAYBACK_ALL_TOMBSTONE,
                type = HistoryChangeType.UPSERT,
                record = null
            )
        }
    }

    override fun recordSearch(
        serviceId: Int,
        query: String,
        searchedAtEpochMillis: Long
    ) {
        database.runInTransaction {
            ensureInitialized(HistorySyncCategory.SEARCH)
            saveLocalChange(
                category = HistorySyncCategory.SEARCH,
                recordId = HistoryRecordId.searchEvent(),
                recordType = HistoryRecordType.SEARCH_EVENT,
                type = HistoryChangeType.UPSERT,
                record = SyncedHistoryRecord(
                    searchEvent = SyncedSearchEvent(
                        serviceId = serviceId,
                        query = query.trim(),
                        searchedAtEpochMillis = searchedAtEpochMillis
                    )
                )
            )
        }
    }

    override fun recordSearchDelete(query: String) {
        database.runInTransaction {
            ensureInitialized(HistorySyncCategory.SEARCH)
            val canonicalQuery = query.trim()
            saveLocalChange(
                category = HistorySyncCategory.SEARCH,
                recordId = HistoryRecordId.searchQueryTombstone(canonicalQuery),
                recordType = HistoryRecordType.SEARCH_QUERY_TOMBSTONE,
                type = HistoryChangeType.UPSERT,
                record = SyncedHistoryRecord(
                    searchQueryTombstone = SyncedSearchQueryTombstone(canonicalQuery)
                )
            )
        }
    }

    override fun recordSearchAllDelete() {
        database.runInTransaction {
            ensureInitialized(HistorySyncCategory.SEARCH)
            saveLocalChange(
                category = HistorySyncCategory.SEARCH,
                recordId = HistoryRecordId.searchAllTombstone(),
                recordType = HistoryRecordType.SEARCH_ALL_TOMBSTONE,
                type = HistoryChangeType.UPSERT,
                record = null
            )
        }
    }

    override fun recordLearningNoteUpsert(noteId: String) {
        database.runInTransaction {
            ensureInitialized(HistorySyncCategory.LEARNING_NOTES)
            val note = learningNoteDao.getNote(noteId)
                ?: throw HistorySyncException("The learning note no longer exists")
            saveLocalChange(
                category = HistorySyncCategory.LEARNING_NOTES,
                recordId = note.noteId,
                recordType = HistoryRecordType.LEARNING_NOTE,
                type = HistoryChangeType.UPSERT,
                record = SyncedHistoryRecord(learningNote = note.toSyncedLearningNote())
            )
        }
    }

    override fun recordLearningNoteDelete(note: LearningNoteEntity) {
        database.runInTransaction {
            ensureInitialized(HistorySyncCategory.LEARNING_NOTES)
            saveLocalChange(
                category = HistorySyncCategory.LEARNING_NOTES,
                recordId = note.noteId,
                recordType = HistoryRecordType.LEARNING_NOTE,
                type = HistoryChangeType.DELETE,
                record = SyncedHistoryRecord(learningNote = note.toSyncedLearningNote())
            )
        }
    }

    override fun getKnownRevisions(
        category: HistorySyncCategory
    ): Map<String, Long> {
        return syncDao.getOriginStates(category.name)
            .filter { it.contiguousRevision > 0 }
            .associate { it.originPeerId to it.contiguousRevision }
    }

    override fun getPendingChanges(
        category: HistorySyncCategory,
        peerId: String,
        limit: Int
    ): HistoryChangeBatch {
        require(limit in 1..MAX_HISTORY_CHANGES_PER_BATCH)
        val peerKnowledge = syncDao.getPeerStates(category.name, peerId)
            .associate { it.originPeerId to it.acknowledgedRevision }
        val origins = syncDao.getChangeOrigins(category.name).sorted()
        val candidates = origins.flatMap { origin ->
            syncDao.getChangesAfter(
                category.name,
                origin,
                peerKnowledge[origin] ?: 0,
                limit
            )
        }.map(::toModel)
            .sortedBy(HistoryChange::versionStamp)
            .take(limit)
        val pendingCount = origins.sumOf { origin ->
            syncDao.countChangesAfter(
                category.name,
                origin,
                peerKnowledge[origin] ?: 0
            )
        }
        return HistoryChangeBatch(
            changes = candidates,
            hasMore = pendingCount > candidates.size.toLong()
        )
    }

    override fun acknowledgePeer(
        category: HistorySyncCategory,
        peerId: String,
        knownRevisions: Map<String, Long>
    ) {
        HistorySyncValidation.validateKnownRevisions(knownRevisions)
        database.runInTransaction {
            val localKnowledge = getKnownRevisions(category)
            val existing = syncDao.getPeerStates(category.name, peerId)
                .associate { it.originPeerId to it.acknowledgedRevision }
            knownRevisions.forEach { (origin, claimedRevision) ->
                val safeRevision = minOf(claimedRevision, localKnowledge[origin] ?: 0)
                val acknowledgedRevision = maxOf(existing[origin] ?: 0, safeRevision)
                if (acknowledgedRevision > 0) {
                    syncDao.upsertPeerState(
                        HistorySyncPeerStateEntity(
                            category = category.name,
                            peerId = peerId,
                            originPeerId = origin,
                            acknowledgedRevision = acknowledgedRevision
                        )
                    )
                }
            }
        }
    }

    override fun applyChanges(
        category: HistorySyncCategory,
        changes: List<HistoryChange>
    ): HistoryApplyResult {
        HistorySyncValidation.validateChanges(category, changes)
        return database.runInTransaction(
            Callable {
                val maximumAcceptedLamport = minOf(
                    syncDao.getMaximumLamportVersion(category.name) +
                        MAX_REMOTE_LAMPORT_ADVANCE,
                    MAX_SYNC_REVISION
                )
                if (changes.any { it.lamportVersion > maximumAcceptedLamport }) {
                    throw HistorySyncException(
                        "A history change advances the logical clock too far"
                    )
                }

                var accepted = 0
                var affected = 0
                val affectedStreams = linkedSetOf<HistoryStreamIdentity>()
                var materializeAllWatchStreams = false
                var materializeSearch = false
                val affectedLearningNotes = linkedSetOf<String>()

                changes.forEach { change ->
                    if (syncDao.insertChange(change.toEntity()) == -1L) {
                        return@forEach
                    }
                    accepted += 1
                    advanceContiguousRevision(category, change.originPeerId)

                    val currentRecord = syncDao.getRecord(category.name, change.recordId)
                    if (
                        currentRecord != null &&
                        change.versionStamp <= currentRecord.versionStamp
                    ) {
                        return@forEach
                    }
                    syncDao.upsertRecord(change.toRecordEntity(currentRecord))
                    affected += 1
                    when (change.recordType) {
                        HistoryRecordType.WATCH_EVENT ->
                            affectedStreams += requireNotNull(change.record?.watchEvent)
                                .stream.identity

                        HistoryRecordType.PLAYBACK_PROGRESS ->
                            affectedStreams += requireNotNull(change.record?.playbackProgress)
                                .stream.identity

                        HistoryRecordType.WATCH_STREAM_TOMBSTONE ->
                            affectedStreams += requireNotNull(
                                change.record?.watchStreamTombstone
                            ).stream.identity

                        HistoryRecordType.WATCH_ALL_TOMBSTONE,
                        HistoryRecordType.PLAYBACK_ALL_TOMBSTONE ->
                            materializeAllWatchStreams = true

                        HistoryRecordType.SEARCH_EVENT,
                        HistoryRecordType.SEARCH_QUERY_TOMBSTONE,
                        HistoryRecordType.SEARCH_ALL_TOMBSTONE ->
                            materializeSearch = true

                        HistoryRecordType.LEARNING_NOTE ->
                            affectedLearningNotes += change.recordId
                    }
                }

                if (materializeAllWatchStreams) {
                    affectedStreams += getAllWatchStreamIdentities()
                }
                affectedStreams.forEach(::materializeWatchStream)
                if (materializeSearch) {
                    materializeSearchHistory()
                }
                affectedLearningNotes.forEach(::materializeLearningNote)
                HistoryApplyResult(
                    acceptedChanges = accepted,
                    affectedRecords = affected
                )
            }
        )
    }

    override fun clearPeerKnowledge() {
        database.runInTransaction {
            HistorySyncCategory.entries.forEach { category ->
                syncDao.deletePeerStates(category.name)
            }
        }
    }

    private fun ensureInitialized(category: HistorySyncCategory) {
        if (syncDao.getOriginState(category.name, localPeerId) != null) {
            return
        }
        when (category) {
            HistorySyncCategory.WATCH -> initializeWatchHistory()
            HistorySyncCategory.SEARCH -> initializeSearchHistory()
            HistorySyncCategory.LEARNING_NOTES -> initializeLearningNotes()
        }
        if (syncDao.getOriginState(category.name, localPeerId) == null) {
            syncDao.upsertOriginState(
                HistorySyncOriginStateEntity(category.name, localPeerId, 0)
            )
        }
    }

    private fun initializeWatchHistory() {
        streamHistoryDao.getAllDirect().forEach { history ->
            val stream = streamDao.getStreamDirect(history.streamUid) ?: return@forEach
            if (stream.isDeviceLocalHistoryStream()) {
                return@forEach
            }
            saveLocalChange(
                category = HistorySyncCategory.WATCH,
                recordId = HistoryRecordId.watchEvent(),
                recordType = HistoryRecordType.WATCH_EVENT,
                type = HistoryChangeType.UPSERT,
                record = SyncedHistoryRecord(
                    watchEvent = SyncedWatchEvent(
                        stream = SyncedHistoryStream.from(stream),
                        watchedAtEpochMillis = history.accessDate.toInstant().toEpochMilli(),
                        repeatCount = history.repeatCount
                    )
                )
            )
        }
        streamStateDao.getAllDirect().forEach { state ->
            val stream = streamDao.getStreamDirect(state.streamUid) ?: return@forEach
            if (stream.isDeviceLocalHistoryStream()) {
                return@forEach
            }
            val syncedStream = SyncedHistoryStream.from(stream)
            saveLocalChange(
                category = HistorySyncCategory.WATCH,
                recordId = HistoryRecordId.progress(syncedStream.identity),
                recordType = HistoryRecordType.PLAYBACK_PROGRESS,
                type = HistoryChangeType.UPSERT,
                record = SyncedHistoryRecord(
                    playbackProgress = SyncedPlaybackProgress(
                        stream = syncedStream,
                        progressMillis = state.progressMillis,
                        updatedAtEpochMillis = System.currentTimeMillis()
                    )
                )
            )
        }
    }

    private fun initializeSearchHistory() {
        searchHistoryDao.getAllDirect().forEach { entry ->
            val query = entry.search?.trim()
            val searchedAt = entry.creationDate
            if (
                entry.serviceId < 0 ||
                query.isNullOrBlank() ||
                query.length > MAX_SEARCH_QUERY_LENGTH ||
                searchedAt == null
            ) {
                return@forEach
            }
            saveLocalChange(
                category = HistorySyncCategory.SEARCH,
                recordId = HistoryRecordId.searchEvent(),
                recordType = HistoryRecordType.SEARCH_EVENT,
                type = HistoryChangeType.UPSERT,
                record = SyncedHistoryRecord(
                    searchEvent = SyncedSearchEvent(
                        serviceId = entry.serviceId,
                        query = query,
                        searchedAtEpochMillis = searchedAt.toInstant().toEpochMilli()
                    )
                )
            )
        }
    }

    private fun initializeLearningNotes() {
        learningNoteDao.getAllDirect().forEach { note ->
            saveLocalChange(
                category = HistorySyncCategory.LEARNING_NOTES,
                recordId = note.noteId,
                recordType = HistoryRecordType.LEARNING_NOTE,
                type = HistoryChangeType.UPSERT,
                record = SyncedHistoryRecord(learningNote = note.toSyncedLearningNote())
            )
        }
    }

    private fun saveLocalChange(
        category: HistorySyncCategory,
        recordId: String,
        recordType: HistoryRecordType,
        type: HistoryChangeType,
        record: SyncedHistoryRecord?
    ) {
        val currentRecord = syncDao.getRecord(category.name, recordId)
        val originState = syncDao.getOriginState(category.name, localPeerId)
        val originRevision = incrementVersion(
            originState?.contiguousRevision ?: 0
        )
        val lamportVersion = incrementVersion(
            maxOf(
                syncDao.getMaximumLamportVersion(category.name),
                currentRecord?.lamportVersion ?: 0
            )
        )
        val change = HistoryChange(
            category = category,
            originPeerId = localPeerId,
            originRevision = originRevision,
            lamportVersion = lamportVersion,
            recordId = recordId,
            recordType = recordType,
            type = type,
            record = record
        )
        HistorySyncValidation.validateChanges(category, listOf(change))
        check(syncDao.insertChange(change.toEntity()) != -1L) {
            "The local history revision already exists"
        }
        syncDao.upsertOriginState(
            HistorySyncOriginStateEntity(category.name, localPeerId, originRevision)
        )
        syncDao.upsertRecord(change.toRecordEntity(currentRecord))
    }

    private fun materializeWatchStream(identity: HistoryStreamIdentity) {
        val records = syncDao.getRecords(HistorySyncCategory.WATCH.name)
        val streamRecords = records.filter { record ->
            decodeRecord(record)?.streamIdentity == identity
        }
        val stream = streamRecords
            .maxByOrNull { it.versionStamp }
            ?.let(::decodeRecord)
            ?.stream
        val existingStream = streamDao.getStreamDirect(identity.serviceId, identity.url)
        val streamId = stream?.let { streamDao.upsert(it.toEntity()) } ?: existingStream?.uid

        val watchCutoff = listOfNotNull(
            records.firstOrNull {
                it.recordId == HistoryRecordId.watchAllTombstone()
            }?.versionStamp,
            records.firstOrNull {
                it.recordId == HistoryRecordId.watchStreamTombstone(identity)
            }?.versionStamp
        ).maxOrNull()
        val events = streamRecords
            .filter { it.parsedRecordType == HistoryRecordType.WATCH_EVENT }
            .filterNot(HistorySyncRecordEntity::isDeleted)
            .filter { watchCutoff == null || it.versionStamp > watchCutoff }
            .mapNotNull { decodeRecord(it)?.watchEvent }
        if (streamId != null) {
            streamHistoryDao.deleteStreamHistory(streamId)
            if (events.isNotEmpty()) {
                streamHistoryDao.insert(
                    StreamHistoryEntity(
                        streamUid = streamId,
                        accessDate = epochMillisToOffsetDateTime(
                            events.maxOf(SyncedWatchEvent::watchedAtEpochMillis)
                        ),
                        repeatCount = events.fold(0L) { total, event ->
                            saturatedAdd(total, event.repeatCount)
                        }
                    )
                )
            }
        }

        val progressRecord = records.firstOrNull {
            it.recordId == HistoryRecordId.progress(identity)
        }
        val progressClear = records.firstOrNull {
            it.recordId == HistoryRecordId.playbackAllTombstone()
        }
        val progressIsCleared = progressRecord == null ||
            progressRecord.isDeleted ||
            (
                progressClear != null &&
                    progressClear.versionStamp >= progressRecord.versionStamp
                )
        if (streamId != null) {
            if (progressIsCleared) {
                streamStateDao.deleteState(streamId)
            } else {
                val progress = decodeRecord(requireNotNull(progressRecord))?.playbackProgress
                    ?: throw HistorySyncException("Stored playback progress is invalid")
                streamStateDao.upsert(
                    StreamStateEntity(streamId, progress.progressMillis)
                )
            }
        }
    }

    private fun materializeSearchHistory() {
        val records = syncDao.getRecords(HistorySyncCategory.SEARCH.name)
        val globalCutoff = records.firstOrNull {
            it.recordId == HistoryRecordId.searchAllTombstone()
        }?.versionStamp
        val queryCutoffs = records
            .filter {
                it.parsedRecordType == HistoryRecordType.SEARCH_QUERY_TOMBSTONE
            }
            .associate { record ->
                val query = decodeRecord(record)?.searchQueryTombstone?.query
                    ?: throw HistorySyncException("Stored search tombstone is invalid")
                query to record.versionStamp
            }
        val events = records.asSequence()
            .filter { it.parsedRecordType == HistoryRecordType.SEARCH_EVENT }
            .filterNot(HistorySyncRecordEntity::isDeleted)
            .filter { globalCutoff == null || it.versionStamp > globalCutoff }
            .filter { record ->
                val event = decodeRecord(record)?.searchEvent
                    ?: throw HistorySyncException("Stored search event is invalid")
                val cutoff = queryCutoffs[event.query]
                cutoff == null || record.versionStamp > cutoff
            }
            .map { record ->
                decodeRecord(record)?.searchEvent
                    ?: throw HistorySyncException("Stored search event is invalid")
            }
            .sortedBy(SyncedSearchEvent::searchedAtEpochMillis)
            .map { event ->
                SearchHistoryEntry(
                    creationDate = epochMillisToOffsetDateTime(
                        event.searchedAtEpochMillis
                    ),
                    serviceId = event.serviceId,
                    search = event.query
                )
            }
            .toList()
        searchHistoryDao.deleteAll()
        if (events.isNotEmpty()) {
            searchHistoryDao.insertAll(events)
        }
    }

    private fun materializeLearningNote(noteId: String) {
        val record = syncDao.getRecord(HistorySyncCategory.LEARNING_NOTES.name, noteId)
            ?: return
        if (record.isDeleted) {
            learningNoteDao.delete(noteId)
            return
        }
        val note = decodeRecord(record)?.learningNote
            ?: throw HistorySyncException("Stored learning note data is invalid")
        val streamId = streamDao.upsert(note.stream.toEntity())
        learningNoteDao.upsert(
            LearningNoteEntity(
                noteId = note.noteId,
                streamId = streamId,
                timestampMillis = note.timestampMillis,
                noteText = note.noteText,
                createdAtEpochMillis = note.createdAtEpochMillis,
                updatedAtEpochMillis = note.updatedAtEpochMillis
            )
        )
    }

    private fun LearningNoteEntity.toSyncedLearningNote(): SyncedLearningNote {
        return SyncedLearningNote(
            noteId = noteId,
            stream = SyncedHistoryStream.from(requireStream(streamId)),
            timestampMillis = timestampMillis,
            noteText = noteText,
            createdAtEpochMillis = createdAtEpochMillis,
            updatedAtEpochMillis = updatedAtEpochMillis
        )
    }

    private fun getAllWatchStreamIdentities(): Set<HistoryStreamIdentity> {
        return syncDao.getRecords(HistorySyncCategory.WATCH.name)
            .mapNotNull { decodeRecord(it)?.streamIdentity }
            .toSet()
    }

    private fun requireStream(streamId: Long): StreamEntity {
        return streamDao.getStreamDirect(streamId)
            ?: throw HistorySyncException("The history stream no longer exists")
    }

    private fun StreamEntity.isDeviceLocalHistoryStream(): Boolean {
        return isLocalMedia || serviceId < 0
    }

    private fun incrementVersion(value: Long): Long {
        if (value >= MAX_SYNC_REVISION) {
            throw HistorySyncException("The history journal version is exhausted")
        }
        return value + 1
    }

    private fun advanceContiguousRevision(
        category: HistorySyncCategory,
        originPeerId: String
    ) {
        var contiguous = syncDao.getOriginState(
            category.name,
            originPeerId
        )?.contiguousRevision ?: 0
        while (
            contiguous < MAX_SYNC_REVISION &&
            syncDao.hasChange(category.name, originPeerId, contiguous + 1)
        ) {
            contiguous += 1
        }
        syncDao.upsertOriginState(
            HistorySyncOriginStateEntity(category.name, originPeerId, contiguous)
        )
    }

    private fun toModel(entity: HistorySyncChangeEntity): HistoryChange {
        return HistoryChange(
            category = parseCategory(entity.category),
            originPeerId = entity.originPeerId,
            originRevision = entity.originRevision,
            lamportVersion = entity.lamportVersion,
            recordId = entity.recordId,
            recordType = parseRecordType(entity.recordType),
            type = parseChangeType(entity.changeType),
            record = entity.recordJson?.let(HistoryRecordCodec::decode)
        )
    }

    private fun HistoryChange.toEntity() = HistorySyncChangeEntity(
        category = category.name,
        originPeerId = originPeerId,
        originRevision = originRevision,
        lamportVersion = lamportVersion,
        recordId = recordId,
        recordType = recordType.name,
        changeType = type.name,
        recordJson = record?.let(HistoryRecordCodec::encode)
    )

    private fun HistoryChange.toRecordEntity(
        currentRecord: HistorySyncRecordEntity?
    ) = HistorySyncRecordEntity(
        category = category.name,
        recordId = recordId,
        recordType = recordType.name,
        lamportVersion = lamportVersion,
        originPeerId = originPeerId,
        originRevision = originRevision,
        isDeleted = type == HistoryChangeType.DELETE,
        recordJson = record?.let(HistoryRecordCodec::encode)
            ?: currentRecord?.recordJson
    )

    private fun decodeRecord(entity: HistorySyncRecordEntity): SyncedHistoryRecord? {
        return entity.recordJson?.let(HistoryRecordCodec::decode)
    }

    private fun parseCategory(value: String): HistorySyncCategory {
        return try {
            HistorySyncCategory.valueOf(value)
        } catch (error: IllegalArgumentException) {
            throw HistorySyncException(
                "The local history journal contains an invalid category",
                error
            )
        }
    }

    private fun parseRecordType(value: String): HistoryRecordType {
        return try {
            HistoryRecordType.valueOf(value)
        } catch (error: IllegalArgumentException) {
            throw HistorySyncException(
                "The local history journal contains an invalid record type",
                error
            )
        }
    }

    private fun parseChangeType(value: String): HistoryChangeType {
        return try {
            HistoryChangeType.valueOf(value)
        } catch (error: IllegalArgumentException) {
            throw HistorySyncException(
                "The local history journal contains an invalid change type",
                error
            )
        }
    }

    private val HistorySyncRecordEntity.versionStamp: HistoryVersionStamp
        get() = HistoryVersionStamp(
            lamportVersion,
            originPeerId,
            originRevision
        )

    private val HistorySyncRecordEntity.parsedRecordType: HistoryRecordType
        get() = parseRecordType(recordType)

    private val SyncedHistoryRecord.streamIdentity: HistoryStreamIdentity?
        get() = stream?.identity

    private val SyncedHistoryRecord.stream: SyncedHistoryStream?
        get() = watchEvent?.stream
            ?: playbackProgress?.stream
            ?: watchStreamTombstone?.stream
            ?: learningNote?.stream

    private fun epochMillisToOffsetDateTime(epochMillis: Long): OffsetDateTime {
        return OffsetDateTime.ofInstant(
            Instant.ofEpochMilli(epochMillis),
            ZoneOffset.UTC
        )
    }

    private fun saturatedAdd(first: Long, second: Long): Long {
        return if (Long.MAX_VALUE - first < second) {
            Long.MAX_VALUE
        } else {
            first + second
        }
    }

    companion object {
        private const val MAX_REMOTE_LAMPORT_ADVANCE = 1_000_000L

        fun get(context: Context): RoomHistorySyncStore {
            val applicationContext = context.applicationContext
            val stateRepository = AndroidSyncStateRepository(applicationContext)
            return RoomHistorySyncStore(
                database = NewPipeDatabase.getInstance(applicationContext),
                localPeerId = stateRepository.loadOrCreateIdentity().peerId.toBase58()
            )
        }
    }
}

class HistorySyncRecorder private constructor(context: Context) {
    private val store = RoomHistorySyncStore.get(context.applicationContext)

    fun recordWatchEvent(
        streamId: Long,
        watchedAtEpochMillis: Long,
        repeatCount: Long
    ) = store.recordWatchEvent(streamId, watchedAtEpochMillis, repeatCount)

    fun recordProgress(
        streamId: Long,
        progressMillis: Long,
        updatedAtEpochMillis: Long
    ) = store.recordProgress(streamId, progressMillis, updatedAtEpochMillis)

    fun recordWatchStreamDelete(streamId: Long) = store.recordWatchStreamDelete(streamId)

    fun recordWatchAllDelete() = store.recordWatchAllDelete()

    fun recordProgressAllDelete() = store.recordProgressAllDelete()

    fun recordSearch(
        serviceId: Int,
        query: String,
        searchedAtEpochMillis: Long
    ) = store.recordSearch(serviceId, query, searchedAtEpochMillis)

    fun recordSearchDelete(query: String) = store.recordSearchDelete(query)

    fun recordSearchAllDelete() = store.recordSearchAllDelete()

    fun recordLearningNoteUpsert(noteId: String) = store.recordLearningNoteUpsert(noteId)

    fun recordLearningNoteDelete(note: LearningNoteEntity) = store.recordLearningNoteDelete(note)

    companion object {
        @JvmStatic
        fun get(context: Context): HistorySyncRecorder {
            return HistorySyncRecorder(context.applicationContext)
        }
    }
}

private object HistoryRecordCodec {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = false
    }

    fun encode(record: SyncedHistoryRecord): String = json.encodeToString(record)

    fun decode(value: String): SyncedHistoryRecord {
        return try {
            json.decodeFromString(value)
        } catch (error: Exception) {
            throw HistorySyncException("Stored history synchronization data is malformed", error)
        }
    }
}
