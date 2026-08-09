package org.schabi.newpipe.sync

import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class DesktopSyncStateRepositoryTest {
    @Test
    fun `identity and trusted peers persist in sqlite`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("CREATE TABLE sync_state (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
                statement.executeUpdate(
                    """CREATE TABLE trusted_peers (peer_id TEXT PRIMARY KEY, public_key TEXT NOT NULL,
                        device_name TEXT NOT NULL, addresses_json TEXT NOT NULL, paired_at INTEGER NOT NULL,
                        last_sync_at INTEGER, last_sync_error TEXT)"""
                )
            }
            val repository = DesktopSyncStateRepository(connection)
            val first = repository.loadOrCreateIdentity()
            val second = repository.loadOrCreateIdentity()
            assertEquals(first.peerId, second.peerId)

            val peer = TrustedPeer("peer", "key", "Phone", listOf("/ip4/192.168.1.2/tcp/1234"), 1L)
            repository.saveTrustedPeer(peer)
            assertEquals(listOf(peer), repository.getTrustedPeers())
            assertNotEquals(first.peerId.toBase58(), peer.peerId)
        }
    }
}
