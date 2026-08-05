/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.libp2p.core.crypto.KeyType
import io.libp2p.core.crypto.generateKeyPair
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.history.model.SearchHistoryEntry
import org.schabi.newpipe.database.history.model.StreamHistoryEntity
import org.schabi.newpipe.database.learning.model.LearningNoteEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.stream.model.StreamStateEntity
import org.schabi.newpipe.extractor.stream.StreamType

@RunWith(AndroidJUnit4::class)
class RoomHistorySyncStoreTest {
    private lateinit var phoneDatabase: AppDatabase
    private lateinit var tabletDatabase: AppDatabase

    @Before
    fun setUp() {
        phoneDatabase = newDatabase()
        tabletDatabase = newDatabase()
    }

    @After
    fun tearDown() {
        phoneDatabase.close()
        tabletDatabase.close()
    }

    @Test
    fun watchHistoryAndLaterRewindConvergeThroughRoomStores() {
        val phoneStore = RoomHistorySyncStore(phoneDatabase, newPeerId())
        val tabletStore = RoomHistorySyncStore(tabletDatabase, newPeerId())
        val phone = HistorySyncEngine(phoneStore)
        val tablet = HistorySyncEngine(tabletStore)
        val phoneStreamId = phoneDatabase.streamDAO().upsert(testStream())
        phoneDatabase.streamHistoryDAO().insert(
            StreamHistoryEntity(
                streamUid = phoneStreamId,
                accessDate = dateTime(1_000),
                repeatCount = 2
            )
        )
        phoneDatabase.streamStateDAO().upsert(
            StreamStateEntity(phoneStreamId, 90_000)
        )

        synchronize(
            HistorySyncCategory.WATCH,
            phone,
            phoneStore,
            tablet,
            tabletStore
        )

        val tabletStream = requireNotNull(
            tabletDatabase.streamDAO().getStreamDirect(SERVICE_ID, STREAM_URL)
        )
        assertEquals(
            2L,
            tabletDatabase.streamHistoryDAO().getAllDirect().single().repeatCount
        )
        assertEquals(
            90_000L,
            tabletDatabase.streamStateDAO().getAllDirect().single().progressMillis
        )

        tabletStore.recordProgress(
            tabletStream.uid,
            progressMillis = 12_000,
            updatedAtEpochMillis = 2_000
        )
        tabletDatabase.streamStateDAO().upsert(
            StreamStateEntity(tabletStream.uid, 12_000)
        )
        synchronize(
            HistorySyncCategory.WATCH,
            tablet,
            tabletStore,
            phone,
            phoneStore
        )

        assertEquals(
            12_000L,
            phoneDatabase.streamStateDAO().getAllDirect().single().progressMillis
        )
        assertEquals(
            12_000L,
            tabletDatabase.streamStateDAO().getAllDirect().single().progressMillis
        )

        tabletStore.recordWatchAllDelete()
        tabletDatabase.streamHistoryDAO().deleteAll()
        synchronize(
            HistorySyncCategory.WATCH,
            tablet,
            tabletStore,
            phone,
            phoneStore
        )

        assertTrue(phoneDatabase.streamHistoryDAO().getAllDirect().isEmpty())
        assertEquals(
            12_000L,
            phoneDatabase.streamStateDAO().getAllDirect().single().progressMillis
        )

        tabletStore.recordProgressAllDelete()
        tabletDatabase.streamStateDAO().deleteAll()
        synchronize(
            HistorySyncCategory.WATCH,
            tablet,
            tabletStore,
            phone,
            phoneStore
        )

        assertTrue(phoneDatabase.streamStateDAO().getAllDirect().isEmpty())
        assertTrue(tabletDatabase.streamStateDAO().getAllDirect().isEmpty())
    }

    @Test
    fun searchEventsAndDeletionTombstonesConvergeThroughRoomStores() {
        val phoneStore = RoomHistorySyncStore(phoneDatabase, newPeerId())
        val tabletStore = RoomHistorySyncStore(tabletDatabase, newPeerId())
        val phone = HistorySyncEngine(phoneStore)
        val tablet = HistorySyncEngine(tabletStore)
        phoneDatabase.searchHistoryDAO().insertAll(
            listOf(
                searchEntry("spices", 1_000),
                searchEntry("recipes", 2_000),
                searchEntry("spices", 3_000)
            )
        )

        synchronize(
            HistorySyncCategory.SEARCH,
            phone,
            phoneStore,
            tablet,
            tabletStore
        )

        assertEquals(
            listOf("spices", "recipes", "spices"),
            tabletDatabase.searchHistoryDAO().getAllDirect()
                .mapNotNull(SearchHistoryEntry::search)
        )

        tabletStore.recordSearchDelete("spices")
        tabletDatabase.searchHistoryDAO().deleteAllWhereQuery("spices")
        synchronize(
            HistorySyncCategory.SEARCH,
            tablet,
            tabletStore,
            phone,
            phoneStore
        )

        assertEquals(
            listOf("recipes"),
            phoneDatabase.searchHistoryDAO().getAllDirect()
                .mapNotNull(SearchHistoryEntry::search)
        )
        assertEquals(
            listOf("recipes"),
            tabletDatabase.searchHistoryDAO().getAllDirect()
                .mapNotNull(SearchHistoryEntry::search)
        )

        val revived = searchEntry("spices", 4_000)
        phoneStore.recordSearch(
            revived.serviceId,
            requireNotNull(revived.search),
            requireNotNull(revived.creationDate).toInstant().toEpochMilli()
        )
        phoneDatabase.searchHistoryDAO().insert(revived)
        synchronize(
            HistorySyncCategory.SEARCH,
            phone,
            phoneStore,
            tablet,
            tabletStore
        )

        assertEquals(
            listOf("recipes", "spices"),
            tabletDatabase.searchHistoryDAO().getAllDirect()
                .mapNotNull(SearchHistoryEntry::search)
        )

        phoneStore.recordSearchAllDelete()
        phoneDatabase.searchHistoryDAO().deleteAll()
        synchronize(
            HistorySyncCategory.SEARCH,
            phone,
            phoneStore,
            tablet,
            tabletStore
        )

        assertTrue(phoneDatabase.searchHistoryDAO().getAllDirect().isEmpty())
        assertTrue(tabletDatabase.searchHistoryDAO().getAllDirect().isEmpty())
    }

