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
import org.schabi.newpipe.database.learning.model.LearningNoteEntity

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

    @Query("DELETE FROM learning_notes WHERE note_id = :noteId")
    fun delete(noteId: String): Int

    @Query("DELETE FROM learning_notes")
    fun deleteAll(): Int
}
