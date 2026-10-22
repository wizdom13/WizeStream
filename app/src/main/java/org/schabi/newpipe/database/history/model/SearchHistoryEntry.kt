/*
 * SPDX-FileCopyrightText: 2022 NewPipe contributors <https://newpipe.net>
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.history.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.OffsetDateTime

@Entity(
    tableName = SearchHistoryEntry.TABLE_NAME,
    indices = [Index(value = [SearchHistoryEntry.PROFILE_ID, SearchHistoryEntry.SEARCH])]
)
data class SearchHistoryEntry @JvmOverloads constructor(
    @ColumnInfo(name = CREATION_DATE)
    var creationDate: OffsetDateTime?,

    @ColumnInfo(name = SERVICE_ID)
    val serviceId: Int,

    @ColumnInfo(name = SEARCH)
    val search: String?,

    @ColumnInfo(name = ID)
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = PROFILE_ID, defaultValue = "'00000000-0000-0000-0000-000000000000'")
    val profileId: String = DEFAULT_PROFILE_ID
) {

    @Ignore
    fun hasEqualValues(otherEntry: SearchHistoryEntry): Boolean {
        return profileId == otherEntry.profileId && serviceId == otherEntry.serviceId && search == otherEntry.search
    }

    companion object {
        const val PROFILE_ID = "profile_id"
        const val DEFAULT_PROFILE_ID = "00000000-0000-0000-0000-000000000000"
        const val ID = "id"
        const val TABLE_NAME = "search_history"
        const val SERVICE_ID = "service_id"
        const val CREATION_DATE = "creation_date"
        const val SEARCH = "search"
    }
}
