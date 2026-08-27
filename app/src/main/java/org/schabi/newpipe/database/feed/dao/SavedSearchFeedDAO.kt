package org.schabi.newpipe.database.feed.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import java.time.OffsetDateTime
import org.schabi.newpipe.database.feed.model.SavedSearchFeedEntity
import org.schabi.newpipe.database.feed.model.SavedSearchFeedStreamEntity
import org.schabi.newpipe.database.stream.model.StreamEntity

@Dao
abstract class SavedSearchFeedDAO {
    @Query("SELECT * FROM saved_search_feed ORDER BY sort_order ASC, name COLLATE NOCASE ASC")
    abstract fun getAllDirect(): List<SavedSearchFeedEntity>

    @Query("SELECT * FROM saved_search_feed WHERE uid = :feedId")
    abstract fun getDirect(feedId: Long): SavedSearchFeedEntity?

    @Transaction
    open fun insert(entity: SavedSearchFeedEntity): Long {
        entity.sortOrder = nextSortOrder()
        return insertInternal(entity)
    }

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract fun insertInternal(entity: SavedSearchFeedEntity): Long

    @Update(onConflict = OnConflictStrategy.ABORT)
    abstract fun update(entity: SavedSearchFeedEntity): Int

    @Query("DELETE FROM saved_search_feed WHERE uid = :feedId")
    abstract fun delete(feedId: Long): Int

    @Query("SELECT COALESCE(MAX(sort_order) + 1, 0) FROM saved_search_feed")
    protected abstract fun nextSortOrder(): Long

    @Query(
        """
        SELECT streams.*
        FROM streams
        INNER JOIN saved_search_feed_stream cache
            ON streams.uid = cache.stream_id
        WHERE cache.feed_id = :feedId
        ORDER BY cache.position ASC
        LIMIT :limit
        """
    )
    abstract fun getCachedStreamsDirect(feedId: Long, limit: Int): List<StreamEntity>

    @Query("DELETE FROM saved_search_feed_stream WHERE feed_id = :feedId")
    abstract fun clearCachedStreams(feedId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun insertCachedStreams(entities: List<SavedSearchFeedStreamEntity>): List<Long>

    @Query(
        "SELECT COALESCE(MAX(position) + 1, 0) " +
            "FROM saved_search_feed_stream WHERE feed_id = :feedId"
    )
    abstract fun nextCachePosition(feedId: Long): Long

    @Query(
        """
        DELETE FROM saved_search_feed_stream
        WHERE feed_id = :feedId
          AND position NOT IN (
              SELECT position
              FROM saved_search_feed_stream
              WHERE feed_id = :feedId
              ORDER BY position ASC
              LIMIT :maximumItems
          )
        """
    )
    abstract fun pruneCache(feedId: Long, maximumItems: Int): Int

    @Query("UPDATE saved_search_feed SET last_refresh = :refreshedAt WHERE uid = :feedId")
    abstract fun setLastRefresh(feedId: Long, refreshedAt: OffsetDateTime): Int
}
