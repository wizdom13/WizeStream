/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import io.libp2p.core.crypto.KeyType
import io.libp2p.core.crypto.generateKeyPair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.database.subscription.SubscriptionEntity

class SubscriptionSyncEngineTest {
    @Test
    fun `subscription additions synchronize in both directions and are idempotent`() {
        val phoneStore = newStore()
        val tabletStore = newStore()
        val phone = SubscriptionSyncEngine(phoneStore)
        val tablet = SubscriptionSyncEngine(tabletStore)
        phoneStore.add(SERVICE_ID, PHONE_URL)
        tabletStore.add(SERVICE_ID, TABLET_URL)

        val rounds = synchronize(phone, phoneStore, tablet, tabletStore)

        assertEquals(setOf(PHONE_URL, TABLET_URL), phoneStore.subscriptionUrls)
        assertEquals(setOf(PHONE_URL, TABLET_URL), tabletStore.subscriptionUrls)
        assertTrue(rounds >= 1)

        val repeatRounds = synchronize(phone, phoneStore, tablet, tabletStore)
        assertEquals(1, repeatRounds)
        val request = phone.createRequest(tabletStore.localPeerId)
        assertTrue(request.changes.isEmpty())
        assertFalse(request.hasMore)
    }

    @Test
    fun `subscription deletion propagates as a tombstone`() {
        val phoneStore = newStore()
        val tabletStore = newStore()
        val phone = SubscriptionSyncEngine(phoneStore)
        val tablet = SubscriptionSyncEngine(tabletStore)
        phoneStore.add(SERVICE_ID, PHONE_URL)
        synchronize(phone, phoneStore, tablet, tabletStore)

        tabletStore.delete(SERVICE_ID, PHONE_URL)
        synchronize(tablet, tabletStore, phone, phoneStore)

        assertTrue(phoneStore.subscriptionUrls.isEmpty())
        assertTrue(tabletStore.subscriptionUrls.isEmpty())
        synchronize(tablet, tabletStore, phone, phoneStore)
        assertTrue(phoneStore.subscriptionUrls.isEmpty())
    }

    @Test
    fun `more than one batch is exchanged in a single manual sync`() {
        val phoneStore = newStore()
        val tabletStore = newStore()
        val phone = SubscriptionSyncEngine(phoneStore)
        val tablet = SubscriptionSyncEngine(tabletStore)
        repeat(MAX_SUBSCRIPTION_CHANGES_PER_BATCH + 9) { index ->
            phoneStore.add(SERVICE_ID, "https://example.com/channel/$index")
        }

        val rounds = synchronize(phone, phoneStore, tablet, tabletStore)

        assertTrue(rounds >= 2)
        assertEquals(phoneStore.subscriptionUrls, tabletStore.subscriptionUrls)
    }

    @Test
    fun `youtube mode membership changes synchronize`() {
        val phoneStore = newStore()
        val tabletStore = newStore()
        val phone = SubscriptionSyncEngine(phoneStore)
        val tablet = SubscriptionSyncEngine(tabletStore)
        phoneStore.recordLocalUpsert(
            SubscriptionEntity(
                serviceId = SERVICE_ID,
                url = PHONE_URL,
                name = "Music channel",
                youtubeModeMask = SubscriptionEntity.YOUTUBE_MODE_MUSIC
            )
        )

        synchronize(phone, phoneStore, tablet, tabletStore)

        assertEquals(
            SubscriptionEntity.YOUTUBE_MODE_MUSIC,
            tabletStore.youtubeModeMask(PHONE_URL)
        )

        phoneStore.recordLocalUpsert(
            SubscriptionEntity(
                serviceId = SERVICE_ID,
                url = PHONE_URL,
                name = "Music channel",
                youtubeModeMask = SubscriptionEntity.YOUTUBE_MODE_ALL
            )
        )
        synchronize(phone, phoneStore, tablet, tabletStore)

        assertEquals(
            SubscriptionEntity.YOUTUBE_MODE_ALL,
            tabletStore.youtubeModeMask(PHONE_URL)
        )
    }

