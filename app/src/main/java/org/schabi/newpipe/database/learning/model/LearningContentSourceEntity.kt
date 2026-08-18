/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.learning.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.schabi.newpipe.database.playlist.model.PlaylistEntity

@Entity(
    tableName = LearningContentSourceEntity.TABLE_NAME,
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = [PlaylistEntity.PLAYLIST_ID],
            childColumns = [LearningContentSourceEntity.LOCAL_PLAYLIST_ID],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = [LearningContentSourceEntity.LOCAL_PLAYLIST_ID]),
        Index(value = [LearningContentSourceEntity.SOURCE_TYPE]),
        Index(value = [LearningContentSourceEntity.SERVICE_ID, LearningContentSourceEntity.URL])
    ]
)
data class LearningContentSourceEntity(
    @PrimaryKey
    @ColumnInfo(name = SOURCE_ID)
    val sourceId: String,

    @ColumnInfo(name = SOURCE_TYPE)
    val sourceType: String,

    @ColumnInfo(name = LOCAL_PLAYLIST_ID)
    val localPlaylistId: Long? = null,

    @ColumnInfo(name = SERVICE_ID)
    val serviceId: Int? = null,

    @ColumnInfo(name = URL)
    val url: String? = null,

    @ColumnInfo(name = TITLE)
    val title: String? = null,

    @ColumnInfo(name = THUMBNAIL_URL)
    val thumbnailUrl: String? = null,

    @ColumnInfo(name = CREATED_AT)
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val TABLE_NAME = "learning_content_sources"
        const val SOURCE_ID = "source_id"
        const val SOURCE_TYPE = "source_type"
        const val LOCAL_PLAYLIST_ID = "local_playlist_id"
        const val SERVICE_ID = "service_id"
        const val URL = "url"
        const val TITLE = "title"
        const val THUMBNAIL_URL = "thumbnail_url"
        const val CREATED_AT = "created_at"

        const val TYPE_STREAM = "STREAM"
        const val TYPE_LOCAL_PLAYLIST = "LOCAL_PLAYLIST"
        const val TYPE_REMOTE_PLAYLIST = "REMOTE_PLAYLIST"
    }
}
