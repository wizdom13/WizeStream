package org.schabi.newpipe.database.feed.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.OffsetDateTime
import org.schabi.newpipe.database.feed.model.SavedSearchFeedEntity.Companion.CONTENT_FILTER
import org.schabi.newpipe.database.feed.model.SavedSearchFeedEntity.Companion.QUERY
import org.schabi.newpipe.database.feed.model.SavedSearchFeedEntity.Companion.SAVED_SEARCH_FEED_TABLE
import org.schabi.newpipe.database.feed.model.SavedSearchFeedEntity.Companion.SERVICE_ID
import org.schabi.newpipe.database.feed.model.SavedSearchFeedEntity.Companion.SORT_FILTER
import org.schabi.newpipe.database.feed.model.SavedSearchFeedEntity.Companion.SORT_ORDER

@Entity(
    tableName = SAVED_SEARCH_FEED_TABLE,
    indices = [
        Index(SORT_ORDER),
        Index(
            value = [SERVICE_ID, QUERY, CONTENT_FILTER, SORT_FILTER],
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
    var lastRefresh: OffsetDateTime? = null
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

        private const val FILTER_SEPARATOR = "\u001F"

        @JvmStatic
        fun encodeContentFilters(filters: Array<String>): String =
            filters.filter(String::isNotBlank).joinToString(FILTER_SEPARATOR)

        @JvmStatic
        fun encodeSortFilters(filters: IntArray): String =
            filters.joinToString(FILTER_SEPARATOR)
    }
}
