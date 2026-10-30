/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.playlist.smart

import androidx.room.Dao
import androidx.room.Query
import io.reactivex.rxjava3.core.Flowable
import org.schabi.newpipe.database.stream.StreamWithState

@Dao
interface SmartPlaylistDAO {
    @Query(
        """
        SELECT s.*, st.progress_time FROM streams s
        LEFT JOIN stream_state st ON st.stream_id = s.uid AND st.profile_id = :profile
        WHERE (
            EXISTS (SELECT 1 FROM feed f JOIN subscriptions sub ON sub.uid = f.subscription_id
                    WHERE f.stream_id = s.uid AND sub.profile_id = :profile)
            OR EXISTS (SELECT 1 FROM playlist_stream_join ps JOIN playlists p ON p.uid = ps.playlist_id
                       WHERE ps.stream_id = s.uid AND p.profile_id = :profile)
            OR EXISTS (SELECT 1 FROM stream_history h WHERE h.stream_id = s.uid AND h.profile_id = :profile)
        )
        AND (:maxSeconds = 0 OR (s.duration > 0 AND s.duration <= :maxSeconds))
        AND (:keyword = '' OR instr(lower(s.title), lower(:keyword)) > 0)
        AND (:allChannels OR EXISTS (
            SELECT 1 FROM subscriptions sub WHERE sub.profile_id = :profile
            AND sub.uid IN (:channels) AND sub.service_id = s.service_id AND sub.url = s.uploader_url
        ))
        AND (:watch = 0 OR (:watch = 1 AND
            NOT (COALESCE(st.progress_time, 0) > 5000 OR
                 COALESCE(st.progress_time, 0) > s.duration * 1000 / 4))
            OR (:watch = 2 AND (st.progress_time > 5000 OR st.progress_time > s.duration * 1000 / 4)
                AND NOT (st.progress_time >= s.duration * 1000 - 60000
                         AND st.progress_time >= s.duration * 1000 * 3 / 4)))
        AND (:learningOnly = 0 OR s.uid IN (
            SELECT cs.stream_id FROM learning_content_streams cs
            UNION SELECT ps.stream_id FROM playlist_stream_join ps
            JOIN playlists p ON p.uid = ps.playlist_id
            JOIN learning_content_sources src ON src.local_playlist_id = p.uid
            WHERE p.profile_id = :profile
        ))
        ORDER BY s.upload_date DESC, s.uid DESC LIMIT 1000
    """
    )
    fun observe(profile: String, watch: Int, maxSeconds: Long, keyword: String, allChannels: Boolean, channels: List<Long>, learningOnly: Boolean): Flowable<List<StreamWithState>>
}
