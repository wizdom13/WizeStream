/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.learning

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.learning.model.LearningNoteEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamType

@RunWith(AndroidJUnit4::class)
class LearningNoteDaoTest {
    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        AppDatabase::class.java
    ).allowMainThreadQueries().build()

    @After
    fun closeDatabase() = database.close()

    @Test
    fun notesAreOrderedAndCanBeUpdatedAndDeleted() {
        val streamId = database.streamDAO().upsert(
            StreamEntity(
                serviceId = 0,
                url = "https://example.com/video",
                title = "Lesson",
                streamType = StreamType.VIDEO_STREAM,
                duration = 600,
                uploader = "Teacher"
            )
        )
        val dao = database.learningNoteDAO()
        val later = note("00000000-0000-0000-0000-000000000002", streamId, 20_000, "Later")
        val earlier = note("00000000-0000-0000-0000-000000000001", streamId, 10_000, "Earlier")
        dao.upsert(later)
        dao.upsert(earlier)

        assertEquals(listOf(earlier, later), dao.getNotesForStreamDirect(streamId))

        val updated = earlier.copy(noteText = "Updated", updatedAtEpochMillis = 2)
        dao.upsert(updated)
        assertEquals(updated, dao.getNote(earlier.noteId))

        dao.delete(earlier.noteId)
        assertNull(dao.getNote(earlier.noteId))
    }

    private fun note(
        noteId: String,
        streamId: Long,
        timestamp: Long,
        text: String
    ) = LearningNoteEntity(noteId, streamId, timestamp, text, 1, 1)
}
