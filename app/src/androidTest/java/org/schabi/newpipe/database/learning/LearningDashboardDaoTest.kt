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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.history.model.StreamHistoryEntity
import org.schabi.newpipe.database.learning.model.LearningContentSourceEntity
import org.schabi.newpipe.database.learning.model.LearningContentStreamEntity
import org.schabi.newpipe.database.learning.model.LearningNoteEntity
import org.schabi.newpipe.database.learning.model.LearningSessionEntity
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.stream.model.StreamStateEntity
import org.schabi.newpipe.extractor.stream.StreamType

@RunWith(AndroidJUnit4::class)
class LearningDashboardDaoTest {
    private val profileId = "00000000-0000-0000-0000-000000000000"
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
        val foreignPlaylistId = database.playlistDAO().insert(
            PlaylistEntity(
                name = "Other profile course",
                isThumbnailPermanent = false,
                thumbnailStreamId = partialId,
                displayIndex = 0,
                profileId = "other-profile"
            )
        )
        database.playlistStreamDAO().insert(
            PlaylistStreamEntity(foreignPlaylistId, partialId, 0)
        )
        database.learningContentDAO().upsertSource(
            LearningContentSourceEntity(
                sourceId = "local-playlist:$foreignPlaylistId",
                sourceType = LearningContentSourceEntity.TYPE_LOCAL_PLAYLIST,
                localPlaylistId = foreignPlaylistId,
                title = "Other profile course"
            )
        )
        database.streamStateDAO().insert(StreamStateEntity(partialId, 300_000))
        database.streamStateDAO().insert(StreamStateEntity(completedId, 600_000))
        database.streamStateDAO().insert(
            StreamStateEntity(partialId, 600_000, "other-profile")
        )
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
        database.learningSessionDAO().upsert(
            LearningSessionEntity(
                "session-other-profile",
                partialId,
                3,
                303_003,
                300_000,
                "2026-08-05",
                false,
                true,
                "other-profile"
            )
        )

        val dao = database.learningDashboardDAO()
        val summary = dao.observePlaylistSummaries(profileId).blockingFirst().single()
        assertEquals(2, summary.eligibleCount)
        assertEquals(1, summary.completedCount)
        assertEquals(50, summary.percentage)

        val continueLearning = dao.observeContinueLearning(profileId, 5).blockingFirst()
        assertEquals(listOf(partialId), continueLearning.map { it.stream.uid })
        assertEquals(50, continueLearning.single().progressPercentage)

        val annotated = dao.observeRecentlyAnnotated(profileId, 5).blockingFirst().single()
        assertEquals(partialId, annotated.stream.uid)
        assertEquals(1, annotated.noteCount)
        assertEquals(2, annotated.latestNoteUpdate)

        val activity = dao.observeDailyStudyActivity(profileId).blockingFirst().single()
        assertEquals("2026-08-05", activity.localDate)
        assertEquals(180_000, activity.watchedDurationMillis)
    }

    @Test
    fun lastLessonIncludesCompletedAndBrieflyWatchedLearningVideosOnly() {
        val completed = markedStream("completed-last", LearningContentSourceEntity.TYPE_STREAM)
        val brief = markedStream("brief-last", LearningContentSourceEntity.TYPE_STREAM)
        val unrelated = database.streamDAO().insert(stream("unrelated", "Entertainment"))
        database.streamStateDAO().insert(StreamStateEntity(completed, 600_000))
        recordView(completed, 100)
        recordView(unrelated, 200)
        assertEquals(completed, lastLesson().lesson.stream.uid)
        assertEquals(100, lastLesson().lesson.progressPercentage)
        assertEquals(0L, lastLesson().resumePositionMillis)

        recordView(brief, 300)
        assertEquals(brief, lastLesson().lesson.stream.uid)
        assertEquals(0L, lastLesson().lesson.progressMillis)
        database.learningContentDAO().deleteSource("marked:brief-last")
        assertEquals(completed, lastLesson().lesson.stream.uid)
    }

    @Test
    fun lastLessonSupportsRemoteCoursesWithoutLocalPlaylistMembership() {
        val lesson = markedStream("remote-last", LearningContentSourceEntity.TYPE_REMOTE_PLAYLIST)
        database.streamStateDAO().insert(StreamStateEntity(lesson, 120_000))
        recordView(lesson, 100)
        assertEquals(lesson, lastLesson().lesson.stream.uid)
        assertEquals("Remote course", lastLesson().courseTitle)
        assertEquals(120_000L, lastLesson().resumePositionMillis)
    }

    @Test
    fun lastLessonUsesLearningSessionsWhenWatchHistoryIsDisabled() {
        val lesson = markedStream("session-last", LearningContentSourceEntity.TYPE_STREAM)
        database.learningSessionDAO().upsert(
            LearningSessionEntity("last-session", lesson, 100, 200, 100, "2026-10-08", false, true)
        )
        assertEquals(lesson, lastLesson().lesson.stream.uid)
        assertTrue(database.learningDashboardDAO().observeLastLesson("other-profile").blockingFirst().isEmpty())
    }

    @Test
    fun lastLessonScopesHistoryProgressAndLocalCoursesToTheProfile() {
        val ownLesson = markedStream("own-last", LearningContentSourceEntity.TYPE_STREAM)
        val otherLesson = markedStream("other-last", LearningContentSourceEntity.TYPE_STREAM)
        recordView(ownLesson, 100)
        recordView(otherLesson, 300, "other-profile")
        database.streamStateDAO().insert(StreamStateEntity(ownLesson, 120_000))
        database.streamStateDAO().insert(StreamStateEntity(ownLesson, 500_000, "other-profile"))
        val foreignLesson = database.streamDAO().insert(stream("foreign-last", "Foreign lesson"))
        val foreignCourse = database.playlistDAO().insert(
            PlaylistEntity(
                name = "Foreign course",
                isThumbnailPermanent = false,
                thumbnailStreamId = foreignLesson,
                displayIndex = 0,
                profileId = "other-profile"
            )
        )
        database.playlistStreamDAO().insert(PlaylistStreamEntity(foreignCourse, foreignLesson, 0))
        database.learningContentDAO().upsertSource(
            LearningContentSourceEntity(
                "foreign-course",
                LearningContentSourceEntity.TYPE_LOCAL_PLAYLIST,
                localPlaylistId = foreignCourse,
                title = "Foreign course"
            )
        )
        recordView(foreignLesson, 500)
        assertEquals(ownLesson, lastLesson().lesson.stream.uid)
        assertEquals(120_000L, lastLesson().lesson.progressMillis)
        assertEquals(otherLesson, database.learningDashboardDAO().observeLastLesson("other-profile").blockingFirst().single().lesson.stream.uid)
    }

    private fun lastLesson() = database.learningDashboardDAO().observeLastLesson(profileId).blockingFirst().single()

    private fun recordView(streamId: Long, epochMillis: Long, profile: String = profileId) {
        database.streamHistoryDAO().insert(
            StreamHistoryEntity(streamId, OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMillis), java.time.ZoneOffset.UTC), 1, profile)
        )
    }

    private fun markedStream(suffix: String, type: String): Long {
        val streamId = database.streamDAO().insert(stream(suffix, "Lesson"))
        val sourceId = "marked:$suffix"
        database.learningContentDAO().upsertSource(
            LearningContentSourceEntity(sourceId, type, serviceId = 0, url = "https://example.com/course", title = "Remote course")
        )
        database.learningContentDAO().insertSourceStreams(listOf(LearningContentStreamEntity(sourceId, streamId)))
        return streamId
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