    @Test
    fun `equal Lamport conflicts resolve identically regardless of arrival order`() {
        val firstStore = newStore()
        val secondStore = newStore()
        val firstOrigin = newPeerId()
        val secondOrigin = newPeerId()
        val recordId = SubscriptionRecordId.from(SERVICE_ID, PHONE_URL)
        val addition = SubscriptionChange(
            originPeerId = firstOrigin,
            originRevision = 1,
            lamportVersion = 7,
            recordId = recordId,
            serviceId = SERVICE_ID,
            url = PHONE_URL,
            type = SubscriptionChangeType.UPSERT,
            subscription = SyncedSubscription(SERVICE_ID, PHONE_URL, "Channel")
        )
        val deletion = SubscriptionChange(
            originPeerId = secondOrigin,
            originRevision = 1,
            lamportVersion = 7,
            recordId = recordId,
            serviceId = SERVICE_ID,
            url = PHONE_URL,
            type = SubscriptionChangeType.DELETE
        )

        firstStore.applyChanges(listOf(addition, deletion))
        secondStore.applyChanges(listOf(deletion, addition))

        assertEquals(firstStore.subscriptionUrls, secondStore.subscriptionUrls)
        val additionWins = addition.versionStamp > deletion.versionStamp
        assertEquals(additionWins, PHONE_URL in firstStore.subscriptionUrls)
    }

    @Test
    fun `malformed record identity is rejected without applying changes`() {
        val phoneStore = newStore()
        val tabletStore = newStore()
        val tablet = SubscriptionSyncEngine(tabletStore)
        val malformed = SubscriptionChange(
            originPeerId = phoneStore.localPeerId,
            originRevision = 1,
            lamportVersion = 1,
            recordId = "not-the-record-hash",
            serviceId = SERVICE_ID,
            url = PHONE_URL,
            type = SubscriptionChangeType.UPSERT,
            subscription = SyncedSubscription(SERVICE_ID, PHONE_URL, "Channel")
        )

        val response = tablet.handleRequest(
            phoneStore.localPeerId,
            SubscriptionSyncRequest(
                knownRevisions = emptyMap(),
                changes = listOf(malformed),
                hasMore = false
            )
        )

        assertFalse(response.accepted)
        assertTrue(tabletStore.subscriptionUrls.isEmpty())
    }

    @Test
    fun `noncanonical subscription URL is rejected without applying changes`() {
        val phoneStore = newStore()
        val tabletStore = newStore()
        val tablet = SubscriptionSyncEngine(tabletStore)
        val paddedUrl = " $PHONE_URL "
        val noncanonical = SubscriptionChange(
            originPeerId = phoneStore.localPeerId,
            originRevision = 1,
            lamportVersion = 1,
            recordId = SubscriptionRecordId.from(SERVICE_ID, paddedUrl),
            serviceId = SERVICE_ID,
            url = paddedUrl,
            type = SubscriptionChangeType.UPSERT,
            subscription = SyncedSubscription(
                SERVICE_ID,
                paddedUrl,
                "Channel"
            )
        )

        val response = tablet.handleRequest(
            phoneStore.localPeerId,
            SubscriptionSyncRequest(
                knownRevisions = emptyMap(),
                changes = listOf(noncanonical),
                hasMore = false
            )
        )

        assertFalse(response.accepted)
        assertTrue(tabletStore.subscriptionUrls.isEmpty())
    }

    private fun synchronize(
        initiator: SubscriptionSyncEngine,
        initiatorStore: TestSubscriptionSyncStore,
        responder: SubscriptionSyncEngine,
        responderStore: TestSubscriptionSyncStore
    ): Int {
        var rounds = 0
        while (true) {
            rounds += 1
            val request = initiator.createRequest(responderStore.localPeerId)
            val response = responder.handleRequest(
                initiatorStore.localPeerId,
                request
            )
            initiator.handleResponse(responderStore.localPeerId, response)
            if (!request.hasMore && !response.hasMore) {
                return rounds
            }
        }
    }

    private fun newStore() = TestSubscriptionSyncStore(newPeerId())

    private fun newPeerId(): String {
        val privateKey = generateKeyPair(KeyType.ED25519).first
        return DeviceIdentity(privateKey).peerId.toBase58()
    }

    companion object {
        private const val SERVICE_ID = 0
        private const val PHONE_URL = "https://example.com/channel/phone"
        private const val TABLET_URL = "https://example.com/channel/tablet"
    }
}
