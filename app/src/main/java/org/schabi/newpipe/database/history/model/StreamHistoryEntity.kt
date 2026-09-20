/*
 * SPDX-FileCopyrightText: 2018-2022 NewPipe contributors <https://newpipe.net>
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.history.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.CASCADE
import androidx.room.Index
import java.time.OffsetDateTime
import org.schabi.newpipe.database.history.model.StreamHistoryEntity.Companion.JOIN_STREAM_ID
import org.schabi.newpipe.database.history.model.StreamHistoryEntity.Companion.PROFILE_ID
import org.schabi.newpipe.database.history.model.StreamHistoryEntity.Companion.STREAM_ACCESS_DATE
import org.schabi.newpipe.database.history.model.StreamHistoryEntity.Companion.STREAM_HISTORY_TABLE
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.stream.model.StreamEntity.Companion.STREAM_ID

/**
 * @param streamUid the stream id this history item will refer to
 * @param accessDate the last time the stream was accessed
 * @param repeatCount the total number of views this stream received
 */
@Entity(
    tableName = STREAM_HISTORY_TABLE,
    primaryKeys = [PROFILE_ID, JOIN_STREAM_ID, STREAM_ACCESS_DATE],
    indices = [
        Index(value = [JOIN_STREAM_ID]),
        Index(value = [PROFILE_ID, JOIN_STREAM_ID])
    ],
    foreignKeys = [
        ForeignKey(
            entity = StreamEntity::class,
            parentColumns = arrayOf(STREAM_ID),
            childColumns = arrayOf(JOIN_STREAM_ID),
            onDelete = CASCADE,
            onUpdate = CASCADE
        )
    ]
)
data class StreamHistoryEntity @JvmOverloads constructor(
    @ColumnInfo(name = JOIN_STREAM_ID)
    val streamUid: Long,

    @ColumnInfo(name = STREAM_ACCESS_DATE)
    var accessDate: OffsetDateTime,

    @ColumnInfo(name = STREAM_REPEAT_COUNT)
    var repeatCount: Long,

    @ColumnInfo(
        name = PROFILE_ID,
        defaultValue = "'00000000-0000-0000-0000-000000000000'"
    )
    var profileId: String = DEFAULT_PROFILE_ID
) {
    companion object {
        const val STREAM_HISTORY_TABLE: String = "stream_history"
        const val STREAM_ACCESS_DATE: String = "access_date"
        const val JOIN_STREAM_ID: String = "stream_id"
        const val STREAM_REPEAT_COUNT: String = "repeat_count"
        const val PROFILE_ID: String = "profile_id"
        const val DEFAULT_PROFILE_ID: String = "00000000-0000-0000-0000-000000000000"
    }
}
