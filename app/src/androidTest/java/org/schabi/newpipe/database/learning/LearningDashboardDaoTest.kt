/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.learning

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.OffsetDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.history.model.StreamHistoryEntity
import org.schabi.newpipe.database.learning.model.LearningNoteEntity
import org.schabi.newpipe.database.learning.model.LearningSessionEntity
import org.schabi.newpipe.database.learning.model.LearningContentSourceEntity
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.stream.model.StreamStateEntity
import org.schabi.newpipe.extractor.stream.StreamType

@RunWith(AndroidJUnit4::class)
class LearningDashboardDaoTest {
    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        AppDatabase::class.java
    ).allowMainThreadQueries().build()

    @After
    fun closeDatabase() = database.close()

    @Test
    fun aggregatesProgressContinueLearningAndRecentNotes() {
        val partialId = database.streamDAO().insert(stream("partial", "Partial lesson"))
        val completedId = database.streamDAO().insert(stream("completed", "Completed lesson"))
        val playlistId = database.playlistDAO().insert(
            PlaylistEntity(
                name = "Course",
                isThumbnailPermanent = false,
                thumbnailStreamId = partialId,
                displayIndex = 0
            )
        )
        database.playlistStreamDAO().insertAll(
            listOf(
                PlaylistStreamEntity(playlistId, partialId, 0),
                PlaylistStreamEntity(playlistId, completedId, 1)
            )
        )
        database.learningContentDAO().upsertSource(
            LearningContentSourceEntity(
                sourceId = "local-playlist:$playlistId",
                sourceType = LearningContentSourceEntity.TYPE_LOCAL_PLAYLIST,
                localPlaylistId = playlistId,
                title = "Course"
            )
        )
        database.streamStateDAO().insert(StreamStateEntity(partialId, 300_000))
        database.streamStateDAO().insert(StreamStateEntity(completedId, 600_000))
        database.streamHistoryDAO().insert(
            StreamHistoryEntity(partialId, OffsetDateTime.now(), 1)
        )
        database.learningNoteDAO().upsert(
            LearningNoteEntity("note", partialId, 15_000, "Review", 1, 2)
        )
        database.learningSessionDAO().upsert(
            LearningSessionEntity(
                "session-1",
                partialId,
                1,
                61_001,
                60_000,
                "2026-08-05",
                false,
                true
            )
        )
        database.learningSessionDAO().upsert(
            LearningSessionEntity(
                "session-2",
                completedId,
                2,
                122_002,
                120_000,
                "2026-08-05",
                true,
                true
            )
        )

        val dao = database.learningDashboardDAO()
        val summary = dao.observePlaylistSummaries().blockingFirst().single()
        assertEquals(2, summary.eligibleCount)
        assertEquals(1, summary.completedCount)
        assertEquals(50, summary.percentage)

        val continueLearning = dao.observeContinueLearning(5).blockingFirst()
        assertEquals(listOf(partialId), continueLearning.map { it.stream.uid })
        assertEquals(50, continueLearning.single().progressPercentage)

        val annotated = dao.observeRecentlyAnnotated(5).blockingFirst().single()
        assertEquals(partialId, annotated.stream.uid)
        assertEquals(1, annotated.noteCount)
        assertEquals(2, annotated.latestNoteUpdate)

        val activity = dao.observeDailyStudyActivity().blockingFirst().single()
        assertEquals("2026-08-05", activity.localDate)
        assertEquals(180_000, activity.watchedDurationMillis)
    }

    private fun stream(urlSuffix: String, title: String) = StreamEntity(
        serviceId = 0,
        url = "https://example.com/$urlSuffix",
        title = title,
        streamType = StreamType.VIDEO_STREAM,
        duration = 600,
        uploader = "Teacher"
    )
}
