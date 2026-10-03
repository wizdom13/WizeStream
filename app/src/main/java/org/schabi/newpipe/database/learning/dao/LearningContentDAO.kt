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
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.learning.LearningContentKey

@Dao
interface LearningContentDAO {
    @Query("SELECT * FROM learning_content_sources WHERE source_id = :sourceId")
    fun source(sourceId: String): LearningContentSourceEntity?

    @Query(
        """
        SELECT src.* FROM learning_content_sources src
        WHERE (src.source_type = 'LOCAL_PLAYLIST' AND EXISTS (
            SELECT 1 FROM playlist_stream_join ps JOIN playlists p ON p.uid = ps.playlist_id
            WHERE ps.playlist_id = src.local_playlist_id AND ps.stream_id = :streamId
              AND p.profile_id = :profileId
        )) OR (src.source_type = 'REMOTE_PLAYLIST' AND EXISTS (
            SELECT 1 FROM learning_content_streams cs
            WHERE cs.source_id = src.source_id AND cs.stream_id = :streamId
        )) ORDER BY src.title, src.source_id
        """
    )
    fun coursesForStream(streamId: Long, profileId: String): List<LearningContentSourceEntity>

    @Query(
        """
        SELECT streams.* FROM streams
        JOIN playlist_stream_join ps ON ps.stream_id = streams.uid
        JOIN playlists p ON p.uid = ps.playlist_id
        WHERE p.uid = :playlistId AND p.profile_id = :profileId
        ORDER BY ps.join_index
        """
    )
    fun courseStreams(playlistId: Long, profileId: String): List<StreamEntity>

    @Query(
        """
        SELECT * FROM streams WHERE uid IN (
            SELECT cs.stream_id FROM learning_content_streams cs
            JOIN learning_content_sources src ON src.source_id = cs.source_id
            WHERE (:sourceId IS NULL OR src.source_id = :sourceId)
            UNION
            SELECT ps.stream_id FROM playlist_stream_join ps
            JOIN playlists p ON p.uid = ps.playlist_id
            JOIN learning_content_sources src ON src.local_playlist_id = p.uid
            WHERE p.profile_id = :profileId
              AND (:sourceId IS NULL OR src.source_id = :sourceId)
        ) ORDER BY title
    """
    )
    fun reviewStreams(sourceId: String?, profileId: String): Flowable<List<StreamEntity>>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM learning_content_sources sources
            INNER JOIN playlists ON playlists.uid = sources.local_playlist_id
            WHERE sources.source_type = 'LOCAL_PLAYLIST'
              AND playlists.profile_id = :profileId
              AND EXISTS (SELECT 1 FROM playlist_stream_join
                          WHERE playlist_id = playlists.uid)
            UNION ALL
            SELECT 1 FROM learning_content_sources sources
            WHERE sources.source_type = 'REMOTE_PLAYLIST'
              AND EXISTS (SELECT 1 FROM learning_content_streams
                          WHERE source_id = sources.source_id)
        )
        """
    )
    fun hasLearningPlaylists(profileId: String): Boolean

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

    @Query(
        """
        UPDATE learning_sessions
        SET is_designated = 1
        WHERE profile_id = :profileId AND stream_id IN (:streamIds)
        """
    )
    fun markSessionsDesignated(profileId: String, streamIds: List<Long>)

    @Query(
        """
        UPDATE learning_sessions
        SET is_designated = 1
        WHERE profile_id = :profileId
        AND stream_id IN (
            SELECT stream_id FROM playlist_stream_join WHERE playlist_id = :playlistId
        )
        """
    )
    fun markLocalPlaylistSessionsDesignated(profileId: String, playlistId: Long)

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
