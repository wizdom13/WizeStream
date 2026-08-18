/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.learning.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.reactivex.rxjava3.core.Flowable
import org.schabi.newpipe.database.learning.model.LearningContentSourceEntity
import org.schabi.newpipe.database.learning.model.LearningContentStreamEntity
import org.schabi.newpipe.learning.LearningContentKey

@Dao
interface LearningContentDAO {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun upsertSource(source: LearningContentSourceEntity)

    @Query(
        "UPDATE learning_content_sources SET title = :title, thumbnail_url = :thumbnailUrl " +
            "WHERE source_id = :sourceId"
    )
    fun updateSourceMetadata(sourceId: String, title: String?, thumbnailUrl: String?)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertSourceStreams(streams: List<LearningContentStreamEntity>)

    @Query("DELETE FROM learning_content_sources WHERE source_id = :sourceId")
    fun deleteSource(sourceId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM learning_content_sources WHERE source_id = :sourceId)")
    fun isSourceMarked(sourceId: String): Boolean

    @Query("SELECT source_id FROM learning_content_sources")
    fun observeSourceIds(): Flowable<List<String>>

    @Query("SELECT source_id FROM learning_content_sources")
    fun getSourceIdsDirect(): List<String>

    @Query("UPDATE learning_sessions SET is_designated = 1 WHERE stream_id IN (:streamIds)")
    fun markSessionsDesignated(streamIds: List<Long>)

    @Query(
        """
        UPDATE learning_sessions SET is_designated = 1
        WHERE stream_id IN (
            SELECT stream_id FROM playlist_stream_join WHERE playlist_id = :playlistId
        )
        """
    )
    fun markLocalPlaylistSessionsDesignated(playlistId: Long)

    @Query(
        """
        SELECT DISTINCT streams.service_id, streams.url
        FROM streams
        INNER JOIN learning_content_streams
          ON streams.uid = learning_content_streams.stream_id
        UNION
        SELECT DISTINCT streams.service_id, streams.url
        FROM learning_content_sources
        INNER JOIN playlist_stream_join
          ON learning_content_sources.local_playlist_id = playlist_stream_join.playlist_id
        INNER JOIN streams ON playlist_stream_join.stream_id = streams.uid
        WHERE learning_content_sources.source_type = 'LOCAL_PLAYLIST'
        """
    )
    fun observeEligibleStreamKeys(): Flowable<List<LearningContentKey>>

    @Query(
        """
        SELECT DISTINCT streams.service_id, streams.url
        FROM streams
        INNER JOIN learning_content_streams
          ON streams.uid = learning_content_streams.stream_id
        UNION
        SELECT DISTINCT streams.service_id, streams.url
        FROM learning_content_sources
        INNER JOIN playlist_stream_join
          ON learning_content_sources.local_playlist_id = playlist_stream_join.playlist_id
        INNER JOIN streams ON playlist_stream_join.stream_id = streams.uid
        WHERE learning_content_sources.source_type = 'LOCAL_PLAYLIST'
        """
    )
    fun getEligibleStreamKeysDirect(): List<LearningContentKey>
}
