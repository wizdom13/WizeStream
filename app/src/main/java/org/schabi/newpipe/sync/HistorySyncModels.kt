/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import io.libp2p.core.PeerId
import java.security.MessageDigest
import java.util.UUID
import kotlinx.serialization.Serializable
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamType

internal const val HISTORY_SYNC_PROTOCOL_ID = "/wizestream/history/1.0.0"
internal const val HISTORY_SYNC_VERSION = 1
internal const val MAX_HISTORY_CHANGES_PER_BATCH = 8
internal const val MAX_HISTORY_URL_LENGTH = 4_096
internal const val MAX_HISTORY_TITLE_LENGTH = 1_024
internal const val MAX_HISTORY_UPLOADER_LENGTH = 512
internal const val MAX_SEARCH_QUERY_LENGTH = 4_096
internal const val MAX_LEARNING_NOTE_LENGTH = 10_000

@Serializable
enum class HistorySyncCategory {
    WATCH,
    SEARCH,
    LEARNING_NOTES
}

@Serializable
internal enum class HistoryRecordType {
    WATCH_EVENT,
    PLAYBACK_PROGRESS,
    WATCH_STREAM_TOMBSTONE,
    WATCH_ALL_TOMBSTONE,
    PLAYBACK_ALL_TOMBSTONE,
    SEARCH_EVENT,
    SEARCH_QUERY_TOMBSTONE,
    SEARCH_ALL_TOMBSTONE,
    LEARNING_NOTE
}

@Serializable
internal enum class HistoryChangeType {
    UPSERT,
    DELETE
}

@Serializable
internal data class SyncedHistoryStream(
    val serviceId: Int,
    val url: String,
    val title: String,
    val streamType: String,
    val duration: Long,
    val uploader: String,
    val uploaderUrl: String? = null,
    val thumbnailUrl: String? = null
) {
    val identity: HistoryStreamIdentity
        get() = HistoryStreamIdentity(serviceId, url)

    fun toEntity() = StreamEntity(
        serviceId = serviceId,
        url = url,
        title = title,
        streamType = try {
            StreamType.valueOf(streamType)
        } catch (_: IllegalArgumentException) {
            StreamType.VIDEO_STREAM
        },
        duration = duration,
        uploader = uploader,
        uploaderUrl = uploaderUrl,
        thumbnailUrl = thumbnailUrl
    )

    companion object {
        fun from(entity: StreamEntity) = SyncedHistoryStream(
            serviceId = entity.serviceId,
            url = entity.url.trim(),
            title = entity.title.take(MAX_HISTORY_TITLE_LENGTH),
            streamType = entity.streamType.name,
            duration = entity.duration,
            uploader = entity.uploader.take(MAX_HISTORY_UPLOADER_LENGTH),
            uploaderUrl = entity.uploaderUrl?.take(MAX_HISTORY_URL_LENGTH),
            thumbnailUrl = entity.thumbnailUrl?.take(MAX_HISTORY_URL_LENGTH)
        )
    }
}

@Serializable
internal data class SyncedWatchEvent(
    val stream: SyncedHistoryStream,
    val watchedAtEpochMillis: Long,
    val repeatCount: Long
)

@Serializable
internal data class SyncedPlaybackProgress(
    val stream: SyncedHistoryStream,
    val progressMillis: Long,
    val updatedAtEpochMillis: Long
)

@Serializable
internal data class SyncedWatchStreamTombstone(
    val stream: SyncedHistoryStream
)

@Serializable
internal data class SyncedSearchEvent(
    val serviceId: Int,
    val query: String,
    val searchedAtEpochMillis: Long
)

@Serializable
internal data class SyncedSearchQueryTombstone(
    val query: String
)

