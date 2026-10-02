package org.schabi.newpipe.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.database.learning.model.LearningNoteEntity

class LearningNotesExportTest {
    @Test
    fun csvQuotesCommasQuotesAndMultilineUnicodeNotes() {
        val row = LearningPlaylistNote(LearningNoteEntity("note", 1, 65000, "خطوة, \"one\"\nnext", 1, 1), "Lesson, one", "https://example.com/video")
        val csv = LearningNotesExport.csv(listOf(row))
        assertTrue(csv.startsWith("Video,URL,Timestamp,Note\r\n\"Lesson, one\","))
        assertTrue(csv.endsWith("\"خطوة, \"\"one\"\"\nnext\""))
        assertTrue(LearningNotesExport.text(listOf(row)).contains(row.note.noteText))
        assertEquals("Video,URL,Timestamp,Note\r\n", LearningNotesExport.csv(emptyList()))
    }
}
