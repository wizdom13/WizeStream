package org.schabi.newpipe.learning

import androidx.room.ColumnInfo
import androidx.room.Embedded
import org.schabi.newpipe.database.learning.model.LearningNoteEntity

data class LearningPlaylistNote(
    @Embedded val note: LearningNoteEntity,
    @ColumnInfo(name = "video_title") val title: String,
    @ColumnInfo(name = "video_url") val url: String
)

object LearningNotesExport {
    fun text(notes: List<LearningPlaylistNote>): String = notes.joinToString("\n\n") {
        "${it.title}\n${it.url}\n${LearningNoteTime.format(it.note.timestampMillis)}\n${it.note.noteText}"
    }

    fun csv(notes: List<LearningPlaylistNote>): String = "Video,URL,Timestamp,Note\r\n" + notes.joinToString("\r\n") {
        listOf(it.title, it.url, LearningNoteTime.format(it.note.timestampMillis), it.note.noteText)
            .joinToString(",") { field -> "\"${field.replace("\"", "\"\"")}\"" }
    }
}
