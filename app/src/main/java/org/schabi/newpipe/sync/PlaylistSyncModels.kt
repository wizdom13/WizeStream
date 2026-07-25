/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import io.libp2p.core.PeerId
import java.security.MessageDigest
import java.util.UUID
import kotlinx.serialization.Serializable
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamType

internal const val PLAYLIST_SYNC_PROTOCOL_ID = "/wizestream/playlists/1.0.0"
internal const val PLAYLIST_SYNC_VERSION = 1
internal const val MAX_PLAYLIST_CHANGES_PER_BATCH = 8
internal const val MAX_PLAYLIST_ITEMS = 5_000
internal const val MAX_PLAYLIST_NAME_LENGTH = 512
internal const val MAX_PLAYLIST_URL_LENGTH = 4_096
internal const val MAX_PLAYLIST_TITLE_LENGTH = 1_024
internal const val MAX_PLAYLIST_UPLOADER_LENGTH = 512

@Serializable
internal enum class PlaylistRecordType {
    LOCAL_PLAYLIST,
    LOCAL_PLAYLIST_ITEM,
    LOCAL_PLAYLIST_ORDER,
    REMOTE_PLAYLIST
}

@Serializable
internal enum class PlaylistChangeType {
    UPSERT,
    DELETE
}

@Serializable
internal data class SyncedLocalPlaylist(
    val name: String?,
    val isThumbnailPermanent: Boolean,
    val thumbnailServiceId: Int? = null,
    val thumbnailUrl: String? = null,
    val displayIndex: Long
)

@Serializable
internal data class SyncedStream(
    val serviceId: Int,
    val url: String,
    val title: String,
    val streamType: String,
    val duration: Long,
    val uploader: String,
    val uploaderUrl: String? = null,
    val thumbnailUrl: String? = null
) {
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
        fun from(entity: StreamEntity) = SyncedStream(
            serviceId = entity.serviceId,
            url = entity.url.trim(),
            title = entity.title.take(MAX_PLAYLIST_TITLE_LENGTH),
            streamType = entity.streamType.name,
            duration = entity.duration,
            uploader = entity.uploader.take(MAX_PLAYLIST_UPLOADER_LENGTH),
            uploaderUrl = entity.uploaderUrl?.take(MAX_PLAYLIST_URL_LENGTH),
            thumbnailUrl = entity.thumbnailUrl?.take(MAX_PLAYLIST_URL_LENGTH)
        )
    }
}

@Serializable
internal data class SyncedPlaylistItem(
    val playlistRecordId: String,
    val stream: SyncedStream
)

@Serializable
internal data class SyncedPlaylistOrder(
    val playlistRecordId: String,
    val itemRecordIds: List<String>
)

@Serializable
internal data class SyncedRemotePlaylist(
    val serviceId: Int,
    val url: String,
    val name: String?,
    val thumbnailUrl: String?,
    val uploader: String?,
    val displayIndex: Long,
    val streamCount: Long?
) {
    fun toEntity() = PlaylistRemoteEntity(
        serviceId = serviceId,
        orderingName = name,
        url = url,
        thumbnailUrl = thumbnailUrl,
        uploader = uploader,
        displayIndex = displayIndex,
        streamCount = streamCount
    )

    companion object {
        fun from(entity: PlaylistRemoteEntity) = SyncedRemotePlaylist(
            serviceId = entity.serviceId,
            url = requireNotNull(entity.url).trim(),
            name = entity.orderingName?.take(MAX_PLAYLIST_NAME_LENGTH),
            thumbnailUrl = entity.thumbnailUrl?.take(MAX_PLAYLIST_URL_LENGTH),
            uploader = entity.uploader?.take(MAX_PLAYLIST_UPLOADER_LENGTH),
            displayIndex = entity.displayIndex,
            streamCount = entity.streamCount
        )
    }
}

