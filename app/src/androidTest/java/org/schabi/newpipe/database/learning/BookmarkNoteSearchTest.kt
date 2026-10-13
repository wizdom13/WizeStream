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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.learning.model.LearningContentSourceEntity
import org.schabi.newpipe.database.learning.model.LearningContentStreamEntity
import org.schabi.newpipe.database.learning.model.LearningNoteEntity
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.search.ContextualSearchHelper
import org.schabi.newpipe.local.search.NoteSearch

@RunWith(AndroidJUnit4::class)
class BookmarkNoteSearchTest {
    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        AppDatabase::class.java
    ).allowMainThreadQueries().build()

    @After
    fun closeDatabase() = database.close()

    @Test
    fun localSearchMatchesNotesWithoutDuplicatesAndRespectsProfileAndCategory() {
        val streamId = stream("lesson")
        val local = playlist("owner")
        val otherLocal = playlist("owner")
        val foreign = playlist("other")
        listOf(local, otherLocal, foreign).forEach {
            database.playlistStreamDAO().insert(PlaylistStreamEntity(it, streamId, 0))
        }
        val privateStream = stream("private")
        database.playlistStreamDAO().insert(PlaylistStreamEntity(foreign, privateStream, 1))
        note("one", streamId, "Résumé of the lesson")
        note("two", streamId, "Another résumé")
        note("private", privateStream, "Résumé from another profile")
        val dao = database.learningNoteDAO()
        val notes = dao.bookmarkNotes("owner").blockingFirst()
        assertEquals(setOf(streamId), notes.map { it.stream.uid }.toSet())
        val matches = NoteSearch.bookmarkStreams(notes, "RÉSUMÉ", setOf("local:$local", "local:$otherLocal"))
        assertEquals(listOf(streamId), matches.map { it.streamId })
        assertTrue(NoteSearch.bookmarkStreams(notes, "résumé", setOf("local:$foreign")).isEmpty())
        assertTrue(NoteSearch.bookmarkStreams(notes, "", setOf("local:$local")).isEmpty())
        assertTrue(dao.localPlaylistNotes(foreign, "owner").blockingFirst().isEmpty())
        val text = NoteSearch.textByStream(dao.localPlaylistNotes(local, "owner").blockingFirst())
        assertTrue(ContextualSearchHelper.matches("another résumé", text[streamId]))
        dao.delete("two")
        assertEquals("Résumé of the lesson", NoteSearch.textByStream(dao.localPlaylistNotes(local, "owner").blockingFirst())[streamId])
    }

    @Test
    fun remoteNotesRequireABookmarkInTheActiveProfile() {
        val streamId = stream("remote")
        note("remote", streamId, "A remote note")
        val url = "https://example.com/course"
        val source = LearningContentSourceEntity(
            "remote-playlist:0:$url",
            LearningContentSourceEntity.TYPE_REMOTE_PLAYLIST,
            serviceId = 0,
            url = url
        )
        database.learningContentDAO().upsertSource(source)
        database.learningContentDAO().insertSourceStreams(listOf(LearningContentStreamEntity(source.sourceId, streamId)))
        database.playlistRemoteDAO().insert(
            PlaylistRemoteEntity(serviceId = 0, orderingName = "Course", url = url, thumbnailUrl = null, uploader = null, streamCount = 1, profileId = "owner")
        )
        val dao = database.learningNoteDAO()
        assertTrue(dao.bookmarkNotes("other").blockingFirst().isEmpty())
        assertEquals(listOf(streamId), NoteSearch.bookmarkStreams(dao.bookmarkNotes("owner").blockingFirst(), "REMOTE", setOf("remote:0:$url")).map { it.streamId })
        database.playlistRemoteDAO().deleteAllForProfile("owner")
        assertTrue(dao.bookmarkNotes("owner").blockingFirst().isEmpty())
    }

    private fun stream(name: String) = database.streamDAO().insert(
        StreamEntity(serviceId = 0, url = "https://example.com/$name", title = name, streamType = StreamType.VIDEO_STREAM, duration = 60, uploader = "Teacher")
    )

    private fun playlist(profile: String) = database.playlistDAO().insert(
        PlaylistEntity(name = "Course", isThumbnailPermanent = false, thumbnailStreamId = -1, displayIndex = 0, profileId = profile)
    )

    private fun note(id: String, streamId: Long, text: String) = database.learningNoteDAO().upsert(
        LearningNoteEntity(id, streamId, 1000, text, 0, 0)
    )
}
