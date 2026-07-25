/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import io.libp2p.core.Host
import io.libp2p.core.crypto.KeyType
import io.libp2p.core.crypto.generateKeyPair
import org.junit.Assert.assertEquals
import org.junit.Test

class Libp2pSyncNodeTest {
    @Test
    fun `two nodes pair over a Noise authenticated stream`() {
        val tabletState = InMemorySyncStateRepository()
        val phoneState = InMemorySyncStateRepository()
        val tablet = Libp2pSyncNode(
            tabletState,
            PairingSecurity(),
            "Test tablet",
            ::loopbackAddresses
        )
        val phone = Libp2pSyncNode(
            phoneState,
            PairingSecurity(),
            "Test phone",
            ::loopbackAddresses
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

    private fun loopbackAddresses(host: Host): List<String> {
        return host.listenAddresses().map { address ->
            address.toString().replace("/ip4/0.0.0.0/", "/ip4/127.0.0.1/")
        }
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
        override fun clearTrustedPeers() {
            peers.clear()
        }
    }
}
