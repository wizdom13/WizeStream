package org.schabi.newpipe.sync

import io.libp2p.core.crypto.KeyType
import io.libp2p.core.crypto.generateKeyPair
import io.libp2p.core.crypto.unmarshalPrivateKey
import java.sql.Connection
import java.util.Base64
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class DesktopSyncStateRepository(
    private val connection: Connection
) : SyncStateRepository {
    private val lock = Any()

    override fun loadOrCreateIdentity(): DeviceIdentity = synchronized(lock) {
        val stored = getState(PRIVATE_KEY)
        if (stored != null) {
            return@synchronized DeviceIdentity(unmarshalPrivateKey(Base64.getDecoder().decode(stored)))
        }
        val identity = DeviceIdentity(generateKeyPair(KeyType.ED25519).first)
        putState(PRIVATE_KEY, Base64.getEncoder().encodeToString(identity.privateKey.bytes()))
        identity
    }

    override fun getTrustedPeers(): List<TrustedPeer> = synchronized(lock) {
        connection.prepareStatement(
            """SELECT peer_id, public_key, device_name, addresses_json, paired_at,
                last_sync_at, last_sync_error FROM trusted_peers ORDER BY lower(device_name)"""
        ).use { statement ->
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        add(
                            TrustedPeer(
                                peerId = rows.getString(1),
                                publicKey = rows.getString(2),
                                deviceName = rows.getString(3),
                                addresses = JSON.decodeFromString(rows.getString(4)),
                                pairedAtEpochMillis = rows.getLong(5),
                                lastSyncAtEpochMillis = rows.getLong(6).takeUnless { rows.wasNull() },
                                lastSyncError = rows.getString(7)
                            )
                        )
                    }
                }
            }
        }
    }

    override fun getListenPort(): Int? = getState(LISTEN_PORT)?.toIntOrNull()

    override fun saveListenPort(port: Int) {
        require(port in 1..65_535)
        putState(LISTEN_PORT, port.toString())
    }

    override fun saveTrustedPeer(peer: TrustedPeer) = synchronized(lock) {
        connection.prepareStatement(
            """INSERT INTO trusted_peers(peer_id, public_key, device_name, addresses_json,
                paired_at, last_sync_at, last_sync_error) VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(peer_id) DO UPDATE SET public_key=excluded.public_key,
                device_name=excluded.device_name, addresses_json=excluded.addresses_json"""
        ).use { statement ->
            statement.setString(1, peer.peerId)
            statement.setString(2, peer.publicKey)
            statement.setString(3, peer.deviceName)
            statement.setString(4, JSON.encodeToString(peer.addresses))
            statement.setLong(5, peer.pairedAtEpochMillis)
            statement.setObject(6, peer.lastSyncAtEpochMillis)
            statement.setString(7, peer.lastSyncError)
            statement.executeUpdate()
        }
    }

    override fun updateTrustedPeerSyncStatus(peerId: String, syncedAtEpochMillis: Long?, error: String?) {
        synchronized(lock) {
            connection.prepareStatement(
                "UPDATE trusted_peers SET last_sync_at=COALESCE(?, last_sync_at), last_sync_error=? WHERE peer_id=?"
            ).use { statement ->
                statement.setObject(1, syncedAtEpochMillis)
                statement.setString(2, error?.take(500))
                statement.setString(3, peerId)
                statement.executeUpdate()
            }
        }
    }

    override fun clearTrustedPeers() {
        synchronized(lock) { connection.createStatement().use { it.executeUpdate("DELETE FROM trusted_peers") } }
    }

    private fun getState(key: String): String? = synchronized(lock) {
        connection.prepareStatement("SELECT value FROM sync_state WHERE key=?").use { statement ->
            statement.setString(1, key)
            statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
        }
    }

    private fun putState(key: String, value: String) = synchronized(lock) {
        connection.prepareStatement(
            "INSERT INTO sync_state(key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value=excluded.value"
        ).use { statement ->
            statement.setString(1, key)
            statement.setString(2, value)
            statement.executeUpdate()
        }
    }

    companion object {
        private const val PRIVATE_KEY = "sync_private_key_v1"
        private const val LISTEN_PORT = "sync_listen_port_v1"
        private val JSON = Json { encodeDefaults = true; ignoreUnknownKeys = false }
    }
}
