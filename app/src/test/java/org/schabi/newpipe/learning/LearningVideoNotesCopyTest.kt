package org.schabi.newpipe.learning

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.database.learning.model.LearningNoteEntity

class LearningVideoNotesCopyTest {
    @Test
    fun copiesEveryNoteInTimestampOrderAndPreservesMultilineText() {
        val previousLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            val notes = listOf(
                note("later", 3_661_000, "Arabic: مرحباً\nSecond line"),
                note("first", 12_000, "First note")
            )
            assertEquals(
                "0:12\nFirst note\n\n1:01:01\nArabic: مرحباً\nSecond line",
                LearningVideoNotesCopy.text(notes)
            )
            assertEquals("later", notes.first().noteId)
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    @Test
    fun emptyNotesHaveNoClipboardContent() {
        assertEquals("", LearningVideoNotesCopy.text(emptyList()))
    }

    private fun note(id: String, timestamp: Long, text: String) = LearningNoteEntity(
        id,
        7,
        timestamp,
        text,
        1,
        1
    )
}
