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
    private val profileSyncEngine = ProfileSyncEngine(
        AndroidProfileSyncStore(applicationContext)
    )
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
            profileSyncEngine = profileSyncEngine,
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
                DeviceSyncListenerService.startIfEnabled(applicationContext)
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
        startNode()
        return node.createPairingCode()
    }

    @Synchronized
    fun pair(pairingCode: String): TrustedPeer {
        startNode()
        return node.pair(pairingCode)
    }

    @Synchronized
    fun startListening() {
        startNode()
    }

    @Synchronized
    fun stopListening() {
        node.stop()
    }

    @Synchronized
    fun syncSubscriptions(): DeviceSyncSummary {
        startNode()
        val peers = trustedPeers
        if (peers.isEmpty()) {
            throw SubscriptionSyncException("Pair a trusted device before synchronizing")
        }
        val attempts = peers.map { peer ->
            var activePeer = peer
            val retryDiagnostics = linkedMapOf<DeviceSyncLogCategory, String>()
            val profileAttempt = runSyncStage(
                activePeer,
                DeviceSyncLogCategory.PROFILES,
                retryDiagnostics
            ) { candidate ->
                node.syncProfiles(candidate)
            }
            activePeer = profileAttempt.first
            val profile = profileAttempt.second
            val subscription = if (profile.isSuccess) {
                val attempt = runSyncStage(
                    activePeer,
                    DeviceSyncLogCategory.SUBSCRIPTIONS,
                    retryDiagnostics
                ) { candidate ->
                    node.syncSubscriptions(candidate)
                }
                activePeer = attempt.first
                attempt.second
            } else {
                null
            }
            DeviceSyncAttempt(
                peer = activePeer,
                profileResult = profile.getOrNull(),
                profileError = profile.exceptionOrNull().diagnosticMessage(),
                result = subscription?.getOrNull(),
                error = subscription?.exceptionOrNull().diagnosticMessage()
                    ?: if (profile.isFailure) PROFILE_SYNC_REQUIRED else null,
                retryDiagnostics = retryDiagnostics
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
        startNode()
        val peers = trustedPeers
        if (peers.isEmpty()) {
            throw SubscriptionSyncException("Pair a trusted device before synchronizing")
        }
        val attempts = peers.map { peer ->
            var activePeer = peer
            val retryDiagnostics = linkedMapOf<DeviceSyncLogCategory, String>()

            val profileAttempt = runSyncStage(
                activePeer,
                DeviceSyncLogCategory.PROFILES,
                retryDiagnostics
            ) { candidate ->
                node.syncProfiles(candidate, recordStatus = !background)
            }
            activePeer = profileAttempt.first
            val profile = profileAttempt.second

            val subscription = if (profile.isSuccess) {
                val attempt = runSyncStage(
                    activePeer,
                    DeviceSyncLogCategory.SUBSCRIPTIONS,
                    retryDiagnostics
                ) { candidate ->
                    node.syncSubscriptions(candidate, recordStatus = !background)
                }
                activePeer = attempt.first
                attempt.second
            } else {
                null
            }

            val canContinue = profile.isSuccess &&
                (
                    subscription?.isSuccess == true ||
                        (
                            !background &&
                                subscription != null &&
                                !DeviceSyncTransportRecovery.shouldRetryTransportFailure(
                                    subscription.exceptionOrNull()
                                )
                            )
                    )
            val downstreamTransportError = when {
                profile.isFailure -> PROFILE_SYNC_REQUIRED

                !canContinue &&
                    DeviceSyncTransportRecovery.shouldRetryTransportFailure(
                        subscription?.exceptionOrNull()
                    ) -> PEER_LISTENER_UNAVAILABLE

                else -> null
            }

            val playlist = if (canContinue) {
                val attempt = runSyncStage(
                    activePeer,
                    DeviceSyncLogCategory.PLAYLISTS,
                    retryDiagnostics
                ) { candidate ->
                    node.syncPlaylists(candidate, recordStatus = !background)
                }
                activePeer = attempt.first
                attempt.second
            } else {
                null
            }

            val watchHistoryEnabled = historySyncEngine.isEnabled(
                HistorySyncCategory.WATCH
            )
            val watchHistory = if (canContinue && watchHistoryEnabled) {
                val attempt = runSyncStage(
                    activePeer,
                    DeviceSyncLogCategory.WATCH_HISTORY,
                    retryDiagnostics
                ) { candidate ->
                    node.syncHistory(
                        candidate,
                        HistorySyncCategory.WATCH,
                        recordStatus = !background
                    )
                }
                activePeer = attempt.first
                attempt.second
            } else {
                null
            }

            val searchHistoryEnabled = historySyncEngine.isEnabled(
                HistorySyncCategory.SEARCH
            )
            val searchHistory = if (canContinue && searchHistoryEnabled) {
                val attempt = runSyncStage(
                    activePeer,
                    DeviceSyncLogCategory.SEARCH_HISTORY,
                    retryDiagnostics
                ) { candidate ->
                    node.syncHistory(
                        candidate,
                        HistorySyncCategory.SEARCH,
                        recordStatus = !background
                    )
                }
                activePeer = attempt.first
                attempt.second
            } else {
                null
            }

            val learningNotesEnabled = historySyncEngine.isEnabled(
                HistorySyncCategory.LEARNING_NOTES
            )
            val learningNotes = if (canContinue && learningNotesEnabled) {
                val attempt = runSyncStage(
                    activePeer,
                    DeviceSyncLogCategory.LEARNING_NOTES,
                    retryDiagnostics
                ) { candidate ->
                    node.syncHistory(
                        candidate,
                        HistorySyncCategory.LEARNING_NOTES,
                        recordStatus = !background
                    )
                }
                activePeer = attempt.first
                attempt.second
            } else {
                null
            }

            val structuredPreferences =
                linkedMapOf<StructuredPreferenceCategory, Result<StructuredPreferenceSyncResult>>()
            if (canContinue) {
                StructuredPreferenceCategory.entries.forEach { category ->
                    val attempt = runSyncStage(
                        activePeer,
                        category.toLogCategory(),
                        retryDiagnostics
                    ) { candidate ->
                        node.syncStructuredPreferences(
                            candidate,
                            category,
                            recordStatus = !background
                        )
                    }
                    activePeer = attempt.first
                    structuredPreferences[category] = attempt.second
                }
            }

            val errors = listOfNotNull(
                profile.exceptionOrNull()?.message,
                subscription?.exceptionOrNull()?.message,
                playlist?.exceptionOrNull()?.message,
                watchHistory?.exceptionOrNull()?.message,
                searchHistory?.exceptionOrNull()?.message,
                learningNotes?.exceptionOrNull()?.message
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
                profileResult = profile.getOrNull(),
                profileError = profile.exceptionOrNull().diagnosticMessage(),
                result = subscription?.getOrNull(),
                error = subscription?.exceptionOrNull().diagnosticMessage()
                    ?: if (profile.isFailure) PROFILE_SYNC_REQUIRED else null,
                playlistResult = playlist?.getOrNull(),
                playlistError = playlist?.exceptionOrNull().diagnosticMessage()
                    ?: downstreamTransportError,
                watchHistoryResult = watchHistory?.getOrNull(),
                watchHistoryError = if (watchHistoryEnabled) {
                    watchHistory?.exceptionOrNull().diagnosticMessage()
                        ?: downstreamTransportError
                } else {
                    null
                },
                watchHistorySkipped = !watchHistoryEnabled,
                searchHistoryResult = searchHistory?.getOrNull(),
                searchHistoryError = if (searchHistoryEnabled) {
                    searchHistory?.exceptionOrNull().diagnosticMessage()
                        ?: downstreamTransportError
                } else {
                    null
                },
                searchHistorySkipped = !searchHistoryEnabled,
                learningNotesResult = learningNotes?.getOrNull(),
                learningNotesError = if (learningNotesEnabled) {
                    learningNotes?.exceptionOrNull().diagnosticMessage()
                        ?: downstreamTransportError
                } else {
                    null
                },
                learningNotesSkipped = !learningNotesEnabled,
                structuredPreferenceResults = structuredPreferences.mapValues {
                    it.value.getOrNull()
                },
                structuredPreferenceErrors = if (canContinue) {
                    structuredPreferences.mapValues {
                        it.value.exceptionOrNull().diagnosticMessage()
                    }.filterValues { it != null }
                } else if (downstreamTransportError != null) {
                    StructuredPreferenceCategory.entries.associateWith {
                        downstreamTransportError
                    }
                } else {
                    emptyMap()
                },
                retryDiagnostics = retryDiagnostics
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

    private fun <T> runSyncStage(
        peer: TrustedPeer,
        category: DeviceSyncLogCategory,
        retryDiagnostics: MutableMap<DeviceSyncLogCategory, String>,
        operation: (TrustedPeer) -> T
    ): Pair<TrustedPeer, Result<T>> {
        val attempt = DeviceSyncTransportRecovery.run(
            peer = peer,
            refreshPeer = node::refreshPeerAddresses,
            operation = operation
        )
        attempt.retryDiagnostic?.let { diagnostic ->
            retryDiagnostics[category] = diagnostic
        }
        return attempt.peer to attempt.result
    }

    private fun StructuredPreferenceCategory.toLogCategory(): DeviceSyncLogCategory {
        return when (this) {
            StructuredPreferenceCategory.FEED_GROUPS -> DeviceSyncLogCategory.FEED_GROUPS

            StructuredPreferenceCategory.HOME_TABS -> DeviceSyncLogCategory.HOME_TABS

            StructuredPreferenceCategory.CHANNEL_PROFILES ->
                DeviceSyncLogCategory.CHANNEL_PROFILES

            StructuredPreferenceCategory.FILTERS -> DeviceSyncLogCategory.FILTERS

            StructuredPreferenceCategory.SETTINGS -> DeviceSyncLogCategory.SETTINGS

            StructuredPreferenceCategory.COMPLETED_DOWNLOADS ->
                DeviceSyncLogCategory.COMPLETED_DOWNLOADS
        }
    }

    @Synchronized
    fun clearTrustedPeers() {
        stateRepository.clearTrustedPeers()
        subscriptionSyncEngine.clearPeerKnowledge()
        playlistSyncEngine.clearPeerKnowledge()
        historySyncEngine.clearPeerKnowledge()
        structuredPreferenceSyncEngine.clearPeerKnowledge()
        node.stop()
        DeviceSyncBackgroundScheduler.cancel(applicationContext)
        DeviceSyncListenerService.stop(applicationContext)
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

            HistorySyncCategory.LEARNING_NOTES ->
                org.schabi.newpipe.learning.LearningMode.isNotesSyncEnabled(applicationContext)
        }
    }

    private fun startNode() {
        try {
            node.start(allowEphemeralFallback = true)
        } catch (error: LinkageError) {
            if (isUnsupportedCompletableFutureError(Build.VERSION.SDK_INT, error)) {
                throw IllegalStateException(
                    applicationContext.getString(R.string.device_sync_android_version_unsupported),
                    error
                )
            }
            throw error
        }
    }

    companion object {
        private const val DYNAMIC_LISTEN_PORT = 0
        private const val LEGACY_LISTEN_PORT = 48_243
        private const val MAX_LOG_CAUSE_DEPTH = 4
        private const val MAX_LOG_ERROR_LENGTH = 2_048
        private const val LOG_CAUSE_SEPARATOR = " → "
        private const val PEER_LISTENER_UNAVAILABLE =
            "Skipped because the trusted device listener is unavailable after discovery and retry"
        private const val PROFILE_SYNC_REQUIRED =
            "Skipped because profile synchronization did not complete"

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

        internal fun isUnsupportedCompletableFutureError(
            sdkInt: Int,
            error: Throwable
        ): Boolean {
            if (sdkInt >= Build.VERSION_CODES.N) {
                return false
            }
            return generateSequence(error) { it.cause }
                .take(MAX_LOG_CAUSE_DEPTH)
                .mapNotNull(Throwable::message)
                .any { message ->
                    message.contains("java/util/concurrent/CompletableFuture") ||
                        message.contains("java.util.concurrent.CompletableFuture")
                }
        }
    }
}
