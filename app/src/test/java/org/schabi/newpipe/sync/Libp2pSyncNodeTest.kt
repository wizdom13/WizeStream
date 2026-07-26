/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import io.libp2p.core.Host
import io.libp2p.core.crypto.KeyType
import io.libp2p.core.crypto.generateKeyPair
import java.net.ServerSocket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Libp2pSyncNodeTest {
    @Test
    fun `default listener advertises and reports its selected dynamic port`() {
        val state = InMemorySyncStateRepository()
        val security = PairingSecurity()
        var selectedPort: Int? = null
        val node = Libp2pSyncNode(
            stateRepository = state,
            pairingSecurity = security,
            deviceName = "Test phone",
            advertisedAddressProvider = ::loopbackAddresses,
            onListenPortSelected = { selectedPort = it }
        )

        try {
            node.start()

            val invitation = security.decodeAndVerifyInvitation(node.createPairingCode())
            assertTrue(requireNotNull(selectedPort) > 0)
            assertTrue(invitation.addresses.none { "/tcp/0/" in it })
            assertEquals(node.advertisedAddresses(), invitation.addresses)
        } finally {
            node.stop()
        }
    }

    @Test
    fun `pairing can recover from an occupied listener port`() {
        ServerSocket(0).use { occupiedSocket ->
            val occupiedPort = occupiedSocket.localPort
            var selectedPort: Int? = null
            val node = Libp2pSyncNode(
                stateRepository = InMemorySyncStateRepository(),
                pairingSecurity = PairingSecurity(),
                deviceName = "Test phone",
                advertisedAddressProvider = ::loopbackAddresses,
                listenAddress = "/ip4/127.0.0.1/tcp/$occupiedPort",
                onListenPortSelected = { selectedPort = it }
            )

            try {
                val error = assertThrows(PairingException::class.java) {
                    node.start()
                }
                assertTrue(
                    error.message.orEmpty()
                        .startsWith("Could not start secure device synchronization:")
                )

                node.start(allowEphemeralFallback = true)

                assertTrue(requireNotNull(selectedPort) > 0)
                assertNotEquals(occupiedPort, selectedPort)
                assertTrue(node.advertisedAddresses().none { "/tcp/0/" in it })
            } finally {
                node.stop()
            }
        }
    }

    @Test
    fun `two nodes pair over a Noise authenticated stream`() {
        val tabletState = InMemorySyncStateRepository()
        val phoneState = InMemorySyncStateRepository()
        val tablet = Libp2pSyncNode(
            tabletState,
            PairingSecurity(),
            "Test tablet",
            ::loopbackAddresses,
            listenAddress = TEST_LISTEN_ADDRESS
        )
        val phone = Libp2pSyncNode(
            phoneState,
            PairingSecurity(),
            "Test phone",
            ::loopbackAddresses,
            listenAddress = TEST_LISTEN_ADDRESS
        )

        try {
            tablet.start()
            phone.start()

            val pairedTablet = phone.pair(tablet.createPairingCode())

            assertEquals("Test tablet", pairedTablet.deviceName)
            assertEquals(
                listOf("Test phone"),
                tabletState.getTrustedPeers().map(TrustedPeer::deviceName)
            )
            assertEquals(
                listOf("Test tablet"),
                phoneState.getTrustedPeers().map(TrustedPeer::deviceName)
            )
        } finally {
            phone.stop()
            tablet.stop()
        }
    }

    @Test
    fun `paired nodes exchange subscriptions over a Noise authenticated stream`() {
        val tabletState = InMemorySyncStateRepository()
        val phoneState = InMemorySyncStateRepository()
        val tabletSubscriptions = TestSubscriptionSyncStore(
            tabletState.loadOrCreateIdentity().peerId.toBase58()
        )
        val phoneSubscriptions = TestSubscriptionSyncStore(
            phoneState.loadOrCreateIdentity().peerId.toBase58()
        )
        val tablet = Libp2pSyncNode(
            tabletState,
            PairingSecurity(),
            "Test tablet",
            ::loopbackAddresses,
            SubscriptionSyncEngine(tabletSubscriptions),
            TEST_LISTEN_ADDRESS
        )
        val phone = Libp2pSyncNode(
            phoneState,
            PairingSecurity(),
            "Test phone",
            ::loopbackAddresses,
            SubscriptionSyncEngine(phoneSubscriptions),
            TEST_LISTEN_ADDRESS
        )
        tabletSubscriptions.add(0, TABLET_SUBSCRIPTION_URL)
        phoneSubscriptions.add(0, PHONE_SUBSCRIPTION_URL)

        try {
            tablet.start()
            phone.start()
            val trustedTablet = phone.pair(tablet.createPairingCode())

            val result = phone.syncSubscriptions(trustedTablet)

            assertEquals(1, result.sentChanges)
            assertEquals(1, result.receivedChanges)
            assertEquals(
                setOf(TABLET_SUBSCRIPTION_URL, PHONE_SUBSCRIPTION_URL),
                tabletSubscriptions.subscriptionUrls
            )
            assertEquals(
                tabletSubscriptions.subscriptionUrls,
                phoneSubscriptions.subscriptionUrls
            )
        } finally {
            phone.stop()
            tablet.stop()
        }
    }

    @Test
    fun `paired nodes exchange playlists over a Noise authenticated stream`() {
        val tabletState = InMemorySyncStateRepository()
        val phoneState = InMemorySyncStateRepository()
        val tabletPlaylists = TestPlaylistSyncStore(
            tabletState.loadOrCreateIdentity().peerId.toBase58()
        )
        val phonePlaylists = TestPlaylistSyncStore(
            phoneState.loadOrCreateIdentity().peerId.toBase58()
        )
        val tablet = Libp2pSyncNode(
            stateRepository = tabletState,
            pairingSecurity = PairingSecurity(),
            deviceName = "Test tablet",
            advertisedAddressProvider = ::loopbackAddresses,
            listenAddress = TEST_LISTEN_ADDRESS,
            playlistSyncEngine = PlaylistSyncEngine(tabletPlaylists)
        )
        val phone = Libp2pSyncNode(
            stateRepository = phoneState,
            pairingSecurity = PairingSecurity(),
            deviceName = "Test phone",
            advertisedAddressProvider = ::loopbackAddresses,
            listenAddress = TEST_LISTEN_ADDRESS,
            playlistSyncEngine = PlaylistSyncEngine(phonePlaylists)
        )
        val tabletPlaylistId = tabletPlaylists.createLocalPlaylist(
            "Tablet",
            listOf(TABLET_PLAYLIST_URL)
        )
        phonePlaylists.bookmarkRemotePlaylist(
            0,
            PHONE_PLAYLIST_URL,
            "Phone remote"
        )

        try {
            tablet.start()
            phone.start()
            val trustedTablet = phone.pair(tablet.createPairingCode())

            val result = phone.syncPlaylists(trustedTablet)

            assertEquals(1, result.changedPlaylists)
            assertEquals(
                listOf(TABLET_PLAYLIST_URL),
                phonePlaylists.playlistUrls(tabletPlaylistId)
            )
            assertEquals(
                setOf(PHONE_PLAYLIST_URL),
                tabletPlaylists.remotePlaylistUrls
            )
        } finally {
            phone.stop()
            tablet.stop()
        }
    }

    @Test
    fun `paired nodes exchange watch and search histories over Noise streams`() {
        val tabletState = InMemorySyncStateRepository()
        val phoneState = InMemorySyncStateRepository()
        val tabletHistory = TestHistorySyncStore(
            tabletState.loadOrCreateIdentity().peerId.toBase58()
        )
        val phoneHistory = TestHistorySyncStore(
            phoneState.loadOrCreateIdentity().peerId.toBase58()
        )
        tabletHistory.registerStream(HISTORY_STREAM_ID, HISTORY_STREAM_URL)
        phoneHistory.registerStream(HISTORY_STREAM_ID, HISTORY_STREAM_URL)
        tabletHistory.recordSearch(0, "tablet search", 2_000)
        phoneHistory.recordProgress(HISTORY_STREAM_ID, 42_000, 1_000)
        val tablet = Libp2pSyncNode(
            stateRepository = tabletState,
            pairingSecurity = PairingSecurity(),
            deviceName = "Test tablet",
            advertisedAddressProvider = ::loopbackAddresses,
            listenAddress = TEST_LISTEN_ADDRESS,
            historySyncEngine = HistorySyncEngine(tabletHistory)
        )
        val phone = Libp2pSyncNode(
            stateRepository = phoneState,
            pairingSecurity = PairingSecurity(),
            deviceName = "Test phone",
            advertisedAddressProvider = ::loopbackAddresses,
            listenAddress = TEST_LISTEN_ADDRESS,
            historySyncEngine = HistorySyncEngine(phoneHistory)
        )

        try {
            tablet.start()
            phone.start()
            val trustedTablet = phone.pair(tablet.createPairingCode())

            val watchResult = phone.syncHistory(
                trustedTablet,
                HistorySyncCategory.WATCH
            )
            val searchResult = phone.syncHistory(
                trustedTablet,
                HistorySyncCategory.SEARCH
            )

            assertEquals(1, watchResult.sentChanges)
            assertEquals(0, watchResult.receivedChanges)
            assertEquals(
                42_000L,
                tabletHistory.progressMillis(HISTORY_STREAM_URL)
            )
            assertEquals(0, searchResult.sentChanges)
            assertEquals(1, searchResult.receivedChanges)
            assertEquals(listOf("tablet search"), phoneHistory.searchQueries)
        } finally {
            phone.stop()
            tablet.stop()
        }
    }

    private fun loopbackAddresses(host: Host): List<String> {
        return host.listenAddresses().map { address ->
            address.toString().replace("/ip4/0.0.0.0/", "/ip4/127.0.0.1/")
        }
    }

    companion object {
        private const val TEST_LISTEN_ADDRESS = "/ip4/127.0.0.1/tcp/0"
        private const val TABLET_SUBSCRIPTION_URL =
            "https://example.com/channel/tablet"
        private const val PHONE_SUBSCRIPTION_URL =
            "https://example.com/channel/phone"
        private const val TABLET_PLAYLIST_URL =
            "https://example.com/watch/tablet"
        private const val PHONE_PLAYLIST_URL =
            "https://example.com/playlist/phone"
        private const val HISTORY_STREAM_ID = 1L
        private const val HISTORY_STREAM_URL =
            "https://example.com/watch/history"
    }

    private class InMemorySyncStateRepository : SyncStateRepository {
        private val identity = DeviceIdentity(generateKeyPair(KeyType.ED25519).first)
        private val peers = linkedMapOf<String, TrustedPeer>()

        override fun loadOrCreateIdentity() = identity

        @Synchronized
        override fun getTrustedPeers() = peers.values.toList()

        @Synchronized
        override fun saveTrustedPeer(peer: TrustedPeer) {
            peers[peer.peerId] = peer
        }

        @Synchronized
        override fun updateTrustedPeerSyncStatus(
            peerId: String,
            syncedAtEpochMillis: Long?,
            error: String?
        ) {
            peers[peerId]?.let { peer ->
                peers[peerId] = peer.copy(
                    lastSyncAtEpochMillis = syncedAtEpochMillis
                        ?: peer.lastSyncAtEpochMillis,
                    lastSyncError = error
                )
            }
        }

        @Synchronized
        override fun clearTrustedPeers() {
            peers.clear()
        }
    }
}
