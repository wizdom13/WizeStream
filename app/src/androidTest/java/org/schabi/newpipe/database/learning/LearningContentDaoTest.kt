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
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.learning.model.LearningContentSourceEntity
import org.schabi.newpipe.database.learning.model.LearningContentStreamEntity
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamType

@RunWith(AndroidJUnit4::class)
class LearningContentDaoTest {
    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        AppDatabase::class.java
    ).allowMainThreadQueries().build()

    @After
    fun closeDatabase() = database.close()

    @Test
    fun courseLookupPreservesOrderAndExcludesOtherProfiles() {
        val first = database.streamDAO().insert(StreamEntity(serviceId = 0, url = "https://example.com/first", title = "Z", streamType = StreamType.VIDEO_STREAM, duration = 60, uploader = "Teacher"))
        val second = database.streamDAO().insert(StreamEntity(serviceId = 0, url = "https://example.com/second", title = "A", streamType = StreamType.VIDEO_STREAM, duration = 60, uploader = "Teacher"))
        val playlist = database.playlistDAO().insert(PlaylistEntity(name = "Course", isThumbnailPermanent = false, thumbnailStreamId = -1, displayIndex = 0, profileId = "owner"))
        database.playlistStreamDAO().insertAll(listOf(PlaylistStreamEntity(playlist, first, 0), PlaylistStreamEntity(playlist, second, 1)))
        val dao = database.learningContentDAO()
        dao.upsertSource(LearningContentSourceEntity("local-playlist:$playlist", LearningContentSourceEntity.TYPE_LOCAL_PLAYLIST, localPlaylistId = playlist))
        assertEquals(listOf(first, second), dao.courseStreams(playlist, "owner").map { it.uid })
        assertEquals(1, dao.coursesForStream(second, "owner").size)
        assertEquals(0, dao.coursesForStream(second, "other").size)
        assertEquals(0, dao.courseStreams(playlist, "other").size)
    }

    @Test
    fun streamRemainsEligibleWhileAnotherSourceStillReferencesIt() {
        val streamId = database.streamDAO().insert(
            StreamEntity(
                serviceId = 0,
                url = "https://example.com/lesson",
                title = "Lesson",
                streamType = StreamType.VIDEO_STREAM,
                duration = 600,
                uploader = "Teacher"
            )
        )
        val dao = database.learningContentDAO()
        val streamSource = LearningContentSourceEntity(
            sourceId = "stream:0:https://example.com/lesson",
            sourceType = LearningContentSourceEntity.TYPE_STREAM,
            serviceId = 0,
            url = "https://example.com/lesson"
        )
        val playlistSource = LearningContentSourceEntity(
            sourceId = "remote-playlist:0:https://example.com/course",
            sourceType = LearningContentSourceEntity.TYPE_REMOTE_PLAYLIST,
            serviceId = 0,
            url = "https://example.com/course"
        )
        dao.upsertSource(streamSource)
        dao.upsertSource(playlistSource)
        dao.insertSourceStreams(
            listOf(
                LearningContentStreamEntity(streamSource.sourceId, streamId),
                LearningContentStreamEntity(playlistSource.sourceId, streamId)
            )
        )

        assertEquals(1, dao.observeEligibleStreamKeys().blockingFirst().size)
        dao.deleteSource(playlistSource.sourceId)
        assertEquals(1, dao.observeEligibleStreamKeys().blockingFirst().size)
        dao.deleteSource(streamSource.sourceId)
        assertEquals(0, dao.observeEligibleStreamKeys().blockingFirst().size)
    }
}
