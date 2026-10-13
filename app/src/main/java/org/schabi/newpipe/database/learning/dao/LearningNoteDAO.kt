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
import org.schabi.newpipe.database.learning.model.BookmarkNote
import org.schabi.newpipe.database.learning.model.LearningNoteEntity
import org.schabi.newpipe.database.learning.model.VideoNoteStream
import org.schabi.newpipe.learning.LearningPlaylistNote

@Dao
interface LearningNoteDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(note: LearningNoteEntity): Long

    @Query("SELECT * FROM learning_notes WHERE note_id = :noteId")
    fun getNote(noteId: String): LearningNoteEntity?

    @Query("SELECT * FROM learning_notes ORDER BY updated_at ASC, note_id ASC")
    fun getAllDirect(): List<LearningNoteEntity>

    @Query(
        "SELECT * FROM learning_notes WHERE stream_id = :streamId " +
            "ORDER BY timestamp_ms ASC, created_at ASC, note_id ASC"
    )
    fun getNotesForStream(streamId: Long): Flowable<List<LearningNoteEntity>>

    @Query(
        "SELECT * FROM learning_notes WHERE stream_id = :streamId " +
            "ORDER BY timestamp_ms ASC, created_at ASC, note_id ASC"
    )
    fun getNotesForStreamDirect(streamId: Long): List<LearningNoteEntity>

    @Query(
        """
        SELECT n.*, s.title AS video_title, s.url AS video_url
        FROM learning_notes n INNER JOIN streams s ON s.uid = n.stream_id
        WHERE n.stream_id IN (
            SELECT cs.stream_id FROM learning_content_streams cs
            INNER JOIN learning_content_sources src ON src.source_id = cs.source_id
            WHERE src.source_id = :sourceId AND src.source_type = 'REMOTE_PLAYLIST'
            UNION
            SELECT ps.stream_id FROM playlist_stream_join ps
            INNER JOIN learning_content_sources src ON src.local_playlist_id = ps.playlist_id
            INNER JOIN playlists p ON p.uid = ps.playlist_id
            WHERE src.source_id = :sourceId AND p.profile_id = :profileId
        )
        ORDER BY s.title, n.timestamp_ms, n.note_id
        """
    )
    fun playlistNotes(sourceId: String, profileId: String): Flowable<List<LearningPlaylistNote>>

    @Query(
        """
        SELECT DISTINCT s.uid AS streamId, s.service_id AS serviceId, s.url AS url
        FROM streams s INNER JOIN learning_notes n ON n.stream_id = s.uid
        ORDER BY s.uid
        """
    )
    fun streamsWithNotes(): Flowable<List<VideoNoteStream>>

    @Query(
        """
        SELECT s.*, n.note_text, 'local:' || p.uid AS playlist_key
        FROM learning_notes n
        INNER JOIN streams s ON s.uid = n.stream_id
        INNER JOIN playlist_stream_join ps ON ps.stream_id = s.uid
        INNER JOIN playlists p ON p.uid = ps.playlist_id
        WHERE p.profile_id = :profileId
        UNION
        SELECT s.*, n.note_text, 'remote:' || p.service_id || ':' || p.url AS playlist_key
        FROM learning_notes n
        INNER JOIN streams s ON s.uid = n.stream_id
        INNER JOIN learning_content_streams cs ON cs.stream_id = s.uid
        INNER JOIN learning_content_sources src ON src.source_id = cs.source_id
        INNER JOIN remote_playlists p ON p.service_id = src.service_id AND p.url = src.url
        WHERE p.profile_id = :profileId AND src.source_type = 'REMOTE_PLAYLIST'
        ORDER BY title, uid
        """
    )
    fun bookmarkNotes(profileId: String): Flowable<List<BookmarkNote>>

    @Query(
        """
        SELECT n.* FROM learning_notes n WHERE n.stream_id IN (
            SELECT ps.stream_id FROM playlist_stream_join ps
            INNER JOIN playlists p ON p.uid = ps.playlist_id
            WHERE p.uid = :playlistId AND p.profile_id = :profileId
        )
        ORDER BY n.timestamp_ms, n.note_id
        """
    )
    fun localPlaylistNotes(playlistId: Long, profileId: String): Flowable<List<LearningNoteEntity>>

    @Query("DELETE FROM learning_notes WHERE note_id = :noteId")
    fun delete(noteId: String): Int

    @Query("DELETE FROM learning_notes")
    fun deleteAll(): Int
}