@Serializable
internal data class SyncedPlaylistRecord(
    val localPlaylist: SyncedLocalPlaylist? = null,
    val localItem: SyncedPlaylistItem? = null,
    val localOrder: SyncedPlaylistOrder? = null,
    val remotePlaylist: SyncedRemotePlaylist? = null
)

@Serializable
internal data class PlaylistChange(
    val originPeerId: String,
    val originRevision: Long,
    val lamportVersion: Long,
    val recordId: String,
    val recordType: PlaylistRecordType,
    val parentRecordId: String? = null,
    val type: PlaylistChangeType,
    val record: SyncedPlaylistRecord? = null
) {
    val versionStamp: PlaylistVersionStamp
        get() = PlaylistVersionStamp(
            lamportVersion,
            originPeerId,
            originRevision
        )
}

@Serializable
internal data class PlaylistSyncRequest(
    val version: Int = PLAYLIST_SYNC_VERSION,
    val knownRevisions: Map<String, Long>,
    val changes: List<PlaylistChange>,
    val hasMore: Boolean
)

@Serializable
internal data class PlaylistSyncResponse(
    val accepted: Boolean,
    val error: String? = null,
    val knownRevisions: Map<String, Long> = emptyMap(),
    val changes: List<PlaylistChange> = emptyList(),
    val hasMore: Boolean = false
)

data class PlaylistSyncResult(
    val peer: TrustedPeer,
    val sentChanges: Int,
    val receivedChanges: Int,
    val changedPlaylists: Int,
    val rounds: Int
)

internal data class PlaylistVersionStamp(
    val lamportVersion: Long,
    val originPeerId: String,
    val originRevision: Long
) : Comparable<PlaylistVersionStamp> {
    override fun compareTo(other: PlaylistVersionStamp): Int {
        return compareValuesBy(
            this,
            other,
            PlaylistVersionStamp::lamportVersion,
            PlaylistVersionStamp::originPeerId,
            PlaylistVersionStamp::originRevision
        )
    }
}

internal data class PlaylistChangeBatch(
    val changes: List<PlaylistChange>,
    val hasMore: Boolean
)

internal data class PlaylistApplyResult(
    val acceptedChanges: Int,
    val changedPlaylists: Int
)

internal object PlaylistSyncValidation {
    fun validateRequest(request: PlaylistSyncRequest) {
        if (request.version != PLAYLIST_SYNC_VERSION) {
            throw PlaylistSyncException(
                "Unsupported playlist synchronization version: ${request.version}"
            )
        }
        validateKnownRevisions(request.knownRevisions)
        validateChanges(request.changes)
    }

    fun validateResponse(response: PlaylistSyncResponse) {
        if (!response.accepted) {
            if (response.error.isNullOrBlank()) {
                throw PlaylistSyncException("The remote device rejected playlist synchronization")
            }
            if (response.error.length > MAX_SYNC_ERROR_LENGTH) {
                throw PlaylistSyncException("The playlist synchronization error is too large")
            }
            return
        }
        if (response.error != null) {
            throw PlaylistSyncException(
                "A successful playlist synchronization response has an error"
            )
        }
        validateKnownRevisions(response.knownRevisions)
        validateChanges(response.changes)
    }

    fun validateKnownRevisions(knownRevisions: Map<String, Long>) {
        if (knownRevisions.size > MAX_SYNC_ORIGINS) {
            throw PlaylistSyncException("The playlist synchronization clock has too many origins")
        }
        knownRevisions.forEach { (peerId, revision) ->
            validatePeerId(peerId)
            if (revision !in 0..MAX_SYNC_REVISION) {
                throw PlaylistSyncException(
                    "A playlist synchronization revision is outside the supported range"
                )
            }
        }
    }