    @Test
    fun learningNoteEditsAndDeletionTombstonesConvergeThroughRoomStores() {
        val phoneStore = RoomHistorySyncStore(phoneDatabase, newPeerId())
        val tabletStore = RoomHistorySyncStore(tabletDatabase, newPeerId())
        val phone = HistorySyncEngine(phoneStore)
        val tablet = HistorySyncEngine(tabletStore)
        val phoneStreamId = phoneDatabase.streamDAO().upsert(testStream())
        val original = LearningNoteEntity(
            noteId = "99de68c3-22d6-4186-8056-c4e559006fc5",
            streamId = phoneStreamId,
            timestampMillis = 12_000,
            noteText = "Original note",
            createdAtEpochMillis = 1_000,
            updatedAtEpochMillis = 1_000
        )
        phoneDatabase.learningNoteDAO().upsert(original)
        phoneStore.recordLearningNoteUpsert(original.noteId)

        synchronize(
            HistorySyncCategory.LEARNING_NOTES,
            phone,
            phoneStore,
            tablet,
            tabletStore
        )

        val tabletCopy = requireNotNull(tabletDatabase.learningNoteDAO().getNote(original.noteId))
        assertEquals(original.timestampMillis, tabletCopy.timestampMillis)
        assertEquals(original.noteText, tabletCopy.noteText)

        val phoneEdit = requireNotNull(phoneDatabase.learningNoteDAO().getNote(original.noteId))
            .copy(noteText = "Phone edit", updatedAtEpochMillis = 2_000)
        phoneDatabase.learningNoteDAO().upsert(phoneEdit)
        phoneStore.recordLearningNoteUpsert(phoneEdit.noteId)
        val tabletEdit = tabletCopy.copy(noteText = "Tablet edit", updatedAtEpochMillis = 2_000)
        tabletDatabase.learningNoteDAO().upsert(tabletEdit)
        tabletStore.recordLearningNoteUpsert(tabletEdit.noteId)

        synchronize(
            HistorySyncCategory.LEARNING_NOTES,
            phone,
            phoneStore,
            tablet,
            tabletStore
        )
        synchronize(
            HistorySyncCategory.LEARNING_NOTES,
            tablet,
            tabletStore,
            phone,
            phoneStore
        )

        val convergedPhone = requireNotNull(
            phoneDatabase.learningNoteDAO().getNote(original.noteId)
        )
        val convergedTablet = requireNotNull(
            tabletDatabase.learningNoteDAO().getNote(original.noteId)
        )
        assertEquals(convergedPhone.noteText, convergedTablet.noteText)

        tabletStore.recordLearningNoteDelete(convergedTablet)
        tabletDatabase.learningNoteDAO().delete(convergedTablet.noteId)
        synchronize(
            HistorySyncCategory.LEARNING_NOTES,
            tablet,
            tabletStore,
            phone,
            phoneStore
        )

        assertTrue(phoneDatabase.learningNoteDAO().getAllDirect().isEmpty())
        assertTrue(tabletDatabase.learningNoteDAO().getAllDirect().isEmpty())
    }

    private fun synchronize(
        category: HistorySyncCategory,
        initiator: HistorySyncEngine,
        initiatorStore: HistorySyncStore,
        responder: HistorySyncEngine,
        responderStore: HistorySyncStore
    ) {
        while (true) {
            val request = initiator.createRequest(
                responderStore.localPeerId,
                category
            )
            val response = responder.handleRequest(
                initiatorStore.localPeerId,
                request
            )
            initiator.handleResponse(
                responderStore.localPeerId,
                category,
                response
            )
            if (!request.hasMore && !response.hasMore) {
                return
            }
        }
    }

    private fun newDatabase(): AppDatabase {
        return Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries()
            .build()
    }

    private fun newPeerId(): String {
        val privateKey = generateKeyPair(KeyType.ED25519).first
        return DeviceIdentity(privateKey).peerId.toBase58()
    }

    private fun searchEntry(query: String, epochMillis: Long) = SearchHistoryEntry(
        creationDate = dateTime(epochMillis),
        serviceId = SERVICE_ID,
        search = query
    )

    private fun dateTime(epochMillis: Long): OffsetDateTime {
        return OffsetDateTime.ofInstant(
            Instant.ofEpochMilli(epochMillis),
            ZoneOffset.UTC
        )
    }

    private fun testStream() = StreamEntity(
        serviceId = SERVICE_ID,
        url = STREAM_URL,
        title = "History test",
        streamType = StreamType.VIDEO_STREAM,
        duration = 180,
        uploader = "Uploader"
    )

    companion object {
        private const val SERVICE_ID = 0
        private const val STREAM_URL = "https://example.com/watch/history"
    }
}
