package org.schabi.newpipe.learning

import org.schabi.newpipe.database.learning.model.LearningNoteEntity

/** Plain-text copy of one video's notes, without changing their timestamps or content. */
object LearningVideoNotesCopy {
    @JvmStatic
    fun text(notes: List<LearningNoteEntity>): String = notes.sortedWith(
        compareBy<LearningNoteEntity> { it.timestampMillis }
            .thenBy { it.createdAtEpochMillis }
            .thenBy { it.noteId }
    ).joinToString("\n\n") {
        "${LearningNoteTime.format(it.timestampMillis)}\n${it.noteText}"
    }
}