    fun validateChanges(changes: List<PlaylistChange>) {
        if (changes.size > MAX_PLAYLIST_CHANGES_PER_BATCH) {
            throw PlaylistSyncException("Too many playlist changes were sent")
        }
        val revisions = hashSetOf<Pair<String, Long>>()
        changes.forEach { change ->
            validatePeerId(change.originPeerId)
            if (
                change.originRevision !in 1..MAX_SYNC_REVISION ||
                change.lamportVersion !in 1..MAX_SYNC_REVISION
            ) {
                throw PlaylistSyncException("A playlist change has an invalid version")
            }
            if (!revisions.add(change.originPeerId to change.originRevision)) {
                throw PlaylistSyncException("A playlist change was sent more than once")
            }
            validateRecordIdentity(change)
            when (change.type) {
                PlaylistChangeType.UPSERT -> validateRecord(
                    change,
                    change.record ?: throw PlaylistSyncException(
                        "A playlist update has no record data"
                    )
                )

                PlaylistChangeType.DELETE -> {
                    change.record?.let { validateRecord(change, it) }
                    if (
                        change.recordType == PlaylistRecordType.REMOTE_PLAYLIST &&
                        change.record?.remotePlaylist == null
                    ) {
                        throw PlaylistSyncException(
                            "A remote playlist deletion has no record identity"
                        )
                    }
                }
            }
        }
    }

    private fun validateRecordIdentity(change: PlaylistChange) {
        when (change.recordType) {
            PlaylistRecordType.LOCAL_PLAYLIST -> {
                validateUuid(change.recordId)
                if (change.parentRecordId != null) {
                    throw PlaylistSyncException("A local playlist has an unexpected parent")
                }
            }

            PlaylistRecordType.LOCAL_PLAYLIST_ITEM -> {
                validateUuid(change.recordId)
                validateUuid(
                    change.parentRecordId
                        ?: throw PlaylistSyncException("A playlist item has no parent")
                )
            }

            PlaylistRecordType.LOCAL_PLAYLIST_ORDER -> {
                val parent = change.parentRecordId
                    ?: throw PlaylistSyncException("A playlist order has no parent")
                validateUuid(parent)
                if (change.recordId != PlaylistRecordId.order(parent)) {
                    throw PlaylistSyncException("A playlist order identity is invalid")
                }
            }

            PlaylistRecordType.REMOTE_PLAYLIST -> {
                if (change.parentRecordId != null) {
                    throw PlaylistSyncException("A remote playlist has an unexpected parent")
                }
                if (change.recordId.length != SHA_256_HEX_LENGTH) {
                    throw PlaylistSyncException("A remote playlist identity is invalid")
                }
            }
        }
    }

    private fun validateRecord(
        change: PlaylistChange,
        record: SyncedPlaylistRecord
    ) {
        val populatedRecords = listOfNotNull(
            record.localPlaylist,
            record.localItem,
            record.localOrder,
            record.remotePlaylist
        )
        if (populatedRecords.size != 1) {
            throw PlaylistSyncException("Playlist record data is ambiguous")
        }
        when (change.recordType) {
            PlaylistRecordType.LOCAL_PLAYLIST -> validateLocalPlaylist(
                record.localPlaylist
                    ?: throw PlaylistSyncException("Local playlist data is missing")
            )

            PlaylistRecordType.LOCAL_PLAYLIST_ITEM -> validateLocalItem(
                change,
                record.localItem
                    ?: throw PlaylistSyncException("Playlist item data is missing")
            )

            PlaylistRecordType.LOCAL_PLAYLIST_ORDER -> validateLocalOrder(
                change,
                record.localOrder
                    ?: throw PlaylistSyncException("Playlist order data is missing")
            )

            PlaylistRecordType.REMOTE_PLAYLIST -> validateRemotePlaylist(
                change,
                record.remotePlaylist
                    ?: throw PlaylistSyncException("Remote playlist data is missing")
            )
        }
    }

    private fun validateLocalPlaylist(playlist: SyncedLocalPlaylist) {
        if (
            (playlist.name?.length ?: 0) > MAX_PLAYLIST_NAME_LENGTH ||
            playlist.displayIndex < -1 ||
            (playlist.thumbnailServiceId == null) != (playlist.thumbnailUrl == null) ||
            (playlist.thumbnailServiceId ?: 0) < 0 ||
            (playlist.thumbnailUrl?.length ?: 0) > MAX_PLAYLIST_URL_LENGTH ||
            playlist.thumbnailUrl?.let { it.isBlank() || it != it.trim() } == true
        ) {
            throw PlaylistSyncException("Local playlist metadata is invalid")
        }
    }

