package org.schabi.newpipe.database.feed.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.OffsetDateTime
import org.schabi.newpipe.database.feed.model.SavedSearchFeedEntity.Companion.CONTENT_FILTER
import org.schabi.newpipe.database.feed.model.SavedSearchFeedEntity.Companion.PROFILE_ID
import org.schabi.newpipe.database.feed.model.SavedSearchFeedEntity.Companion.QUERY
import org.schabi.newpipe.database.feed.model.SavedSearchFeedEntity.Companion.SAVED_SEARCH_FEED_TABLE
import org.schabi.newpipe.database.feed.model.SavedSearchFeedEntity.Companion.SERVICE_ID
import org.schabi.newpipe.database.feed.model.SavedSearchFeedEntity.Companion.SORT_FILTER
import org.schabi.newpipe.database.feed.model.SavedSearchFeedEntity.Companion.SORT_ORDER

@Entity(
    tableName = SAVED_SEARCH_FEED_TABLE,
    indices = [
        Index(value = [PROFILE_ID, SORT_ORDER]),
        Index(
            value = [PROFILE_ID, SERVICE_ID, QUERY, CONTENT_FILTER, SORT_FILTER],
            unique = true
        )
    ]
)
data class SavedSearchFeedEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = ID)
    val uid: Long = 0,

    @ColumnInfo(name = NAME)
    var name: String,

    @ColumnInfo(name = SERVICE_ID)
    val serviceId: Int,

    @ColumnInfo(name = QUERY)
    val query: String,

    @ColumnInfo(name = CONTENT_FILTER)
    val contentFilter: String = "",

    @ColumnInfo(name = SORT_FILTER)
    val sortFilter: String = "",

    @ColumnInfo(name = SORT_ORDER)
    var sortOrder: Long = -1,

    @ColumnInfo(name = LAST_REFRESH)
    var lastRefresh: OffsetDateTime? = null,

    @ColumnInfo(
        name = PROFILE_ID,
        defaultValue = "'00000000-0000-0000-0000-000000000000'"
    )
    var profileId: String = DEFAULT_PROFILE_ID
) {
    fun contentFilters(): Array<String> = if (contentFilter.isBlank()) {
        emptyArray()
    } else {
        contentFilter.split(FILTER_SEPARATOR).toTypedArray()
    }

    fun sortFilters(): IntArray = if (sortFilter.isBlank()) {
        intArrayOf()
    } else {
        sortFilter.split(FILTER_SEPARATOR).mapNotNull(String::toIntOrNull).toIntArray()
    }

    companion object {
        const val SAVED_SEARCH_FEED_TABLE = "saved_search_feed"
        const val ID = "uid"
        const val NAME = "name"
        const val SERVICE_ID = "service_id"
        const val QUERY = "query"
        const val CONTENT_FILTER = "content_filter"
        const val SORT_FILTER = "sort_filter"
        const val SORT_ORDER = "sort_order"
        const val LAST_REFRESH = "last_refresh"
        const val PROFILE_ID = "profile_id"
        const val DEFAULT_PROFILE_ID = "00000000-0000-0000-0000-000000000000"

        private const val FILTER_SEPARATOR = "\u001F"

        @JvmStatic
        fun encodeContentFilters(filters: Array<String>): String = filters
            .filter(String::isNotBlank)
            .joinToString(FILTER_SEPARATOR)

        @JvmStatic
        fun encodeSortFilters(filters: IntArray): String = filters.joinToString(
            FILTER_SEPARATOR
        )
    }
}
