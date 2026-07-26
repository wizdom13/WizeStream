/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import io.libp2p.core.crypto.KeyType
import io.libp2p.core.crypto.generateKeyPair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HistorySyncEngineTest {
    @Test
    fun `watch events merge and the latest progress update can rewind`() {
        val phoneStore = newStore()
        val tabletStore = newStore()
        phoneStore.registerStream(STREAM_ID, STREAM_URL)
        tabletStore.registerStream(STREAM_ID, STREAM_URL)
        val phone = HistorySyncEngine(phoneStore)
        val tablet = HistorySyncEngine(tabletStore)
        phoneStore.recordWatchEvent(STREAM_ID, 1_000, 2)
        phoneStore.recordProgress(STREAM_ID, 90_000, 1_000)

        synchronize(
            HistorySyncCategory.WATCH,
            phone,
            phoneStore,
            tablet,
            tabletStore
        )

        assertEquals(2L, tabletStore.repeatCount(STREAM_URL))
        assertEquals(90_000L, tabletStore.progressMillis(STREAM_URL))

        tabletStore.recordProgress(STREAM_ID, 12_000, 2_000)
        synchronize(
            HistorySyncCategory.WATCH,
            tablet,
            tabletStore,
            phone,
            phoneStore
        )

        assertEquals(12_000L, phoneStore.progressMillis(STREAM_URL))
        assertEquals(12_000L, tabletStore.progressMillis(STREAM_URL))
    }

    @Test
    fun `search history is append only and deletion tombstones can be superseded`() {
        val phoneStore = newStore()
        val tabletStore = newStore()
        val phone = HistorySyncEngine(phoneStore)
        val tablet = HistorySyncEngine(tabletStore)
        phoneStore.recordSearch(SERVICE_ID, "spices", 1_000)
        phoneStore.recordSearch(SERVICE_ID, "recipes", 2_000)
        phoneStore.recordSearch(SERVICE_ID, "spices", 3_000)

        synchronize(
            HistorySyncCategory.SEARCH,
            phone,
            phoneStore,
            tablet,
            tabletStore
        )

        assertEquals(
            listOf("spices", "recipes", "spices"),
            tabletStore.searchQueries
        )

        tabletStore.recordSearchDelete("spices")
        synchronize(
            HistorySyncCategory.SEARCH,
            tablet,
            tabletStore,
            phone,
            phoneStore
        )

        assertEquals(listOf("recipes"), phoneStore.searchQueries)
        assertEquals(listOf("recipes"), tabletStore.searchQueries)

        phoneStore.recordSearch(SERVICE_ID, "spices", 4_000)
        synchronize(
            HistorySyncCategory.SEARCH,
            phone,
            phoneStore,
            tablet,
            tabletStore
        )

        assertEquals(listOf("recipes", "spices"), phoneStore.searchQueries)
        assertEquals(phoneStore.searchQueries, tabletStore.searchQueries)

        phoneStore.recordSearchAllDelete()
        synchronize(
            HistorySyncCategory.SEARCH,
            phone,
            phoneStore,
            tablet,
            tabletStore
        )

        assertTrue(phoneStore.searchQueries.isEmpty())
        assertTrue(tabletStore.searchQueries.isEmpty())
    }

    @Test
    fun `watch and search journals batch and acknowledge independently`() {
        val phoneStore = newStore()
        val tabletStore = newStore()
        phoneStore.registerStream(STREAM_ID, STREAM_URL)
        tabletStore.registerStream(STREAM_ID, STREAM_URL)
        val phone = HistorySyncEngine(phoneStore)
        val tablet = HistorySyncEngine(tabletStore)
        repeat(MAX_HISTORY_CHANGES_PER_BATCH + 5) { index ->
            phoneStore.recordWatchEvent(STREAM_ID, index.toLong(), 1)
        }
        phoneStore.recordSearch(SERVICE_ID, "private query", 1_000)

        val watchRounds = synchronize(
            HistorySyncCategory.WATCH,
            phone,
            phoneStore,
            tablet,
            tabletStore
        )

        assertTrue(watchRounds >= 2)
        assertEquals(
            MAX_HISTORY_CHANGES_PER_BATCH + 5L,
            tabletStore.repeatCount(STREAM_URL)
        )
        assertTrue(tabletStore.searchQueries.isEmpty())

        synchronize(
            HistorySyncCategory.SEARCH,
            phone,
            phoneStore,
            tablet,
            tabletStore
        )

        assertEquals(listOf("private query"), tabletStore.searchQueries)
        val repeatRequest = phone.createRequest(
            tabletStore.localPeerId,
            HistorySyncCategory.WATCH
        )
        assertTrue(repeatRequest.changes.isEmpty())
        assertFalse(repeatRequest.hasMore)
    }

    @Test
    fun `disabled search history rejects both outgoing and incoming sync`() {
        val phoneStore = newStore()
        val tabletStore = newStore()
        val phone = HistorySyncEngine(phoneStore)
        val tablet = HistorySyncEngine(tabletStore) { category ->
            category != HistorySyncCategory.SEARCH
        }
        phoneStore.recordSearch(SERVICE_ID, "private query", 1_000)
        val request = phone.createRequest(
            tabletStore.localPeerId,
            HistorySyncCategory.SEARCH
        )

        val response = tablet.handleRequest(phoneStore.localPeerId, request)

        assertFalse(response.accepted)
        assertTrue(response.error.orEmpty().contains("disabled"))
        assertThrows(HistorySyncException::class.java) {
            tablet.createRequest(
                phoneStore.localPeerId,
                HistorySyncCategory.SEARCH
            )
        }
        assertTrue(tabletStore.searchQueries.isEmpty())
    }

    @Test
    fun `noncanonical search query is rejected without applying it`() {
        val phoneStore = newStore()
        val tabletStore = newStore()
        val tablet = HistorySyncEngine(tabletStore)
        val malformed = HistoryChange(
            category = HistorySyncCategory.SEARCH,
            originPeerId = phoneStore.localPeerId,
            originRevision = 1,
            lamportVersion = 1,
            recordId = HistoryRecordId.searchEvent(),
            recordType = HistoryRecordType.SEARCH_EVENT,
            type = HistoryChangeType.UPSERT,
            record = SyncedHistoryRecord(
                searchEvent = SyncedSearchEvent(
                    SERVICE_ID,
                    " padded query ",
                    1_000
                )
            )
        )

        val response = tablet.handleRequest(
            phoneStore.localPeerId,
            HistorySyncRequest(
                category = HistorySyncCategory.SEARCH,
                knownRevisions = emptyMap(),
                changes = listOf(malformed),
                hasMore = false
            )
        )

        assertFalse(response.accepted)
        assertTrue(tabletStore.searchQueries.isEmpty())
    }

    private fun synchronize(
        category: HistorySyncCategory,
        initiator: HistorySyncEngine,
        initiatorStore: TestHistorySyncStore,
        responder: HistorySyncEngine,
        responderStore: TestHistorySyncStore
    ): Int {
        var rounds = 0
        while (true) {
            rounds += 1
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
                return rounds
            }
        }
    }

    private fun newStore() = TestHistorySyncStore(newPeerId())

    private fun newPeerId(): String {
        val privateKey = generateKeyPair(KeyType.ED25519).first
        return DeviceIdentity(privateKey).peerId.toBase58()
    }

    companion object {
        private const val SERVICE_ID = 0
        private const val STREAM_ID = 1L
        private const val STREAM_URL = "https://example.com/watch/history"
    }
}
