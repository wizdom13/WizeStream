/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.learning.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import org.schabi.newpipe.database.stream.model.StreamEntity

@Entity(
    tableName = LearningContentStreamEntity.TABLE_NAME,
    primaryKeys = [LearningContentStreamEntity.SOURCE_ID, LearningContentStreamEntity.STREAM_ID],
    foreignKeys = [
        ForeignKey(
            entity = LearningContentSourceEntity::class,
            parentColumns = [LearningContentSourceEntity.SOURCE_ID],
            childColumns = [LearningContentStreamEntity.SOURCE_ID],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = StreamEntity::class,
            parentColumns = [StreamEntity.STREAM_ID],
            childColumns = [LearningContentStreamEntity.STREAM_ID],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = [LearningContentStreamEntity.STREAM_ID])]
)
data class LearningContentStreamEntity(
    @ColumnInfo(name = SOURCE_ID)
    val sourceId: String,

    @ColumnInfo(name = STREAM_ID)
    val streamId: Long
) {
    companion object {
        const val TABLE_NAME = "learning_content_streams"
        const val SOURCE_ID = "source_id"
        const val STREAM_ID = "stream_id"
    }
}