@Serializable
internal data class SyncedLearningNote(
    val noteId: String,
    val stream: SyncedHistoryStream,
    val timestampMillis: Long,
    val noteText: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

@Serializable
internal data class SyncedHistoryRecord(
    val watchEvent: SyncedWatchEvent? = null,
    val playbackProgress: SyncedPlaybackProgress? = null,
    val watchStreamTombstone: SyncedWatchStreamTombstone? = null,
    val searchEvent: SyncedSearchEvent? = null,
    val searchQueryTombstone: SyncedSearchQueryTombstone? = null,
    val learningNote: SyncedLearningNote? = null
)

@Serializable
internal data class HistoryChange(
    val category: HistorySyncCategory,
    val originPeerId: String,
    val originRevision: Long,
    val lamportVersion: Long,
    val recordId: String,
    val recordType: HistoryRecordType,
    val type: HistoryChangeType,
    val record: SyncedHistoryRecord? = null
) {
    val versionStamp: HistoryVersionStamp
        get() = HistoryVersionStamp(
            lamportVersion,
            originPeerId,
            originRevision
        )
}

@Serializable
internal data class HistorySyncRequest(
    val version: Int = HISTORY_SYNC_VERSION,
    val category: HistorySyncCategory,
    val knownRevisions: Map<String, Long>,
    val changes: List<HistoryChange>,
    val hasMore: Boolean
)

@Serializable
internal data class HistorySyncResponse(
    val accepted: Boolean,
    val category: HistorySyncCategory,
    val error: String? = null,
    val knownRevisions: Map<String, Long> = emptyMap(),
    val changes: List<HistoryChange> = emptyList(),
    val hasMore: Boolean = false
)

data class HistorySyncResult(
    val peer: TrustedPeer,
    val category: HistorySyncCategory,
    val sentChanges: Int,
    val receivedChanges: Int,
    val affectedRecords: Int,
    val rounds: Int
)

internal data class HistoryStreamIdentity(
    val serviceId: Int,
    val url: String
)

internal data class HistoryVersionStamp(
    val lamportVersion: Long,
    val originPeerId: String,
    val originRevision: Long
) : Comparable<HistoryVersionStamp> {
    override fun compareTo(other: HistoryVersionStamp): Int {
        return compareValuesBy(
            this,
            other,
            HistoryVersionStamp::lamportVersion,
            HistoryVersionStamp::originPeerId,
            HistoryVersionStamp::originRevision
        )
    }
}

internal data class HistoryChangeBatch(
    val changes: List<HistoryChange>,
    val hasMore: Boolean
)

internal data class HistoryApplyResult(
    val acceptedChanges: Int,
    val affectedRecords: Int
)

internal object HistorySyncValidation {
    fun validateRequest(request: HistorySyncRequest) {
        if (request.version != HISTORY_SYNC_VERSION) {
            throw HistorySyncException(
                "Unsupported history synchronization version: ${request.version}"
            )
        }
        validateKnownRevisions(request.knownRevisions)
        validateChanges(request.category, request.changes)
    }

    fun validateResponse(
        expectedCategory: HistorySyncCategory,
        response: HistorySyncResponse
    ) {
        if (response.category != expectedCategory) {
            throw HistorySyncException(
                "The remote device returned the wrong history category"
            )
        }
        if (!response.accepted) {
            if (response.error.isNullOrBlank()) {
                throw HistorySyncException("The remote device rejected history synchronization")
            }
            if (response.error.length > MAX_SYNC_ERROR_LENGTH) {
                throw HistorySyncException("The history synchronization error is too large")
            }
            return
        }
        if (response.error != null) {
            throw HistorySyncException(
                "A successful history synchronization response has an error"
            )
        }
        validateKnownRevisions(response.knownRevisions)
        validateChanges(response.category, response.changes)
    }

    fun validateKnownRevisions(knownRevisions: Map<String, Long>) {
        if (knownRevisions.size > MAX_SYNC_ORIGINS) {
            throw HistorySyncException("The history synchronization clock has too many origins")
        }
        knownRevisions.forEach { (peerId, revision) ->
            validatePeerId(peerId)
            if (revision !in 0..MAX_SYNC_REVISION) {
                throw HistorySyncException(
                    "A history synchronization revision is outside the supported range"
                )
            }
        }
    }

    fun validateChanges(
        category: HistorySyncCategory,
        changes: List<HistoryChange>
    ) {
        if (changes.size > MAX_HISTORY_CHANGES_PER_BATCH) {
            throw HistorySyncException("Too many history changes were sent")
        }
        val revisions = hashSetOf<Pair<String, Long>>()
        changes.forEach { change ->
            validatePeerId(change.originPeerId)
            if (
                change.category != category ||
                change.originRevision !in 1..MAX_SYNC_REVISION ||
                change.lamportVersion !in 1..MAX_SYNC_REVISION
            ) {
                throw HistorySyncException("A history change has an invalid category or version")
            }
            if (!revisions.add(change.originPeerId to change.originRevision)) {
                throw HistorySyncException("A history change was sent more than once")
            }
            validateRecord(change)
        }
    }

    private fun validateRecord(change: HistoryChange) {
        val populatedRecords = change.record?.let { record ->
            listOfNotNull(
                record.watchEvent,
                record.playbackProgress,
                record.watchStreamTombstone,
                record.searchEvent,
                record.searchQueryTombstone,
                record.learningNote
            ).size
        } ?: 0
        when (change.recordType) {
            HistoryRecordType.WATCH_EVENT -> {
                requireCategory(change, HistorySyncCategory.WATCH)
                validateUuid(change.recordId)
                requireUpsert(change)
                val event = change.record?.watchEvent
                    ?: throw HistorySyncException("A watch event has no data")
                require(populatedRecords == 1)
                validateStream(event.stream)
                validateEpochMillis(event.watchedAtEpochMillis)
                if (event.repeatCount < 0) {
                    throw HistorySyncException("A watch event has an invalid repeat count")
                }
            }

            HistoryRecordType.PLAYBACK_PROGRESS -> {
                requireCategory(change, HistorySyncCategory.WATCH)
                val progress = change.record?.playbackProgress
                    ?: throw HistorySyncException("A playback update has no data")
                require(populatedRecords == 1)
                validateStream(progress.stream)
                if (
                    change.recordId != HistoryRecordId.progress(progress.stream.identity) ||
                    progress.progressMillis < 0
                ) {
                    throw HistorySyncException("A playback update has invalid progress")
                }
                validateEpochMillis(progress.updatedAtEpochMillis)
            }

            HistoryRecordType.WATCH_STREAM_TOMBSTONE -> {
                requireCategory(change, HistorySyncCategory.WATCH)
                requireUpsert(change)
                val tombstone = change.record?.watchStreamTombstone
                    ?: throw HistorySyncException("A watch deletion has no stream identity")
                require(populatedRecords == 1)
                validateStream(tombstone.stream)
                if (
                    change.recordId !=
                    HistoryRecordId.watchStreamTombstone(tombstone.stream.identity)
                ) {
                    throw HistorySyncException("A watch deletion has an invalid identity")
                }
            }

            HistoryRecordType.WATCH_ALL_TOMBSTONE -> {
                requireCategory(change, HistorySyncCategory.WATCH)
                requireGlobalTombstone(change, HistoryRecordId.watchAllTombstone())
            }

            HistoryRecordType.PLAYBACK_ALL_TOMBSTONE -> {
                requireCategory(change, HistorySyncCategory.WATCH)
                requireGlobalTombstone(change, HistoryRecordId.playbackAllTombstone())
            }

            HistoryRecordType.SEARCH_EVENT -> {
                requireCategory(change, HistorySyncCategory.SEARCH)
                validateUuid(change.recordId)
                requireUpsert(change)
                val event = change.record?.searchEvent
                    ?: throw HistorySyncException("A search event has no data")
                require(populatedRecords == 1)
                validateQuery(event.query)
                validateEpochMillis(event.searchedAtEpochMillis)
                if (event.serviceId < 0) {
                    throw HistorySyncException("A search event has an invalid service")
                }
            }

            HistoryRecordType.SEARCH_QUERY_TOMBSTONE -> {
                requireCategory(change, HistorySyncCategory.SEARCH)
                requireUpsert(change)
                val tombstone = change.record?.searchQueryTombstone
                    ?: throw HistorySyncException("A search deletion has no query")
                require(populatedRecords == 1)
                validateQuery(tombstone.query)
                if (
                    change.recordId != HistoryRecordId.searchQueryTombstone(tombstone.query)
                ) {
                    throw HistorySyncException("A search deletion has an invalid identity")
                }
            }

            HistoryRecordType.SEARCH_ALL_TOMBSTONE -> {
                requireCategory(change, HistorySyncCategory.SEARCH)
                requireGlobalTombstone(change, HistoryRecordId.searchAllTombstone())
            }

            HistoryRecordType.LEARNING_NOTE -> {
                requireCategory(change, HistorySyncCategory.LEARNING_NOTES)
                validateUuid(change.recordId)
                val note = change.record?.learningNote
                    ?: throw HistorySyncException("A learning note has no data")
                require(populatedRecords == 1)
                validateUuid(note.noteId)
                validateStream(note.stream)
                validateEpochMillis(note.createdAtEpochMillis)
                validateEpochMillis(note.updatedAtEpochMillis)
                if (
                    change.recordId != note.noteId ||
                    note.timestampMillis < 0 ||
                    note.noteText.isBlank() ||
                    note.noteText != note.noteText.trim() ||
                    note.noteText.length > MAX_LEARNING_NOTE_LENGTH ||
                    note.updatedAtEpochMillis < note.createdAtEpochMillis
                ) {
                    throw HistorySyncException("A learning note is invalid")
                }
            }
        }
    }

    private fun requireCategory(
        change: HistoryChange,
        expected: HistorySyncCategory
    ) {
        if (change.category != expected) {
            throw HistorySyncException("A history record is in the wrong category")
        }
    }

    private fun requireUpsert(change: HistoryChange) {
        if (change.type != HistoryChangeType.UPSERT) {
            throw HistorySyncException("This history record cannot be deleted")
        }
    }

    private fun requireGlobalTombstone(
        change: HistoryChange,
        expectedRecordId: String
    ) {
        requireUpsert(change)
        if (change.recordId != expectedRecordId || change.record != null) {
            throw HistorySyncException("A history clear tombstone is invalid")
        }
    }

    private fun validateStream(stream: SyncedHistoryStream) {
        if (
            stream.serviceId < 0 ||
            stream.url.isBlank() ||
            stream.url != stream.url.trim() ||
            stream.url.length > MAX_HISTORY_URL_LENGTH ||
            stream.title.length > MAX_HISTORY_TITLE_LENGTH ||
            stream.uploader.length > MAX_HISTORY_UPLOADER_LENGTH ||
            (stream.uploaderUrl?.length ?: 0) > MAX_HISTORY_URL_LENGTH ||
            (stream.thumbnailUrl?.length ?: 0) > MAX_HISTORY_URL_LENGTH ||
            runCatching { StreamType.valueOf(stream.streamType) }.isFailure
        ) {
            throw HistorySyncException("History stream metadata is invalid")
        }
    }

    private fun validateQuery(query: String) {
        if (
            query.isBlank() ||
            query != query.trim() ||
            query.length > MAX_SEARCH_QUERY_LENGTH
        ) {
            throw HistorySyncException("A search query is invalid")
        }
    }

    private fun validateEpochMillis(value: Long) {
        if (value !in 0..MAX_EPOCH_MILLIS) {
            throw HistorySyncException("A history timestamp is invalid")
        }
    }

    private fun validatePeerId(peerId: String) {
        try {
            PeerId.fromBase58(peerId)
        } catch (error: Exception) {
            throw HistorySyncException("A history synchronization PeerID is invalid", error)
        }
    }

    private fun validateUuid(value: String) {
        try {
            if (UUID.fromString(value).toString() != value) {
                throw IllegalArgumentException("Noncanonical UUID")
            }
        } catch (error: IllegalArgumentException) {
            throw HistorySyncException("A history record UUID is invalid", error)
        }
    }

    private const val MAX_SYNC_ERROR_LENGTH = 512
    private const val MAX_EPOCH_MILLIS = 253_402_300_799_999L
}

internal object HistoryRecordId {
    fun watchEvent(): String = UUID.randomUUID().toString()

    fun progress(stream: HistoryStreamIdentity): String {
        return digest("progress\u0000${stream.serviceId}\u0000${stream.url.trim()}")
    }

    fun watchStreamTombstone(stream: HistoryStreamIdentity): String {
        return digest("watch-delete\u0000${stream.serviceId}\u0000${stream.url.trim()}")
    }

    fun watchAllTombstone(): String = digest("watch-delete-all")

    fun playbackAllTombstone(): String = digest("playback-delete-all")

    fun searchEvent(): String = UUID.randomUUID().toString()

    fun searchQueryTombstone(query: String): String {
        return digest("search-delete\u0000${query.trim()}")
    }

    fun searchAllTombstone(): String = digest("search-delete-all")

    private fun digest(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                val item = byte.toInt() and 0xff
                append(HEX_DIGITS[item ushr 4])
                append(HEX_DIGITS[item and 0x0f])
            }
        }
    }

    private const val HEX_DIGITS = "0123456789abcdef"
}

class HistorySyncException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
