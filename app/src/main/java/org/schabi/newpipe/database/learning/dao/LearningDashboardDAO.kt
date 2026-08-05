/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.learning.dao

import androidx.room.Dao
import androidx.room.Query
import io.reactivex.rxjava3.core.Flowable
import org.schabi.newpipe.learning.LearningDailyActivity
import org.schabi.newpipe.learning.LearningDashboardStream
import org.schabi.newpipe.learning.LearningPlaylistSummary

@Dao
interface LearningDashboardDAO {
    @Query(
        """
        SELECT playlists.uid AS playlist_id,
               playlists.name AS playlist_name,
               (SELECT thumbnail_url FROM streams
                WHERE streams.uid = playlists.thumbnail_stream_id) AS thumbnail_url,
               COALESCE(SUM(CASE WHEN streams.duration > 0 THEN 1 ELSE 0 END), 0)
                   AS eligible_count,
               COALESCE(SUM(CASE
                   WHEN streams.duration > 0
                    AND stream_state.progress_time >= streams.duration * 1000 - 60000
                    AND stream_state.progress_time >= streams.duration * 1000 * 3 / 4
                   THEN 1 ELSE 0 END), 0) AS completed_count
        FROM playlists
        LEFT JOIN playlist_stream_join
          ON playlists.uid = playlist_stream_join.playlist_id
        LEFT JOIN streams ON playlist_stream_join.stream_id = streams.uid
        LEFT JOIN stream_state ON streams.uid = stream_state.stream_id
        GROUP BY playlists.uid
        ORDER BY playlists.display_index, playlists.name
        """
    )
    fun observePlaylistSummaries(): Flowable<List<LearningPlaylistSummary>>

    @Query(
        """
        SELECT streams.*, stream_state.progress_time AS progress_millis,
               0 AS note_count, 0 AS latest_note_update
        FROM streams
        INNER JOIN stream_state ON streams.uid = stream_state.stream_id
        INNER JOIN playlist_stream_join ON streams.uid = playlist_stream_join.stream_id
        LEFT JOIN (
            SELECT stream_id, MAX(access_date) AS latest_access
            FROM stream_history GROUP BY stream_id
        ) recent_history ON streams.uid = recent_history.stream_id
        WHERE streams.duration > 0
          AND stream_state.progress_time > 5000
          AND NOT (
              stream_state.progress_time >= streams.duration * 1000 - 60000
              AND stream_state.progress_time >= streams.duration * 1000 * 3 / 4
          )
        GROUP BY streams.uid
        ORDER BY recent_history.latest_access DESC, streams.uid DESC
        LIMIT :limit
        """
    )
    fun observeContinueLearning(limit: Int): Flowable<List<LearningDashboardStream>>

    @Query(
        """
        SELECT streams.*, COALESCE(stream_state.progress_time, 0) AS progress_millis,
               COUNT(learning_notes.note_id) AS note_count,
               MAX(learning_notes.updated_at) AS latest_note_update
        FROM learning_notes
        INNER JOIN streams ON learning_notes.stream_id = streams.uid
        LEFT JOIN stream_state ON streams.uid = stream_state.stream_id
        GROUP BY streams.uid
        ORDER BY latest_note_update DESC, streams.uid DESC
        LIMIT :limit
        """
    )
    fun observeRecentlyAnnotated(limit: Int): Flowable<List<LearningDashboardStream>>

    @Query(
        """
        SELECT local_date, SUM(watched_duration_ms) AS watched_duration_ms
        FROM learning_sessions
        GROUP BY local_date
        ORDER BY local_date
        """
    )
    fun observeDailyStudyActivity(): Flowable<List<LearningDailyActivity>>
}
