/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.sync

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = StructuredPreferenceSyncChangeEntity.TABLE_NAME,
    primaryKeys = [
        StructuredPreferenceSyncChangeEntity.CATEGORY,
        StructuredPreferenceSyncChangeEntity.ORIGIN_PEER_ID,
        StructuredPreferenceSyncChangeEntity.ORIGIN_REVISION
    ],
    indices = [
        Index(
            value = [
                StructuredPreferenceSyncChangeEntity.CATEGORY,
                StructuredPreferenceSyncChangeEntity.RECORD_ID
            ]
        ),
        Index(
            value = [
                StructuredPreferenceSyncChangeEntity.CATEGORY,
                StructuredPreferenceSyncChangeEntity.PARENT_RECORD_ID
            ]
        ),
        Index(
            value = [
                StructuredPreferenceSyncChangeEntity.CATEGORY,
                StructuredPreferenceSyncChangeEntity.LAMPORT_VERSION,
                StructuredPreferenceSyncChangeEntity.ORIGIN_PEER_ID,
                StructuredPreferenceSyncChangeEntity.ORIGIN_REVISION
            ]
        )
    ]
)
data class StructuredPreferenceSyncChangeEntity(
    @ColumnInfo(name = CATEGORY)
    val category: String,

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
    val recordJson: String
) {
    companion object {
        const val TABLE_NAME = "structured_preference_sync_changes"
        const val CATEGORY = "category"
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
    tableName = StructuredPreferenceSyncRecordEntity.TABLE_NAME,
    primaryKeys = [
        StructuredPreferenceSyncRecordEntity.CATEGORY,
        StructuredPreferenceSyncRecordEntity.RECORD_ID
    ],
    indices = [
        Index(
            value = [
                StructuredPreferenceSyncRecordEntity.CATEGORY,
                StructuredPreferenceSyncRecordEntity.RECORD_TYPE
            ]
        ),
        Index(
            value = [
                StructuredPreferenceSyncRecordEntity.CATEGORY,
                StructuredPreferenceSyncRecordEntity.PARENT_RECORD_ID
            ]
        )
    ]
)
data class StructuredPreferenceSyncRecordEntity(
    @ColumnInfo(name = CATEGORY)
    val category: String,

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
    val recordJson: String
) {
    companion object {
        const val TABLE_NAME = "structured_preference_sync_records"
        const val CATEGORY = "category"
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
    tableName = StructuredPreferenceSyncOriginStateEntity.TABLE_NAME,
    primaryKeys = [
        StructuredPreferenceSyncOriginStateEntity.CATEGORY,
        StructuredPreferenceSyncOriginStateEntity.ORIGIN_PEER_ID
    ]
)
data class StructuredPreferenceSyncOriginStateEntity(
    @ColumnInfo(name = CATEGORY)
    val category: String,

    @ColumnInfo(name = ORIGIN_PEER_ID)
    val originPeerId: String,

    @ColumnInfo(name = CONTIGUOUS_REVISION)
    val contiguousRevision: Long
) {
    companion object {
        const val TABLE_NAME = "structured_preference_sync_origin_state"
        const val CATEGORY = "category"
        const val ORIGIN_PEER_ID = "origin_peer_id"
        const val CONTIGUOUS_REVISION = "contiguous_revision"
    }
}

@Entity(
    tableName = StructuredPreferenceSyncPeerStateEntity.TABLE_NAME,
    primaryKeys = [
        StructuredPreferenceSyncPeerStateEntity.CATEGORY,
        StructuredPreferenceSyncPeerStateEntity.PEER_ID,
        StructuredPreferenceSyncPeerStateEntity.ORIGIN_PEER_ID
    ],
    indices = [
        Index(
            value = [
                StructuredPreferenceSyncPeerStateEntity.CATEGORY,
                StructuredPreferenceSyncPeerStateEntity.ORIGIN_PEER_ID
            ]
        )
    ]
)
data class StructuredPreferenceSyncPeerStateEntity(
    @ColumnInfo(name = CATEGORY)
    val category: String,

    @ColumnInfo(name = PEER_ID)
    val peerId: String,

    @ColumnInfo(name = ORIGIN_PEER_ID)
    val originPeerId: String,

    @ColumnInfo(name = ACKNOWLEDGED_REVISION)
    val acknowledgedRevision: Long
) {
    companion object {
        const val TABLE_NAME = "structured_preference_sync_peer_state"
        const val CATEGORY = "category"
        const val PEER_ID = "peer_id"
        const val ORIGIN_PEER_ID = "origin_peer_id"
        const val ACKNOWLEDGED_REVISION = "acknowledged_revision"
    }
}

@Entity(
    tableName = StructuredPreferenceSyncFeedGroupMapEntity.TABLE_NAME,
    primaryKeys = [StructuredPreferenceSyncFeedGroupMapEntity.GROUP_RECORD_ID],
    indices = [
        Index(
            value = [StructuredPreferenceSyncFeedGroupMapEntity.GROUP_UID],
            unique = true
        )
    ]
)
data class StructuredPreferenceSyncFeedGroupMapEntity(
    @ColumnInfo(name = GROUP_RECORD_ID)
    val groupRecordId: String,

    @ColumnInfo(name = GROUP_UID)
    val groupUid: Long
) {
    companion object {
        const val TABLE_NAME = "structured_preference_sync_feed_group_map"
        const val GROUP_RECORD_ID = "group_record_id"
        const val GROUP_UID = "group_uid"
    }
}

@Entity(
    tableName = StructuredPreferenceSyncLocalStateEntity.TABLE_NAME,
    primaryKeys = [StructuredPreferenceSyncLocalStateEntity.CATEGORY]
)
data class StructuredPreferenceSyncLocalStateEntity(
    @ColumnInfo(name = CATEGORY)
    val category: String,

    @ColumnInfo(name = SNAPSHOT_HASH)
    val snapshotHash: String
) {
    companion object {
        const val TABLE_NAME = "structured_preference_sync_local_state"
        const val CATEGORY = "category"
        const val SNAPSHOT_HASH = "snapshot_hash"
    }
}
