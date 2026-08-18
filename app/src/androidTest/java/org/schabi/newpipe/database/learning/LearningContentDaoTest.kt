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