    private fun validateLocalItem(
        change: PlaylistChange,
        item: SyncedPlaylistItem
    ) {
        if (item.playlistRecordId != change.parentRecordId) {
            throw PlaylistSyncException("Playlist item data has the wrong parent")
        }
        validateStream(item.stream)
    }

    private fun validateLocalOrder(
        change: PlaylistChange,
        order: SyncedPlaylistOrder
    ) {
        if (
            order.playlistRecordId != change.parentRecordId ||
            order.itemRecordIds.size > MAX_PLAYLIST_ITEMS ||
            order.itemRecordIds.distinct().size != order.itemRecordIds.size
        ) {
            throw PlaylistSyncException("Playlist order data is invalid")
        }
        order.itemRecordIds.forEach(::validateUuid)
    }

    private fun validateRemotePlaylist(
        change: PlaylistChange,
        playlist: SyncedRemotePlaylist
    ) {
        if (
            playlist.serviceId < 0 ||
            playlist.url.isBlank() ||
            playlist.url != playlist.url.trim() ||
            playlist.url.length > MAX_PLAYLIST_URL_LENGTH ||
            (playlist.name?.length ?: 0) > MAX_PLAYLIST_NAME_LENGTH ||
            (playlist.thumbnailUrl?.length ?: 0) > MAX_PLAYLIST_URL_LENGTH ||
            (playlist.uploader?.length ?: 0) > MAX_PLAYLIST_UPLOADER_LENGTH ||
            playlist.displayIndex < -1 ||
            (playlist.streamCount ?: 0) < -1 ||
            change.recordId != PlaylistRecordId.remote(
                playlist.serviceId,
                playlist.url
            )
        ) {
            throw PlaylistSyncException("Remote playlist metadata is invalid")
        }
    }

    private fun validateStream(stream: SyncedStream) {
        if (
            stream.serviceId < 0 ||
            stream.url.isBlank() ||
            stream.url != stream.url.trim() ||
            stream.url.length > MAX_PLAYLIST_URL_LENGTH ||
            stream.title.length > MAX_PLAYLIST_TITLE_LENGTH ||
            stream.uploader.length > MAX_PLAYLIST_UPLOADER_LENGTH ||
            (stream.uploaderUrl?.length ?: 0) > MAX_PLAYLIST_URL_LENGTH ||
            (stream.thumbnailUrl?.length ?: 0) > MAX_PLAYLIST_URL_LENGTH ||
            runCatching { StreamType.valueOf(stream.streamType) }.isFailure
        ) {
            throw PlaylistSyncException("Playlist stream metadata is invalid")
        }
    }

    private fun validatePeerId(peerId: String) {
        try {
            PeerId.fromBase58(peerId)
        } catch (error: Exception) {
            throw PlaylistSyncException("A playlist synchronization PeerID is invalid", error)
        }
    }

    private fun validateUuid(value: String) {
        try {
            if (UUID.fromString(value).toString() != value) {
                throw IllegalArgumentException("Noncanonical UUID")
            }
        } catch (error: IllegalArgumentException) {
            throw PlaylistSyncException("A playlist record UUID is invalid", error)
        }
    }

    private const val MAX_SYNC_ERROR_LENGTH = 512
    private const val SHA_256_HEX_LENGTH = 64
}

internal object PlaylistRecordId {
    fun local(): String = UUID.randomUUID().toString()

    fun item(): String = UUID.randomUUID().toString()

    fun order(playlistRecordId: String): String {
        return digest("local-order\u0000$playlistRecordId")
    }

    fun remote(serviceId: Int, url: String): String {
        return digest("remote\u0000$serviceId\u0000${url.trim()}")
    }

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

class PlaylistSyncException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
