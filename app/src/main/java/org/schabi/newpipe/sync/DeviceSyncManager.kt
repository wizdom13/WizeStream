/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import android.content.Context
import android.os.Build
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R

class DeviceSyncManager private constructor(context: Context) {
    private val applicationContext = context.applicationContext
    private val defaultPreferences = PreferenceManager.getDefaultSharedPreferences(
        applicationContext
    )
    private val stateRepository = AndroidSyncStateRepository(applicationContext)
    private val subscriptionSyncEngine = SubscriptionSyncEngine(
        RoomSubscriptionSyncStore.get(applicationContext)
    )
    private val playlistSyncEngine = PlaylistSyncEngine(
        RoomPlaylistSyncStore.get(applicationContext)
    )
    private val historySyncEngine = HistorySyncEngine(
        RoomHistorySyncStore.get(applicationContext),
        ::isHistoryCategoryEnabled
    )
    private val structuredPreferenceSyncEngine = StructuredPreferenceSyncEngine(
        RoomStructuredPreferenceSyncStore.get(applicationContext)
    )
    private val deviceName = listOf(Build.MANUFACTURER, Build.MODEL)
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .joinToString(" ")
    private val node by lazy {
        val listenPort = stateRepository.getListenPort()
            ?: if (stateRepository.getTrustedPeers().isEmpty()) {
                DYNAMIC_LISTEN_PORT
            } else {
                LEGACY_LISTEN_PORT
            }
        Libp2pSyncNode(
            stateRepository = stateRepository,
            pairingSecurity = PairingSecurity(),
            deviceName = deviceName,
            advertisedAddressProvider = AndroidNetworkAddressProvider::addresses,
            subscriptionSyncEngine = subscriptionSyncEngine,
            listenAddress = "/ip4/0.0.0.0/tcp/$listenPort",
            onListenPortSelected = stateRepository::saveListenPort,
            playlistSyncEngine = playlistSyncEngine,
            historySyncEngine = historySyncEngine,
            structuredPreferenceSyncEngine = structuredPreferenceSyncEngine
        )
    }

    val peerId: String
        get() = stateRepository.loadOrCreateIdentity().peerId.toBase58()

    val trustedPeers: List<TrustedPeer>
        get() = stateRepository.getTrustedPeers()

    @Synchronized
    fun createPairingCode(): String {
        node.start(allowEphemeralFallback = true)
        return node.createPairingCode()
    }

    @Synchronized
    fun pair(pairingCode: String): TrustedPeer {
        node.start(allowEphemeralFallback = true)
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
            val watchHistoryEnabled = historySyncEngine.isEnabled(
                HistorySyncCategory.WATCH
            )
            val watchHistory = if (watchHistoryEnabled) {
                runCatching {
                    node.syncHistory(peer, HistorySyncCategory.WATCH)
                }
            } else {
                null
            }
            val searchHistoryEnabled = historySyncEngine.isEnabled(
                HistorySyncCategory.SEARCH
            )
            val searchHistory = if (searchHistoryEnabled) {
                runCatching {
                    node.syncHistory(peer, HistorySyncCategory.SEARCH)
                }
            } else {
                null
            }
            val structuredPreferences = StructuredPreferenceCategory.entries.associateWith { category ->
                runCatching {
                    node.syncStructuredPreferences(peer, category)
                }
            }
            val errors = listOfNotNull(
                subscription.exceptionOrNull()?.message,
                playlist.exceptionOrNull()?.message,
                watchHistory?.exceptionOrNull()?.message,
                searchHistory?.exceptionOrNull()?.message
            ) + structuredPreferences.values.mapNotNull { result ->
                result.exceptionOrNull()?.message
            }
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
                    ?: playlist.exceptionOrNull()?.javaClass?.simpleName,
                watchHistoryResult = watchHistory?.getOrNull(),
                watchHistoryError = watchHistory?.exceptionOrNull()?.message
                    ?: watchHistory?.exceptionOrNull()?.javaClass?.simpleName,
                watchHistorySkipped = !watchHistoryEnabled,
                searchHistoryResult = searchHistory?.getOrNull(),
                searchHistoryError = searchHistory?.exceptionOrNull()?.message
                    ?: searchHistory?.exceptionOrNull()?.javaClass?.simpleName,
                searchHistorySkipped = !searchHistoryEnabled,
                structuredPreferenceResults = structuredPreferences.mapValues {
                    it.value.getOrNull()
                },
                structuredPreferenceErrors = structuredPreferences.mapValues {
                    it.value.exceptionOrNull()?.message
                        ?: it.value.exceptionOrNull()?.javaClass?.simpleName
                }.filterValues { it != null }
            )
        }
        return DeviceSyncSummary(attempts)
    }

    fun clearTrustedPeers() {
        stateRepository.clearTrustedPeers()
        subscriptionSyncEngine.clearPeerKnowledge()
        playlistSyncEngine.clearPeerKnowledge()
        historySyncEngine.clearPeerKnowledge()
        structuredPreferenceSyncEngine.clearPeerKnowledge()
    }

    private fun isHistoryCategoryEnabled(category: HistorySyncCategory): Boolean {
        return when (category) {
            HistorySyncCategory.WATCH -> defaultPreferences.getBoolean(
                applicationContext.getString(R.string.enable_watch_history_key),
                false
            )

            HistorySyncCategory.SEARCH -> defaultPreferences.getBoolean(
                applicationContext.getString(R.string.enable_search_history_key),
                false
            ) && defaultPreferences.getBoolean(
                applicationContext.getString(R.string.device_sync_search_history_key),
                false
            )
        }
    }

    companion object {
        private const val DYNAMIC_LISTEN_PORT = 0
        private const val LEGACY_LISTEN_PORT = 48_243

        @Volatile
        private var instance: DeviceSyncManager? = null

        fun get(context: Context): DeviceSyncManager {
            return instance ?: synchronized(this) {
                instance ?: DeviceSyncManager(context).also { instance = it }
            }
        }
    }
}
