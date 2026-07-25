/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

// jvm-libp2p's CompletableFuture API is backported by coreLibraryDesugaring.
@file:android.annotation.SuppressLint("NewApi")

package org.schabi.newpipe.sync

import io.libp2p.core.Host
import io.libp2p.core.PeerId
import io.libp2p.core.dsl.host
import io.libp2p.core.multiformats.Multiaddr
import java.util.concurrent.TimeUnit

class Libp2pSyncNode(
    private val stateRepository: SyncStateRepository,
    private val pairingSecurity: PairingSecurity,
    private val deviceName: String,
    private val advertisedAddressProvider: (Host) -> List<String> = { currentHost ->
        currentHost.listenAddresses().map(Multiaddr::toString)
    }
) {
    private val identity = stateRepository.loadOrCreateIdentity()
    private val protocol = SyncProtocolBinding(::handlePairingRequest)
    private val host: Host = host {
        identity {
            factory = { this@Libp2pSyncNode.identity.privateKey }
        }
        protocols {
            +protocol
        }
        network {
            listen(LISTEN_ADDRESS)
        }
    }

    @Volatile
    private var started = false

    val peerId: PeerId
        get() = identity.peerId

    @Synchronized
    fun start() {
        if (started) {
            return
        }
        try {
            host.start().get(START_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            started = true
        } catch (error: Exception) {
            throw PairingException("Could not start secure device synchronization", error)
        }
    }

    @Synchronized
    fun stop() {
        if (!started) {
            return
        }
        try {
            host.stop().get(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } finally {
            started = false
        }
    }

    fun advertisedAddresses(): List<String> {
        checkStarted()
        return advertisedAddressProvider(host)
    }

    fun createPairingCode(): String {
        checkStarted()
        val invitation = pairingSecurity.createInvitation(
            identity = identity,
            deviceName = deviceName,
            addresses = advertisedAddresses()
        )
        return pairingSecurity.encodeInvitation(invitation)
    }

    @Throws(PairingException::class)
    fun pair(pairingCode: String): TrustedPeer {
        checkStarted()
        val invitation = pairingSecurity.decodeAndVerifyInvitation(pairingCode)
        if (invitation.peerId == peerId.toBase58()) {
            throw PairingException("This pairing code belongs to this device")
        }

        val remotePeerId = try {
            PeerId.fromBase58(invitation.peerId)
        } catch (error: Exception) {
            throw PairingException("The remote PeerID is invalid", error)
        }
        val remoteAddresses = try {
            invitation.addresses.map(::Multiaddr).toTypedArray()
        } catch (error: Exception) {
            throw PairingException("The remote network addresses are invalid", error)
        }

        val streamPromise = protocol.dial(host, remotePeerId, *remoteAddresses)
        val controller = try {
            streamPromise.controller.get(PAIR_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (error: Exception) {
            throw PairingException("Could not reach the other device", error)
        }

        try {
            val request = pairingSecurity.createPairingRequest(
                identity = identity,
                deviceName = deviceName,
                addresses = advertisedAddresses(),
                invitationToken = invitation.token
            )
            controller.sendPairingRequest(request)
            val response = controller.response.get(PAIR_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!response.accepted) {
                throw PairingException(response.error ?: "The other device rejected pairing")
            }

            val trustedPeer = pairingSecurity.invitationToTrustedPeer(invitation)
            stateRepository.saveTrustedPeer(trustedPeer)
            return trustedPeer
        } catch (error: PairingException) {
            throw error
        } catch (error: Exception) {
            throw PairingException("Secure pairing failed", error)
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
            val trustedPeer = pairingSecurity.verifyAndConsumePairingRequest(
                request,
                remotePeerId
            )
            stateRepository.saveTrustedPeer(trustedPeer)
            controller.sendPairingResponse(PairingResponse(accepted = true))
        } catch (error: Exception) {
            controller.sendPairingResponse(
                PairingResponse(
                    accepted = false,
                    error = error.message ?: "The pairing request was rejected"
                )
            )
        }
    }

    private fun checkStarted() {
        if (!started) {
            throw PairingException("Device synchronization is not running")
        }
    }

    companion object {
        private const val LISTEN_ADDRESS = "/ip4/0.0.0.0/tcp/0"
        private const val START_TIMEOUT_SECONDS = 20L
        private const val STOP_TIMEOUT_SECONDS = 10L
        private const val PAIR_TIMEOUT_SECONDS = 20L
    }
}
