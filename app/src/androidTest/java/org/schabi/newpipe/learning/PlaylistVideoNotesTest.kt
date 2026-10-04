package org.schabi.newpipe.learning

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamType

@RunWith(AndroidJUnit4::class)
class PlaylistVideoNotesTest {
    @Test
    fun existingPlaylistVideosCanHaveIndependentNotesWithoutLoadingStreamInfo() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = NewPipeDatabase.getInstance(context)
        val manager = LearningNoteManager(context)
        val streams = (1..2).map {
            StreamEntity(serviceId = 0, url = "https://example.com/note-${UUID.randomUUID()}", title = "Video $it", streamType = StreamType.VIDEO_STREAM, duration = 60, uploader = "Channel")
                .also { stream -> stream.uid = database.streamDAO().upsert(stream) }
        }
        val noteIds = mutableListOf<String>()
        try {
            val first = manager.createForStream(streams[0].uid, 0, "First explanation").blockingGet().also { noteIds.add(it.noteId) }
            val second = manager.createForStream(streams[1].uid, 42000, "Second explanation").blockingGet().also { noteIds.add(it.noteId) }
            val updated = manager.update(first, 1000, "Edited explanation").blockingGet()
            assertEquals(listOf(updated), database.learningNoteDAO().getNotesForStreamDirect(streams[0].uid))
            assertEquals(listOf(second), database.learningNoteDAO().getNotesForStreamDirect(streams[1].uid))
            manager.delete(first.noteId).blockingAwait()
            assertTrue(database.learningNoteDAO().getNotesForStreamDirect(streams[0].uid).isEmpty())
            assertEquals(listOf(second), database.learningNoteDAO().getNotesForStreamDirect(streams[1].uid))
        } finally {
            manager.deleteNotes(noteIds).blockingAwait()
            streams.forEach(database.streamDAO()::delete)
        }
    }
}
