/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.sync

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = PlaylistSyncChangeEntity.TABLE_NAME,
    primaryKeys = [
        PlaylistSyncChangeEntity.ORIGIN_PEER_ID,
        PlaylistSyncChangeEntity.ORIGIN_REVISION
    ],
    indices = [
        Index(value = [PlaylistSyncChangeEntity.RECORD_ID]),
        Index(value = [PlaylistSyncChangeEntity.PARENT_RECORD_ID]),
        Index(
            value = [
                PlaylistSyncChangeEntity.LAMPORT_VERSION,
                PlaylistSyncChangeEntity.ORIGIN_PEER_ID,
                PlaylistSyncChangeEntity.ORIGIN_REVISION
            ]
        )
    ]
)
data class PlaylistSyncChangeEntity(
    @ColumnInfo(name = ORIGIN_PEER_ID)
    val originPeerId: String,

    @ColumnInfo(name = ORIGIN_REVISION)
    val originRevision: Long,

    @ColumnInfo(name = LAMPORT_VERSION)
    val lamportVersion: Long,

    @ColumnInfo(name = RECORD_ID)
    val recordId: String,

    @ColumnInfo(name = RECORD_TYPE)
    val recordType: String,

    @ColumnInfo(name = PARENT_RECORD_ID)
    val parentRecordId: String?,

    @ColumnInfo(name = CHANGE_TYPE)
    val changeType: String,

    @ColumnInfo(name = RECORD_JSON)
    val recordJson: String?
) {
    companion object {
        const val TABLE_NAME = "playlist_sync_changes"
        const val ORIGIN_PEER_ID = "origin_peer_id"
        const val ORIGIN_REVISION = "origin_revision"
        const val LAMPORT_VERSION = "lamport_version"
        const val RECORD_ID = "record_id"
        const val RECORD_TYPE = "record_type"
        const val PARENT_RECORD_ID = "parent_record_id"
        const val CHANGE_TYPE = "change_type"
        const val RECORD_JSON = "record_json"
    }
}

@Entity(
    tableName = PlaylistSyncRecordEntity.TABLE_NAME,
    primaryKeys = [PlaylistSyncRecordEntity.RECORD_ID],
    indices = [
        Index(value = [PlaylistSyncRecordEntity.PARENT_RECORD_ID]),
        Index(value = [PlaylistSyncRecordEntity.RECORD_TYPE])
    ]
)
data class PlaylistSyncRecordEntity(
    @ColumnInfo(name = RECORD_ID)
    val recordId: String,

    @ColumnInfo(name = RECORD_TYPE)
    val recordType: String,

    @ColumnInfo(name = PARENT_RECORD_ID)
    val parentRecordId: String?,

    @ColumnInfo(name = LAMPORT_VERSION)
    val lamportVersion: Long,

    @ColumnInfo(name = ORIGIN_PEER_ID)
    val originPeerId: String,

    @ColumnInfo(name = ORIGIN_REVISION)
    val originRevision: Long,

    @ColumnInfo(name = IS_DELETED)
    val isDeleted: Boolean,

    @ColumnInfo(name = RECORD_JSON)
    val recordJson: String?
) {
    companion object {
        const val TABLE_NAME = "playlist_sync_records"
        const val RECORD_ID = "record_id"
        const val RECORD_TYPE = "record_type"
        const val PARENT_RECORD_ID = "parent_record_id"
        const val LAMPORT_VERSION = "lamport_version"
        const val ORIGIN_PEER_ID = "origin_peer_id"
        const val ORIGIN_REVISION = "origin_revision"
        const val IS_DELETED = "is_deleted"
        const val RECORD_JSON = "record_json"
    }
}

@Entity(
    tableName = PlaylistSyncOriginStateEntity.TABLE_NAME,
    primaryKeys = [PlaylistSyncOriginStateEntity.ORIGIN_PEER_ID]
)
data class PlaylistSyncOriginStateEntity(
    @ColumnInfo(name = ORIGIN_PEER_ID)
    val originPeerId: String,

    @ColumnInfo(name = CONTIGUOUS_REVISION)
    val contiguousRevision: Long
) {
    companion object {
        const val TABLE_NAME = "playlist_sync_origin_state"
        const val ORIGIN_PEER_ID = "origin_peer_id"
        const val CONTIGUOUS_REVISION = "contiguous_revision"
    }
}

@Entity(
    tableName = PlaylistSyncPeerStateEntity.TABLE_NAME,
    primaryKeys = [
        PlaylistSyncPeerStateEntity.PEER_ID,
        PlaylistSyncPeerStateEntity.ORIGIN_PEER_ID
    ],
    indices = [Index(value = [PlaylistSyncPeerStateEntity.ORIGIN_PEER_ID])]
)
data class PlaylistSyncPeerStateEntity(
    @ColumnInfo(name = PEER_ID)
    val peerId: String,

    @ColumnInfo(name = ORIGIN_PEER_ID)
    val originPeerId: String,

    @ColumnInfo(name = ACKNOWLEDGED_REVISION)
    val acknowledgedRevision: Long
) {
    companion object {
        const val TABLE_NAME = "playlist_sync_peer_state"
        const val PEER_ID = "peer_id"
        const val ORIGIN_PEER_ID = "origin_peer_id"
        const val ACKNOWLEDGED_REVISION = "acknowledged_revision"
    }
}

@Entity(
    tableName = PlaylistSyncLocalMapEntity.TABLE_NAME,
    primaryKeys = [PlaylistSyncLocalMapEntity.PLAYLIST_RECORD_ID],
    indices = [Index(value = [PlaylistSyncLocalMapEntity.PLAYLIST_UID], unique = true)]
)
data class PlaylistSyncLocalMapEntity(
    @ColumnInfo(name = PLAYLIST_RECORD_ID)
    val playlistRecordId: String,

    @ColumnInfo(name = PLAYLIST_UID)
    val playlistUid: Long
) {
    companion object {
        const val TABLE_NAME = "playlist_sync_local_map"
        const val PLAYLIST_RECORD_ID = "playlist_record_id"
        const val PLAYLIST_UID = "playlist_uid"
    }
}
