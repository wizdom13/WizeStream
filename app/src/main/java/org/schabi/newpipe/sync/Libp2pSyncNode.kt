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
import java.net.BindException
import java.util.concurrent.TimeUnit

class Libp2pSyncNode(
    private val stateRepository: SyncStateRepository,
    private val pairingSecurity: PairingSecurity,
    private val deviceName: String,
    private val advertisedAddressProvider: (Host) -> List<String> = { currentHost ->
        currentHost.listenAddresses().map(Multiaddr::toString)
    },
    private val subscriptionSyncEngine: SubscriptionSyncEngine? = null,
    private val listenAddress: String = LISTEN_ADDRESS,
    private val playlistSyncEngine: PlaylistSyncEngine? = null,
    private val onListenPortSelected: (Int) -> Unit = {},
    private val historySyncEngine: HistorySyncEngine? = null,
    private val structuredPreferenceSyncEngine: StructuredPreferenceSyncEngine? = null,
    private val onTrustedPeerSaved: () -> Unit = {},
    private val peerAddressResolver: (TrustedPeer) -> List<String> = { emptyList() }
) {
    private val identity = stateRepository.loadOrCreateIdentity()
    private val pairingProtocol = SyncProtocolBinding(::handlePairingRequest)
    private val subscriptionProtocol = SubscriptionSyncProtocolBinding(
        ::handleSubscriptionSyncRequest
    )
    private val playlistProtocol = PlaylistSyncProtocolBinding(
        ::handlePlaylistSyncRequest
    )
    private val historyProtocol = HistorySyncProtocolBinding(
        ::handleHistorySyncRequest
    )
    private val structuredPreferenceProtocol = StructuredPreferenceSyncProtocolBinding(
        ::handleStructuredPreferenceSyncRequest
    )
    private var preferredListenAddress = listenAddress

    @Volatile
    private var activeHost: Host? = null

    @Volatile
    private var started = false

    val peerId: PeerId
        get() = identity.peerId

    private fun createHost(address: String): Host = host {
        identity {
            factory = { this@Libp2pSyncNode.identity.privateKey }
        }
        protocols {
            +pairingProtocol
            +subscriptionProtocol
            +playlistProtocol
            +historyProtocol
            +structuredPreferenceProtocol
        }
        network {
            listen(address)
        }
    }

    @Synchronized
    fun start(allowEphemeralFallback: Boolean = false) {
        if (started) {
            return
        }

        val addresses = buildList {
            add(preferredListenAddress)
            if (allowEphemeralFallback) {
                add(ephemeralListenAddress(preferredListenAddress))
            }
        }.distinct()
        var lastError: Exception? = null

        for ((index, address) in addresses.withIndex()) {
            val candidate = createHost(address)
            try {
                candidate.start().get(START_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                val selectedPort = candidate.listenAddresses()
                    .asSequence()
                    .mapNotNull(::tcpPortFromMultiaddress)
                    .firstOrNull { it in MIN_LISTEN_PORT..MAX_LISTEN_PORT }
                    ?: throw PairingException(
                        "The synchronization listener has no usable TCP address"
                    )
                onListenPortSelected(selectedPort)
                preferredListenAddress = listenAddressWithPort(address, selectedPort)
                activeHost = candidate
                started = true
                return
            } catch (error: Exception) {
                lastError = error
                runCatching {
                    candidate.stop().get(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                }
                val canRetry = index == 0 &&
                    addresses.size > 1 &&
                    isAddressAlreadyInUse(error)
                if (!canRetry) {
                    break
                }
            }
        }

        val error = requireNotNull(lastError)
        throw PairingException(startFailureMessage(error), error)
    }

    @Synchronized
    fun stop() {
        val currentHost = activeHost
        if (!started || currentHost == null) {
            return
        }
        try {
            currentHost.stop().get(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } finally {
            activeHost = null
            started = false
        }
    }

    fun advertisedAddresses(): List<String> {
        return advertisedAddressProvider(requireStartedHost())
    }

    fun refreshPeerAddresses(peer: TrustedPeer): TrustedPeer? {
        ensureTrusted(peer.peerId)
        val discoveredAddresses = runCatching {
            peerAddressResolver(peer)
        }.getOrDefault(emptyList())
        if (discoveredAddresses.isEmpty()) {
            return null
        }
        val addresses = mergeTrustedPeerAddresses(
            peer.peerId,
            discoveredAddresses,
            peer.addresses
        )
        return peer.copy(addresses = addresses)
    }

    fun createPairingCode(): String {
        requireStartedHost()
        val invitation = pairingSecurity.createInvitation(
            identity = identity,
            deviceName = deviceName,
            addresses = advertisedAddresses()
        )
        return pairingSecurity.encodeInvitation(invitation)
    }

    @Throws(PairingException::class)
    fun pair(pairingCode: String): TrustedPeer {
        val currentHost = requireStartedHost()
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

        val streamPromise = pairingProtocol.dial(currentHost, remotePeerId, *remoteAddresses)
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
            runCatching(onTrustedPeerSaved)
            return trustedPeer
        } catch (error: PairingException) {
            throw error
        } catch (error: Exception) {
            throw PairingException("Secure pairing failed", error)
        } finally {
            controller.close()
        }
    }

    @Throws(SubscriptionSyncException::class)
    fun syncSubscriptions(
        peer: TrustedPeer,
        recordStatus: Boolean = true
    ): SubscriptionSyncResult {
        val currentHost = requireStartedHost()
        val engine = subscriptionSyncEngine
            ?: throw SubscriptionSyncException("Subscription synchronization is unavailable")
        ensureTrusted(peer.peerId)
        val remotePeerId = parsePeerId(peer.peerId)
        val remoteAddresses = parseAddresses(remotePeerId, peer.addresses)

        var sentChanges = 0
        var receivedChanges = 0
        var addedSubscriptions = 0
        var removedSubscriptions = 0
        var rounds = 0

        try {
            while (true) {
                if (rounds >= MAX_SYNC_ROUNDS) {
                    throw SubscriptionSyncException(
                        "Synchronization needs too many batches; run it again to continue"
                    )
                }
                rounds += 1
                val request = engine.createRequest(peer.peerId)
                val streamPromise = subscriptionProtocol.dial(
                    currentHost,
                    remotePeerId,
                    *remoteAddresses
                )
                val controller = try {
                    streamPromise.controller.get(
                        SYNC_TIMEOUT_SECONDS,
                        TimeUnit.SECONDS
                    )
                } catch (error: Exception) {
                    throw SubscriptionSyncException(
                        "Could not reach ${peer.deviceName}",
                        error
                    )
                }

                val response = try {
                    controller.sendRequest(request)
                    controller.response.get(SYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                } catch (error: Exception) {
                    throw SubscriptionSyncException(
                        "Subscription synchronization with ${peer.deviceName} failed",
                        error
                    )
                } finally {
                    controller.close()
                }

                val applied = engine.handleResponse(peer.peerId, response)
                sentChanges += request.changes.size
                receivedChanges += response.changes.size
                addedSubscriptions += applied.addedSubscriptions
                removedSubscriptions += applied.removedSubscriptions

                if (!request.hasMore && !response.hasMore) {
                    break
                }
            }

            recordSyncStatus(
                recordStatus,
                peer.peerId,
                System.currentTimeMillis(),
                null
            )
            rememberPeerAddresses(peer)
            return SubscriptionSyncResult(
                peer = peer,
                sentChanges = sentChanges,
                receivedChanges = receivedChanges,
                addedSubscriptions = addedSubscriptions,
                removedSubscriptions = removedSubscriptions,
                rounds = rounds
            )
        } catch (error: SubscriptionSyncException) {
            recordSyncStatus(
                recordStatus,
                peer.peerId,
                null,
                error.message ?: "Subscription synchronization failed"
            )
            throw error
        } catch (error: Exception) {
            val wrapped = SubscriptionSyncException(
                "Subscription synchronization with ${peer.deviceName} failed",
                error
            )
            recordSyncStatus(
                recordStatus,
                peer.peerId,
                null,
                wrapped.message
            )
            throw wrapped
        }
    }

    @Throws(PlaylistSyncException::class)
    fun syncPlaylists(
        peer: TrustedPeer,
        recordStatus: Boolean = true
    ): PlaylistSyncResult {
        val currentHost = requireStartedHost()
        val engine = playlistSyncEngine
            ?: throw PlaylistSyncException("Playlist synchronization is unavailable")
        ensureTrusted(peer.peerId)
        val remotePeerId = parsePeerId(peer.peerId)
        val remoteAddresses = parseAddresses(remotePeerId, peer.addresses)

        var sentChanges = 0
        var receivedChanges = 0
        var changedPlaylists = 0
        var rounds = 0

        try {
            while (true) {
                if (rounds >= MAX_SYNC_ROUNDS) {
                    throw PlaylistSyncException(
                        "Playlist synchronization needs too many batches; run it again to continue"
                    )
                }
                rounds += 1
                val request = engine.createRequest(peer.peerId)
                val streamPromise = playlistProtocol.dial(
                    currentHost,
                    remotePeerId,
                    *remoteAddresses
                )
                val controller = try {
                    streamPromise.controller.get(
                        SYNC_TIMEOUT_SECONDS,
                        TimeUnit.SECONDS
                    )
                } catch (error: Exception) {
                    throw PlaylistSyncException(
                        "Could not reach ${peer.deviceName}",
                        error
                    )
                }

                val response = try {
                    controller.sendRequest(request)
                    controller.response.get(SYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                } catch (error: Exception) {
                    throw PlaylistSyncException(
                        "Playlist synchronization with ${peer.deviceName} failed",
                        error
                    )
                } finally {
                    controller.close()
                }

                val applied = engine.handleResponse(peer.peerId, response)
                sentChanges += request.changes.size
                receivedChanges += response.changes.size
                changedPlaylists += applied.changedPlaylists

                if (!request.hasMore && !response.hasMore) {
                    break
                }
            }

            recordSyncStatus(
                recordStatus,
                peer.peerId,
                System.currentTimeMillis(),
                null
            )
            rememberPeerAddresses(peer)
            return PlaylistSyncResult(
                peer = peer,
                sentChanges = sentChanges,
                receivedChanges = receivedChanges,
                changedPlaylists = changedPlaylists,
                rounds = rounds
            )
        } catch (error: PlaylistSyncException) {
            recordSyncStatus(
                recordStatus,
                peer.peerId,
                null,
                error.message ?: "Playlist synchronization failed"
            )
            throw error
        } catch (error: Exception) {
            val wrapped = PlaylistSyncException(
                "Playlist synchronization with ${peer.deviceName} failed",
                error
            )
            recordSyncStatus(
                recordStatus,
                peer.peerId,
                null,
                wrapped.message
            )
            throw wrapped
        }
    }

    @Throws(HistorySyncException::class)
    fun syncHistory(
        peer: TrustedPeer,
        category: HistorySyncCategory,
        recordStatus: Boolean = true
    ): HistorySyncResult {
        val currentHost = requireStartedHost()
        val engine = historySyncEngine
            ?: throw HistorySyncException("History synchronization is unavailable")
        ensureTrusted(peer.peerId)
        val remotePeerId = parsePeerId(peer.peerId)
        val remoteAddresses = parseAddresses(remotePeerId, peer.addresses)

        var sentChanges = 0
        var receivedChanges = 0
        var affectedRecords = 0
        var rounds = 0

        try {
            while (true) {
                if (rounds >= MAX_SYNC_ROUNDS) {
                    throw HistorySyncException(
                        "History synchronization needs too many batches; run it again to continue"
                    )
                }
                rounds += 1
                val request = engine.createRequest(peer.peerId, category)
                val streamPromise = historyProtocol.dial(
                    currentHost,
                    remotePeerId,
                    *remoteAddresses
                )
                val controller = try {
                    streamPromise.controller.get(
                        SYNC_TIMEOUT_SECONDS,
                        TimeUnit.SECONDS
                    )
                } catch (error: Exception) {
                    throw HistorySyncException(
                        "Could not reach ${peer.deviceName}",
                        error
                    )
                }

                val response = try {
                    controller.sendRequest(request)
                    controller.response.get(SYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                } catch (error: Exception) {
                    throw HistorySyncException(
                        "History synchronization with ${peer.deviceName} failed",
                        error
                    )
                } finally {
                    controller.close()
                }

                val applied = engine.handleResponse(
                    peer.peerId,
                    category,
                    response
                )
                sentChanges += request.changes.size
                receivedChanges += response.changes.size
                affectedRecords += applied.affectedRecords

                if (!request.hasMore && !response.hasMore) {
                    break
                }
            }

            recordSyncStatus(
                recordStatus,
                peer.peerId,
                System.currentTimeMillis(),
                null
            )
            rememberPeerAddresses(peer)
            return HistorySyncResult(
                peer = peer,
                category = category,
                sentChanges = sentChanges,
                receivedChanges = receivedChanges,
                affectedRecords = affectedRecords,
                rounds = rounds
            )
        } catch (error: HistorySyncException) {
            recordSyncStatus(
                recordStatus,
                peer.peerId,
                null,
                error.message ?: "History synchronization failed"
            )
            throw error
        } catch (error: Exception) {
            val wrapped = HistorySyncException(
                "History synchronization with ${peer.deviceName} failed",
                error
            )
            recordSyncStatus(
                recordStatus,
                peer.peerId,
                null,
                wrapped.message
            )
            throw wrapped
        }
    }

    @Throws(StructuredPreferenceSyncException::class)
    fun syncStructuredPreferences(
        peer: TrustedPeer,
        category: StructuredPreferenceCategory,
        recordStatus: Boolean = true
    ): StructuredPreferenceSyncResult {
        val currentHost = requireStartedHost()
        val engine = structuredPreferenceSyncEngine
            ?: throw StructuredPreferenceSyncException(
                "Structured preference synchronization is unavailable"
            )
        ensureTrusted(peer.peerId)
        val remotePeerId = parsePeerId(peer.peerId)
        val remoteAddresses = parseAddresses(remotePeerId, peer.addresses)

        var sentChanges = 0
        var receivedChanges = 0
        var affectedRecords = 0
        var rounds = 0

        try {
            while (true) {
                if (rounds >= MAX_SYNC_ROUNDS) {
                    throw StructuredPreferenceSyncException(
                        "Structured preference synchronization needs too many batches; " +
                            "run it again to continue"
                    )
                }
                rounds += 1
                val request = engine.createRequest(peer.peerId, category)
                val streamPromise = structuredPreferenceProtocol.dial(
                    currentHost,
                    remotePeerId,
                    *remoteAddresses
                )
                val controller = try {
                    streamPromise.controller.get(
                        SYNC_TIMEOUT_SECONDS,
                        TimeUnit.SECONDS
                    )
                } catch (error: Exception) {
                    throw StructuredPreferenceSyncException(
                        "Could not reach ${peer.deviceName}",
                        error
                    )
                }

                val response = try {
                    controller.sendRequest(request)
                    controller.response.get(SYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                } catch (error: Exception) {
                    throw StructuredPreferenceSyncException(
                        "Structured preference synchronization with " +
                            "${peer.deviceName} failed",
                        error
                    )
                } finally {
                    controller.close()
                }

                val applied = engine.handleResponse(
                    peer.peerId,
                    category,
                    response
                )
                sentChanges += request.changes.size
                receivedChanges += response.changes.size
                affectedRecords += applied.affectedRecords

                if (!request.hasMore && !response.hasMore) {
                    break
                }
            }

            recordSyncStatus(
                recordStatus,
                peer.peerId,
                System.currentTimeMillis(),
                null
            )
            rememberPeerAddresses(peer)
            return StructuredPreferenceSyncResult(
                peer = peer,
                category = category,
                sentChanges = sentChanges,
                receivedChanges = receivedChanges,
                affectedRecords = affectedRecords,
                rounds = rounds
            )
        } catch (error: StructuredPreferenceSyncException) {
            recordSyncStatus(
                recordStatus,
                peer.peerId,
                null,
                error.message ?: "Structured preference synchronization failed"
            )
            throw error
        } catch (error: Exception) {
            val wrapped = StructuredPreferenceSyncException(
                "Structured preference synchronization with ${peer.deviceName} failed",
                error
            )
            recordSyncStatus(
                recordStatus,
                peer.peerId,
                null,
                wrapped.message
            )
            throw wrapped
        }
    }

    private fun recordSyncStatus(
        enabled: Boolean,
        peerId: String,
        syncedAtEpochMillis: Long?,
        error: String?
    ) {
        if (enabled) {
            stateRepository.updateTrustedPeerSyncStatus(
                peerId,
                syncedAtEpochMillis,
                error
            )
        }
    }

    private fun rememberPeerAddresses(peer: TrustedPeer) {
        stateRepository.saveTrustedPeer(peer)
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
            runCatching(onTrustedPeerSaved)
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

    private fun handleSubscriptionSyncRequest(
        remotePeerId: PeerId,
        request: SubscriptionSyncRequest,
        controller: SubscriptionSyncProtocolController
    ) {
        val peerIdValue = remotePeerId.toBase58()
        val response = try {
            ensureTrusted(peerIdValue)
            val engine = subscriptionSyncEngine
                ?: throw SubscriptionSyncException(
                    "Subscription synchronization is unavailable"
                )
            engine.handleRequest(peerIdValue, request)
        } catch (error: Exception) {
            SubscriptionSyncResponse(
                accepted = false,
                error = (
                    error.message ?: "The synchronization request was rejected"
                    ).take(MAX_SYNC_ERROR_LENGTH)
            )
        }
        controller.sendResponse(response)
        stateRepository.updateTrustedPeerSyncStatus(
            peerIdValue,
            if (response.accepted) System.currentTimeMillis() else null,
            response.error
        )
    }

    private fun handlePlaylistSyncRequest(
        remotePeerId: PeerId,
        request: PlaylistSyncRequest,
        controller: PlaylistSyncProtocolController
    ) {
        val peerIdValue = remotePeerId.toBase58()
        val response = try {
            ensureTrusted(peerIdValue)
            val engine = playlistSyncEngine
                ?: throw PlaylistSyncException(
                    "Playlist synchronization is unavailable"
                )
            engine.handleRequest(peerIdValue, request)
        } catch (error: Exception) {
            PlaylistSyncResponse(
                accepted = false,
                error = (
                    error.message ?: "The playlist synchronization request was rejected"
                    ).take(MAX_SYNC_ERROR_LENGTH)
            )
        }
        controller.sendResponse(response)
        stateRepository.updateTrustedPeerSyncStatus(
            peerIdValue,
            if (response.accepted) System.currentTimeMillis() else null,
            response.error
        )
    }

    private fun handleHistorySyncRequest(
        remotePeerId: PeerId,
        request: HistorySyncRequest,
        controller: HistorySyncProtocolController
    ) {
        val peerIdValue = remotePeerId.toBase58()
        val response = try {
            ensureTrusted(peerIdValue)
            val engine = historySyncEngine
                ?: throw HistorySyncException(
                    "History synchronization is unavailable"
                )
            engine.handleRequest(peerIdValue, request)
        } catch (error: Exception) {
            HistorySyncResponse(
                accepted = false,
                category = request.category,
                error = (
                    error.message ?: "The history synchronization request was rejected"
                    ).take(MAX_SYNC_ERROR_LENGTH)
            )
        }
        controller.sendResponse(response)
        stateRepository.updateTrustedPeerSyncStatus(
            peerIdValue,
            if (response.accepted) System.currentTimeMillis() else null,
            response.error
        )
    }

    private fun handleStructuredPreferenceSyncRequest(
        remotePeerId: PeerId,
        request: StructuredPreferenceSyncRequest,
        controller: StructuredPreferenceSyncProtocolController
    ) {
        val peerIdValue = remotePeerId.toBase58()
        val response = try {
            ensureTrusted(peerIdValue)
            val engine = structuredPreferenceSyncEngine
                ?: throw StructuredPreferenceSyncException(
                    "Structured preference synchronization is unavailable"
                )
            engine.handleRequest(peerIdValue, request)
        } catch (error: Exception) {
            StructuredPreferenceSyncResponse(
                accepted = false,
                category = request.category,
                error = (
                    error.message
                        ?: "The structured preference request was rejected"
                    ).take(MAX_SYNC_ERROR_LENGTH)
            )
        }
        controller.sendResponse(response)
        stateRepository.updateTrustedPeerSyncStatus(
            peerIdValue,
            if (response.accepted) System.currentTimeMillis() else null,
            response.error
        )
    }

    private fun ensureTrusted(peerIdValue: String) {
        if (stateRepository.getTrustedPeers().none { it.peerId == peerIdValue }) {
            throw SubscriptionSyncException("The connected device is not trusted")
        }
    }

    private fun parsePeerId(peerIdValue: String): PeerId {
        return try {
            PeerId.fromBase58(peerIdValue)
        } catch (error: Exception) {
            throw SubscriptionSyncException("The trusted device PeerID is invalid", error)
        }
    }

    private fun parseAddresses(
        remotePeerId: PeerId,
        addresses: List<String>
    ): Array<Multiaddr> {
        if (addresses.isEmpty() || addresses.size > MAX_PAIRING_ADDRESSES) {
            throw SubscriptionSyncException("The trusted device has no valid network address")
        }
        return try {
            addresses.map(::Multiaddr)
                .onEach { address ->
                    if (address.getPeerId() != remotePeerId) {
                        throw SubscriptionSyncException(
                            "A trusted device address has the wrong PeerID"
                        )
                    }
                }
                .toTypedArray()
        } catch (error: Exception) {
            throw SubscriptionSyncException(
                "The trusted device network addresses are invalid",
                error
            )
        }
    }

    private fun requireStartedHost(): Host {
        if (!started || activeHost == null) {
            throw PairingException("Device synchronization is not running")
        }
        return requireNotNull(activeHost)
    }

    private fun isAddressAlreadyInUse(error: Throwable): Boolean {
        return error.causes().any { cause ->
            cause is BindException ||
                cause.message?.contains("address already in use", ignoreCase = true) == true ||
                cause.message?.contains("EADDRINUSE", ignoreCase = true) == true
        }
    }

    private fun startFailureMessage(error: Throwable): String {
        val detail = error.causes()
            .mapNotNull { it.message?.trim()?.takeIf(String::isNotEmpty) }
            .lastOrNull()
            ?.take(MAX_START_ERROR_LENGTH)
        return if (detail == null) {
            "Could not start secure device synchronization"
        } else {
            "Could not start secure device synchronization: $detail"
        }
    }

    private fun Throwable.causes(): Sequence<Throwable> {
        return generateSequence(this) { it.cause }.take(MAX_CAUSE_DEPTH)
    }

    private fun ephemeralListenAddress(address: String): String {
        return listenAddressWithPort(address, 0)
    }

    private fun listenAddressWithPort(address: String, port: Int): String {
        return TCP_PORT_VALUE.replace(address, port.toString())
    }

    companion object {
        private const val LISTEN_ADDRESS = "/ip4/0.0.0.0/tcp/0"
        private const val START_TIMEOUT_SECONDS = 20L
        private const val STOP_TIMEOUT_SECONDS = 10L
        private const val PAIR_TIMEOUT_SECONDS = 20L
        private const val SYNC_TIMEOUT_SECONDS = 30L
        private const val MAX_SYNC_ROUNDS = 2048
        private const val MAX_SYNC_ERROR_LENGTH = 512
        private const val MAX_START_ERROR_LENGTH = 256
        private const val MAX_CAUSE_DEPTH = 16
        private const val MIN_LISTEN_PORT = 1
        private const val MAX_LISTEN_PORT = 65_535
        private val TCP_PORT_VALUE = Regex("(?<=/tcp/)\\d+")
    }
}

internal fun mergeTrustedPeerAddresses(
    peerIdValue: String,
    discoveredAddresses: List<String>,
    storedAddresses: List<String>
): List<String> {
    val peerId = runCatching { PeerId.fromBase58(peerIdValue) }.getOrNull()
        ?: return storedAddresses
    return (discoveredAddresses + storedAddresses)
        .asSequence()
        .filter { it.length <= MAX_MULTIADDRESS_LENGTH }
        .filter { value ->
            runCatching { Multiaddr(value).getPeerId() == peerId }.getOrDefault(false)
        }
        .distinct()
        .take(MAX_PAIRING_ADDRESSES)
        .toList()
}
