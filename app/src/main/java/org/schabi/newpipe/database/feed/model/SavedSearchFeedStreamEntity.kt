package org.schabi.newpipe.database.feed.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import org.schabi.newpipe.database.feed.model.SavedSearchFeedEntity.Companion.SAVED_SEARCH_FEED_TABLE
import org.schabi.newpipe.database.feed.model.SavedSearchFeedStreamEntity.Companion.FEED_ID
import org.schabi.newpipe.database.feed.model.SavedSearchFeedStreamEntity.Companion.POSITION
import org.schabi.newpipe.database.feed.model.SavedSearchFeedStreamEntity.Companion.SAVED_SEARCH_FEED_STREAM_TABLE
import org.schabi.newpipe.database.feed.model.SavedSearchFeedStreamEntity.Companion.STREAM_ID
import org.schabi.newpipe.database.stream.model.StreamEntity

@Entity(
    tableName = SAVED_SEARCH_FEED_STREAM_TABLE,
    primaryKeys = [FEED_ID, STREAM_ID],
    indices = [
        Index(STREAM_ID),
        Index(value = [FEED_ID, POSITION], unique = true)
    ],
    foreignKeys = [
        ForeignKey(
            entity = SavedSearchFeedEntity::class,
            parentColumns = [SavedSearchFeedEntity.ID],
            childColumns = [FEED_ID],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = StreamEntity::class,
            parentColumns = [StreamEntity.STREAM_ID],
            childColumns = [STREAM_ID],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ]
)
data class SavedSearchFeedStreamEntity(
    @ColumnInfo(name = FEED_ID)
    val feedId: Long,

    @ColumnInfo(name = STREAM_ID)
    val streamId: Long,

    @ColumnInfo(name = POSITION)
    val position: Long
) {
    companion object {
        const val SAVED_SEARCH_FEED_STREAM_TABLE = "saved_search_feed_stream"
        const val FEED_ID = "feed_id"
        const val STREAM_ID = "stream_id"
        const val POSITION = "position"
    }
}
