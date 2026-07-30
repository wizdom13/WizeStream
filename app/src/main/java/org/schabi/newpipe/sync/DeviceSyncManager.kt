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
    private val syncLogRepository = DeviceSyncLogRepository(applicationContext)
    private val peerDiscovery = AndroidPeerDiscovery(applicationContext)
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
            advertisedAddressProvider = { host ->
                AndroidNetworkAddressProvider.addresses(applicationContext, host)
            },
            peerAddressResolver = { peer ->
                peerDiscovery.addressesFor(peer)
            },
            subscriptionSyncEngine = subscriptionSyncEngine,
            listenAddress = "/ip4/0.0.0.0/tcp/$listenPort",
            onListenPortSelected = { port ->
                stateRepository.saveListenPort(port)
                peerDiscovery.start(peerId, port)
            },
            playlistSyncEngine = playlistSyncEngine,
            historySyncEngine = historySyncEngine,
            structuredPreferenceSyncEngine = structuredPreferenceSyncEngine,
            onTrustedPeerSaved = {
                DeviceSyncBackgroundScheduler.initialize(
                    applicationContext,
                    hasTrustedPeers = true
                )
            }
        )
    }

    val peerId: String
        get() = stateRepository.loadOrCreateIdentity().peerId.toBase58()

    val trustedPeers: List<TrustedPeer>
        get() = stateRepository.getTrustedPeers()

    val syncLogEntries: List<DeviceSyncLogEntry>
        get() = syncLogRepository.entries()

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
        node.start(allowEphemeralFallback = true)
    }

    @Synchronized
    fun syncSubscriptions(): DeviceSyncSummary {
        node.start(allowEphemeralFallback = true)
        val peers = trustedPeers
        if (peers.isEmpty()) {
            throw SubscriptionSyncException("Pair a trusted device before synchronizing")
        }
        val attempts = peers.map { peer ->
            var activePeer = peer
            var result = runCatching {
                node.syncSubscriptions(peer)
            }
            if (result.exceptionOrNull().isReachabilityFailure()) {
                node.refreshPeerAddresses(peer)?.let { refreshedPeer ->
                    activePeer = refreshedPeer
                    result = runCatching {
                        node.syncSubscriptions(refreshedPeer)
                    }
                }
            }
            result.fold(
                onSuccess = {
                    DeviceSyncAttempt(
                        peer = activePeer,
                        result = it
                    )
                },
                onFailure = { error ->
                    DeviceSyncAttempt(
                        peer = activePeer,
                        error = error.message ?: "Subscription synchronization failed"
                    )
                }
            )
        }
        return DeviceSyncSummary(attempts)
    }

    @Synchronized
    fun sync(): DeviceSyncSummary {
        return syncAndRecord(background = false)
    }

    @Synchronized
    fun syncInBackground(): DeviceSyncSummary {
        return syncAndRecord(background = true)
    }

    private fun syncAndRecord(background: Boolean): DeviceSyncSummary {
        return try {
            syncInternal(background).also { summary ->
                syncLogRepository.record(
                    summary = summary,
                    background = background,
                    localAddresses = runCatching(node::advertisedAddresses)
                        .getOrDefault(emptyList())
                )
            }
        } catch (error: Exception) {
            syncLogRepository.recordFailure(background, error)
            throw error
        }
    }

    private fun syncInternal(background: Boolean): DeviceSyncSummary {
        node.start(allowEphemeralFallback = true)
        val peers = trustedPeers
        if (peers.isEmpty()) {
            throw SubscriptionSyncException("Pair a trusted device before synchronizing")
        }
        val attempts = peers.map { peer ->
            var activePeer = peer
            var subscription = runCatching {
                node.syncSubscriptions(peer, recordStatus = !background)
            }
            if (subscription.exceptionOrNull().isReachabilityFailure()) {
                node.refreshPeerAddresses(peer)?.let { refreshedPeer ->
                    activePeer = refreshedPeer
                    subscription = runCatching {
                        node.syncSubscriptions(refreshedPeer, recordStatus = !background)
                    }
                }
            }
            val canContinue = subscription.isSuccess ||
                (!background && !subscription.exceptionOrNull().isReachabilityFailure())
            val playlist = if (canContinue) {
                runCatching {
                    node.syncPlaylists(activePeer, recordStatus = !background)
                }
            } else {
                null
            }
            val watchHistoryEnabled = historySyncEngine.isEnabled(
                HistorySyncCategory.WATCH
            )
            val watchHistory = if (canContinue && watchHistoryEnabled) {
                runCatching {
                    node.syncHistory(
                        activePeer,
                        HistorySyncCategory.WATCH,
                        recordStatus = !background
                    )
                }
            } else {
                null
            }
            val searchHistoryEnabled = historySyncEngine.isEnabled(
                HistorySyncCategory.SEARCH
            )
            val searchHistory = if (canContinue && searchHistoryEnabled) {
                runCatching {
                    node.syncHistory(
                        activePeer,
                        HistorySyncCategory.SEARCH,
                        recordStatus = !background
                    )
                }
            } else {
                null
            }
            val structuredPreferences = if (canContinue) {
                StructuredPreferenceCategory.entries.associateWith { category ->
                    runCatching {
                        node.syncStructuredPreferences(
                            activePeer,
                            category,
                            recordStatus = !background
                        )
                    }
                }
            } else {
                emptyMap()
            }
            val errors = listOfNotNull(
                subscription.exceptionOrNull()?.message,
                playlist?.exceptionOrNull()?.message,
                watchHistory?.exceptionOrNull()?.message,
                searchHistory?.exceptionOrNull()?.message
            ) + structuredPreferences.values.mapNotNull { result ->
                result.exceptionOrNull()?.message
            }
            if (!background || errors.isEmpty()) {
                stateRepository.updateTrustedPeerSyncStatus(
                    activePeer.peerId,
                    if (errors.isEmpty()) System.currentTimeMillis() else null,
                    errors.takeIf { it.isNotEmpty() }?.joinToString("; ")
                )
            }
            DeviceSyncAttempt(
                peer = activePeer,
                result = subscription.getOrNull(),
                error = subscription.exceptionOrNull().diagnosticMessage(),
                playlistResult = playlist?.getOrNull(),
                playlistError = playlist?.exceptionOrNull().diagnosticMessage(),
                watchHistoryResult = watchHistory?.getOrNull(),
                watchHistoryError = watchHistory?.exceptionOrNull().diagnosticMessage(),
                watchHistorySkipped = !watchHistoryEnabled,
                searchHistoryResult = searchHistory?.getOrNull(),
                searchHistoryError = searchHistory?.exceptionOrNull().diagnosticMessage(),
                searchHistorySkipped = !searchHistoryEnabled,
                structuredPreferenceResults = structuredPreferences.mapValues {
                    it.value.getOrNull()
                },
                structuredPreferenceErrors = structuredPreferences.mapValues {
                    it.value.exceptionOrNull().diagnosticMessage()
                }.filterValues { it != null }
            )
        }
        return DeviceSyncSummary(attempts)
    }

    private fun Throwable?.diagnosticMessage(): String? {
        if (this == null) {
            return null
        }
        return generateSequence(this) { it.cause }
            .take(MAX_LOG_CAUSE_DEPTH)
            .joinToString(LOG_CAUSE_SEPARATOR) { cause ->
                val name = cause.javaClass.simpleName
                cause.message?.takeIf(String::isNotBlank)?.let { "$name: $it" } ?: name
            }
            .take(MAX_LOG_ERROR_LENGTH)
    }

    private fun Throwable?.isReachabilityFailure(): Boolean {
        return this != null && generateSequence(this) { it.cause }
            .take(MAX_LOG_CAUSE_DEPTH)
            .mapNotNull(Throwable::message)
            .any { it.startsWith(REACHABILITY_ERROR_PREFIX) }
    }

    @Synchronized
    fun clearTrustedPeers() {
        stateRepository.clearTrustedPeers()
        subscriptionSyncEngine.clearPeerKnowledge()
        playlistSyncEngine.clearPeerKnowledge()
        historySyncEngine.clearPeerKnowledge()
        structuredPreferenceSyncEngine.clearPeerKnowledge()
        DeviceSyncBackgroundScheduler.cancel(applicationContext)
    }

    @Synchronized
    fun clearSyncLog() {
        syncLogRepository.clear()
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
        private const val MAX_LOG_CAUSE_DEPTH = 4
        private const val MAX_LOG_ERROR_LENGTH = 2_048
        private const val LOG_CAUSE_SEPARATOR = " → "
        private const val REACHABILITY_ERROR_PREFIX = "Could not reach "

        @Volatile
        private var instance: DeviceSyncManager? = null

        fun get(context: Context): DeviceSyncManager {
            return instance ?: synchronized(this) {
                instance ?: DeviceSyncManager(context).also { instance = it }
            }
        }

        fun hasTrustedPeers(context: Context): Boolean {
            return AndroidSyncStateRepository(context.applicationContext).hasTrustedPeers()
        }
    }
}
