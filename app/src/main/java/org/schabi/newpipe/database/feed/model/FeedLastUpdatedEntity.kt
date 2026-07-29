package org.schabi.newpipe.database.feed.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import java.time.OffsetDateTime
import org.schabi.newpipe.database.feed.model.FeedLastUpdatedEntity.Companion.FEED_LAST_UPDATED_TABLE
import org.schabi.newpipe.database.feed.model.FeedLastUpdatedEntity.Companion.SUBSCRIPTION_ID
import org.schabi.newpipe.database.feed.model.FeedLastUpdatedEntity.Companion.YOUTUBE_MODE_MASK
import org.schabi.newpipe.database.subscription.SubscriptionEntity

@Entity(
    tableName = FEED_LAST_UPDATED_TABLE,
    primaryKeys = [SUBSCRIPTION_ID, YOUTUBE_MODE_MASK],
    foreignKeys = [
        ForeignKey(
            entity = SubscriptionEntity::class,
            parentColumns = [SubscriptionEntity.SUBSCRIPTION_UID],
            childColumns = [SUBSCRIPTION_ID],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
            deferred = true
        )
    ]
)
data class FeedLastUpdatedEntity(
    @ColumnInfo(name = SUBSCRIPTION_ID)
    var subscriptionId: Long,

    @ColumnInfo(name = YOUTUBE_MODE_MASK, defaultValue = "1")
    var youtubeModeMask: Int = SubscriptionEntity.YOUTUBE_MODE_REGULAR,

    @ColumnInfo(name = LAST_UPDATED)
    var lastUpdated: OffsetDateTime? = null
) {
    companion object {
        const val FEED_LAST_UPDATED_TABLE = "feed_last_updated"

        const val SUBSCRIPTION_ID = "subscription_id"
        const val YOUTUBE_MODE_MASK = "youtube_mode_mask"
        const val LAST_UPDATED = "last_updated"
    }
}
