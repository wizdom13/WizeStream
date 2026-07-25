/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import android.content.Context
import android.os.Build

class DeviceSyncManager private constructor(context: Context) {
    private val applicationContext = context.applicationContext
    private val stateRepository = AndroidSyncStateRepository(applicationContext)
    private val subscriptionSyncEngine = SubscriptionSyncEngine(
        RoomSubscriptionSyncStore.get(applicationContext)
    )
    private val playlistSyncEngine = PlaylistSyncEngine(
        RoomPlaylistSyncStore.get(applicationContext)
    )
    private val deviceName = listOf(Build.MANUFACTURER, Build.MODEL)
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .joinToString(" ")
    private val node by lazy {
        Libp2pSyncNode(
            stateRepository = stateRepository,
            pairingSecurity = PairingSecurity(),
            deviceName = deviceName,
            advertisedAddressProvider = AndroidNetworkAddressProvider::addresses,
            subscriptionSyncEngine = subscriptionSyncEngine,
            playlistSyncEngine = playlistSyncEngine
        )
    }

    val peerId: String
        get() = stateRepository.loadOrCreateIdentity().peerId.toBase58()

    val trustedPeers: List<TrustedPeer>
        get() = stateRepository.getTrustedPeers()

    @Synchronized
    fun createPairingCode(): String {
        node.start()
        return node.createPairingCode()
    }

    @Synchronized
    fun pair(pairingCode: String): TrustedPeer {
        node.start()
        return node.pair(pairingCode)
    }

    @Synchronized
    fun startListening() {
        node.start()
    }

    @Synchronized
    fun syncSubscriptions(): DeviceSyncSummary {
        node.start()
        val peers = trustedPeers
        if (peers.isEmpty()) {
            throw SubscriptionSyncException("Pair a trusted device before synchronizing")
        }
        val attempts = peers.map { peer ->
            try {
                DeviceSyncAttempt(
                    peer = peer,
                    result = node.syncSubscriptions(peer)
                )
            } catch (error: Exception) {
                DeviceSyncAttempt(
                    peer = peer,
                    error = error.message ?: "Subscription synchronization failed"
                )
            }
        }
        return DeviceSyncSummary(attempts)
    }

    @Synchronized
    fun sync(): DeviceSyncSummary {
        node.start()
        val peers = trustedPeers
        if (peers.isEmpty()) {
            throw SubscriptionSyncException("Pair a trusted device before synchronizing")
        }
        val attempts = peers.map { peer ->
            val subscription = runCatching {
                node.syncSubscriptions(peer)
            }
            val playlist = runCatching {
                node.syncPlaylists(peer)
            }
            val errors = listOfNotNull(
                subscription.exceptionOrNull()?.message,
                playlist.exceptionOrNull()?.message
            )
            stateRepository.updateTrustedPeerSyncStatus(
                peer.peerId,
                if (errors.isEmpty()) System.currentTimeMillis() else null,
                errors.takeIf { it.isNotEmpty() }?.joinToString("; ")
            )
            DeviceSyncAttempt(
                peer = peer,
                result = subscription.getOrNull(),
                error = subscription.exceptionOrNull()?.message
                    ?: subscription.exceptionOrNull()?.javaClass?.simpleName,
                playlistResult = playlist.getOrNull(),
                playlistError = playlist.exceptionOrNull()?.message
                    ?: playlist.exceptionOrNull()?.javaClass?.simpleName
            )
        }
        return DeviceSyncSummary(attempts)
    }

    fun clearTrustedPeers() {
        stateRepository.clearTrustedPeers()
        subscriptionSyncEngine.clearPeerKnowledge()
        playlistSyncEngine.clearPeerKnowledge()
    }

    companion object {
        @Volatile
        private var instance: DeviceSyncManager? = null

        fun get(context: Context): DeviceSyncManager {
            return instance ?: synchronized(this) {
                instance ?: DeviceSyncManager(context).also { instance = it }
            }
        }
    }
}
