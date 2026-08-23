/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.sync

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = SubscriptionSyncChangeEntity.TABLE_NAME,
    primaryKeys = [
        SubscriptionSyncChangeEntity.ORIGIN_PEER_ID,
        SubscriptionSyncChangeEntity.ORIGIN_REVISION
    ],
    indices = [
        Index(value = [SubscriptionSyncChangeEntity.RECORD_ID]),
        Index(
            value = [
                SubscriptionSyncChangeEntity.LAMPORT_VERSION,
                SubscriptionSyncChangeEntity.ORIGIN_PEER_ID,
                SubscriptionSyncChangeEntity.ORIGIN_REVISION
            ]
        )
    ]
)
data class SubscriptionSyncChangeEntity(
    @ColumnInfo(name = ORIGIN_PEER_ID)
    val originPeerId: String,

    @ColumnInfo(name = ORIGIN_REVISION)
    val originRevision: Long,

    @ColumnInfo(name = LAMPORT_VERSION)
    val lamportVersion: Long,

    @ColumnInfo(name = RECORD_ID)
    val recordId: String,

    @ColumnInfo(name = CHANGE_TYPE)
    val changeType: String,

    @ColumnInfo(name = SERVICE_ID)
    val serviceId: Int,

    @ColumnInfo(name = URL)
    val url: String,

    @ColumnInfo(name = NAME)
    val name: String?,

    @ColumnInfo(name = AVATAR_URL)
    val avatarUrl: String?,

    @ColumnInfo(name = SUBSCRIBER_COUNT)
    val subscriberCount: Long?,

    @ColumnInfo(name = DESCRIPTION)
    val description: String?,

    @ColumnInfo(name = YOUTUBE_MODE_MASK)
    val youtubeModeMask: Int?,

    @ColumnInfo(name = NOTIFICATION_MODE)
    val notificationMode: Int?,

    @ColumnInfo(name = NOTIFICATION_KEYWORDS)
    val notificationKeywords: String?
) {
    companion object {
        const val TABLE_NAME = "subscription_sync_changes"
        const val ORIGIN_PEER_ID = "origin_peer_id"
        const val ORIGIN_REVISION = "origin_revision"
        const val LAMPORT_VERSION = "lamport_version"
        const val RECORD_ID = "record_id"
        const val CHANGE_TYPE = "change_type"
        const val SERVICE_ID = "service_id"
        const val URL = "url"
        const val NAME = "name"
        const val AVATAR_URL = "avatar_url"
        const val SUBSCRIBER_COUNT = "subscriber_count"
        const val DESCRIPTION = "description"
        const val YOUTUBE_MODE_MASK = "youtube_mode_mask"
        const val NOTIFICATION_MODE = "notification_mode"
        const val NOTIFICATION_KEYWORDS = "notification_keywords"
    }
}

@Entity(
    tableName = SubscriptionSyncRecordEntity.TABLE_NAME,
    primaryKeys = [SubscriptionSyncRecordEntity.RECORD_ID],
    indices = [
        Index(
            value = [
                SubscriptionSyncRecordEntity.SERVICE_ID,
                SubscriptionSyncRecordEntity.URL
            ],
            unique = true
        )
    ]
)
data class SubscriptionSyncRecordEntity(
    @ColumnInfo(name = RECORD_ID)
    val recordId: String,

    @ColumnInfo(name = SERVICE_ID)
    val serviceId: Int,

    @ColumnInfo(name = URL)
    val url: String,

    @ColumnInfo(name = LAMPORT_VERSION)
    val lamportVersion: Long,

    @ColumnInfo(name = ORIGIN_PEER_ID)
    val originPeerId: String,

    @ColumnInfo(name = ORIGIN_REVISION)
    val originRevision: Long,

    @ColumnInfo(name = IS_DELETED)
    val isDeleted: Boolean,

    @ColumnInfo(name = YOUTUBE_MODE_MASK, defaultValue = "1")
    val youtubeModeMask: Int,

    @ColumnInfo(name = NOTIFICATION_MODE)
    val notificationMode: Int?,

    @ColumnInfo(name = NOTIFICATION_KEYWORDS)
    val notificationKeywords: String?
) {
    companion object {
        const val TABLE_NAME = "subscription_sync_records"
        const val RECORD_ID = "record_id"
        const val SERVICE_ID = "service_id"
        const val URL = "url"
        const val LAMPORT_VERSION = "lamport_version"
        const val ORIGIN_PEER_ID = "origin_peer_id"
        const val ORIGIN_REVISION = "origin_revision"
        const val IS_DELETED = "is_deleted"
        const val YOUTUBE_MODE_MASK = "youtube_mode_mask"
        const val NOTIFICATION_MODE = "notification_mode"
        const val NOTIFICATION_KEYWORDS = "notification_keywords"
    }
}

@Entity(
    tableName = SubscriptionSyncOriginStateEntity.TABLE_NAME,
    primaryKeys = [SubscriptionSyncOriginStateEntity.ORIGIN_PEER_ID]
)
data class SubscriptionSyncOriginStateEntity(
    @ColumnInfo(name = ORIGIN_PEER_ID)
    val originPeerId: String,

    @ColumnInfo(name = CONTIGUOUS_REVISION)
    val contiguousRevision: Long
) {
    companion object {
        const val TABLE_NAME = "subscription_sync_origin_state"
        const val ORIGIN_PEER_ID = "origin_peer_id"
        const val CONTIGUOUS_REVISION = "contiguous_revision"
    }
}

@Entity(
    tableName = SubscriptionSyncPeerStateEntity.TABLE_NAME,
    primaryKeys = [
        SubscriptionSyncPeerStateEntity.PEER_ID,
        SubscriptionSyncPeerStateEntity.ORIGIN_PEER_ID
    ],
    indices = [Index(value = [SubscriptionSyncPeerStateEntity.ORIGIN_PEER_ID])]
)
data class SubscriptionSyncPeerStateEntity(
    @ColumnInfo(name = PEER_ID)
    val peerId: String,

    @ColumnInfo(name = ORIGIN_PEER_ID)
    val originPeerId: String,

    @ColumnInfo(name = ACKNOWLEDGED_REVISION)
    val acknowledgedRevision: Long
) {
    companion object {
        const val TABLE_NAME = "subscription_sync_peer_state"
        const val PEER_ID = "peer_id"
        const val ORIGIN_PEER_ID = "origin_peer_id"
        const val ACKNOWLEDGED_REVISION = "acknowledged_revision"
    }
}
