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
import org.schabi.newpipe.database.stream.model.StreamEntity

@Entity(
    tableName = LearningSessionEntity.TABLE_NAME,
    foreignKeys = [
        ForeignKey(
            entity = StreamEntity::class,
            parentColumns = [StreamEntity.STREAM_ID],
            childColumns = [LearningSessionEntity.STREAM_ID],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = [LearningSessionEntity.STREAM_ID, LearningSessionEntity.STARTED_AT]),
        Index(value = [LearningSessionEntity.LOCAL_DATE])
    ]
)
data class LearningSessionEntity(
    @PrimaryKey
    @ColumnInfo(name = SESSION_ID)
    val sessionId: String,

    @ColumnInfo(name = STREAM_ID)
    val streamId: Long,

    @ColumnInfo(name = STARTED_AT)
    val startedAtEpochMillis: Long,

    @ColumnInfo(name = ENDED_AT)
    val endedAtEpochMillis: Long,

    @ColumnInfo(name = WATCHED_DURATION_MS)
    val watchedDurationMillis: Long,

    @ColumnInfo(name = LOCAL_DATE)
    val localDate: String,

    @ColumnInfo(name = BACKGROUND_PLAYBACK)
    val backgroundPlayback: Boolean
) {
    companion object {
        const val TABLE_NAME = "learning_sessions"
        const val SESSION_ID = "session_id"
        const val STREAM_ID = "stream_id"
        const val STARTED_AT = "started_at"
        const val ENDED_AT = "ended_at"
        const val WATCHED_DURATION_MS = "watched_duration_ms"
        const val LOCAL_DATE = "local_date"
        const val BACKGROUND_PLAYBACK = "background_playback"
    }
}
