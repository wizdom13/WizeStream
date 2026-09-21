package org.schabi.newpipe.local.history

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.OffsetDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.history.model.StreamHistoryEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.stream.model.StreamStateEntity
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.testUtil.TestDatabase

@RunWith(AndroidJUnit4::class)
class ProfileHistoryIsolationTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = TestDatabase.createReplacingNewPipeDatabase()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun watchHistoryAndProgressAreIndependentPerProfile() {
        val profileA = "profile-a"
        val profileB = "profile-b"
        val streamId = database.streamDAO().insert(
            StreamEntity(
                serviceId = 0,
                url = "https://example.com/shared-video",
                title = "Shared video",
                streamType = StreamType.VIDEO_STREAM,
                duration = 120,
                uploader = "Channel"
            )
        )

        database.streamStateDAO().insert(
            StreamStateEntity(streamId, 15_000, profileA)
        )
        database.streamStateDAO().insert(
            StreamStateEntity(streamId, 45_000, profileB)
        )
        database.streamHistoryDAO().insert(
            StreamHistoryEntity(
                streamId,
                OffsetDateTime.parse("2026-09-20T10:00:00Z"),
                1,
                profileA
            )
        )
        database.streamHistoryDAO().insert(
            StreamHistoryEntity(
                streamId,
                OffsetDateTime.parse("2026-09-20T11:00:00Z"),
                3,
                profileB
            )
        )

        assertEquals(
            15_000L,
            database.streamStateDAO()
                .getStateForProfile(profileA, streamId)
                .blockingFirst()
                .single()
                .progressMillis
        )
        assertEquals(
            45_000L,
            database.streamStateDAO()
                .getStateForProfile(profileB, streamId)
                .blockingFirst()
                .single()
                .progressMillis
        )
        assertEquals(
            1L,
            database.streamHistoryDAO()
                .getLatestEntryForProfile(profileA, streamId)
                ?.repeatCount
        )
        assertEquals(
            3L,
            database.streamHistoryDAO()
                .getLatestEntryForProfile(profileB, streamId)
                ?.repeatCount
        )

        database.streamStateDAO().deleteAllForProfile(profileA)
        database.streamHistoryDAO().deleteAllForProfile(profileA)

        assertTrue(
            database.streamStateDAO()
                .getStateForProfile(profileA, streamId)
                .blockingFirst()
                .isEmpty()
        )
        assertEquals(
            45_000L,
            database.streamStateDAO()
                .getStateForProfile(profileB, streamId)
                .blockingFirst()
                .single()
                .progressMillis
        )
        assertEquals(
            3L,
            database.streamHistoryDAO()
                .getLatestEntryForProfile(profileB, streamId)
                ?.repeatCount
        )
    }
}
