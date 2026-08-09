package org.schabi.newpipe.sync

import io.libp2p.core.Host
import io.libp2p.core.PeerId
import io.libp2p.core.dsl.host
import io.libp2p.core.multiformats.Multiaddr
import java.net.Inet4Address
import java.net.NetworkInterface
import java.sql.Connection
import java.util.concurrent.TimeUnit

class DesktopSyncService(
    connection: Connection,
    private val deviceName: String
) {
    private val repository = DesktopSyncStateRepository(connection)
    private val security = PairingSecurity()
    private val identity = repository.loadOrCreateIdentity()
    private val pairingProtocol = SyncProtocolBinding(::handlePairingRequest)

    @Volatile
    private var activeHost: Host? = null

    @Synchronized
    fun start() {
        if (activeHost != null) return
        val node = host {
            identity { factory = { this@DesktopSyncService.identity.privateKey } }
            protocols { +pairingProtocol }
            network { listen("/ip4/0.0.0.0/tcp/${repository.getListenPort() ?: 0}") }
        }
        node.start().get(15, TimeUnit.SECONDS)
        val port = node.listenAddresses().asSequence().mapNotNull(::tcpPort).firstOrNull()
            ?: throw PairingException("The desktop synchronization listener has no TCP port")
        repository.saveListenPort(port)
        activeHost = node
    }

    @Synchronized
    fun stop() {
        val node = activeHost ?: return
        activeHost = null
        node.stop().get(10, TimeUnit.SECONDS)
    }

    fun status(): Map<String, Any?> = linkedMapOf(
        "protocol" to SYNC_PROTOCOL_ID,
        "peerId" to identity.peerId.toBase58(),
        "listenAddresses" to advertisedAddresses(),
        "trustedPeers" to repository.getTrustedPeers().map { peer ->
            linkedMapOf(
                "peerId" to peer.peerId,
                "deviceName" to peer.deviceName,
                "lastSyncAtEpochMillis" to peer.lastSyncAtEpochMillis,
                "lastSyncError" to peer.lastSyncError
            )
        },
        "dataSyncEnabled" to false
    )

    fun createPairingCode(): String {
        requireStarted()
        return security.encodeInvitation(
            security.createInvitation(identity, deviceName, advertisedAddresses())
        )
    }

    fun pair(pairingCode: String): Map<String, Any?> {
        val node = requireStarted()
        val invitation = security.decodeAndVerifyInvitation(pairingCode)
        if (invitation.peerId == identity.peerId.toBase58()) {
            throw PairingException("This pairing code belongs to this desktop")
        }
        val remotePeerId = PeerId.fromBase58(invitation.peerId)
        val remoteAddresses = invitation.addresses.map(::Multiaddr).toTypedArray()
        val controller = pairingProtocol.dial(node, remotePeerId, *remoteAddresses)
            .controller.get(20, TimeUnit.SECONDS)
        try {
            controller.sendPairingRequest(
                security.createPairingRequest(
                    identity = identity,
                    deviceName = deviceName,
                    addresses = advertisedAddresses(),
                    invitationToken = invitation.token
                )
            )
            val response = controller.response.get(20, TimeUnit.SECONDS)
            if (!response.accepted) throw PairingException(response.error ?: "Pairing was rejected")
            val peer = security.invitationToTrustedPeer(invitation)
            repository.saveTrustedPeer(peer)
            return linkedMapOf("peerId" to peer.peerId, "deviceName" to peer.deviceName)
        } finally {
            controller.close()
        }
    }

    private fun handlePairingRequest(
        remotePeerId: PeerId,
        request: PairingRequest,
        controller: SyncProtocolController
    ) {
        try {
            repository.saveTrustedPeer(security.verifyAndConsumePairingRequest(request, remotePeerId))
            controller.sendPairingResponse(PairingResponse(accepted = true))
        } catch (error: Exception) {
            controller.sendPairingResponse(
                PairingResponse(accepted = false, error = error.message ?: "Pairing was rejected")
            )
        }
    }

    private fun advertisedAddresses(): List<String> {
        val port = requireNotNull(repository.getListenPort())
        val suffix = "/tcp/$port/p2p/${identity.peerId.toBase58()}"
        val local = NetworkInterface.getNetworkInterfaces().toList().asSequence()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .flatMap { it.inetAddresses.toList().asSequence() }
            .filterIsInstance<Inet4Address>()
            .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            .sortedByDescending { it.isSiteLocalAddress }
            .map { "/ip4/${it.hostAddress}$suffix" }
            .distinct()
            .take(8)
            .toList()
        if (local.isEmpty()) throw PairingException("No local network address is available for pairing")
        return local
    }

    private fun requireStarted(): Host = activeHost ?: throw PairingException("Desktop sync is not running")

    private fun tcpPort(address: Multiaddr): Int? = TCP_PORT.find(address.toString())
        ?.groupValues?.get(1)?.toIntOrNull()

    companion object {
        private val TCP_PORT = Regex("/tcp/(\\d+)")
    }
}
