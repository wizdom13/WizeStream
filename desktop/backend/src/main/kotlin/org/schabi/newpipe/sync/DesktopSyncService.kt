/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import io.libp2p.core.Host
import io.libp2p.core.multiformats.Multiaddr
import java.net.Inet4Address
import java.net.NetworkInterface
import java.sql.Connection

class DesktopSyncService(
    connection: Connection,
    private val deviceName: String
) {
    private val repository = DesktopSyncStateRepository(connection)
    private val identity = repository.loadOrCreateIdentity()
    private val journal = DesktopChangeJournal(connection, identity.peerId.toBase58())
    private val subscriptionEngine = SubscriptionSyncEngine(
        DesktopSubscriptionSyncStore(connection, identity.peerId.toBase58(), journal)
    )
    private val playlistEngine = PlaylistSyncEngine(
        DesktopPlaylistSyncStore(connection, identity.peerId.toBase58(), journal)
    )
    private val historyEngine = HistorySyncEngine(
        DesktopHistorySyncStore(connection, identity.peerId.toBase58(), journal)
    )
    private val structuredEngine = StructuredPreferenceSyncEngine(
        DesktopStructuredPreferenceSyncStore(connection, identity.peerId.toBase58(), journal)
    )
    private val discovery = DesktopPeerDiscovery()
    private val node = Libp2pSyncNode(
        stateRepository = repository,
        pairingSecurity = PairingSecurity(),
        deviceName = deviceName,
        advertisedAddressProvider = ::advertisedAddresses,
        subscriptionSyncEngine = subscriptionEngine,
        listenAddress = "/ip4/0.0.0.0/tcp/${repository.getListenPort() ?: 0}",
        playlistSyncEngine = playlistEngine,
        onListenPortSelected = repository::saveListenPort,
        historySyncEngine = historyEngine,
        structuredPreferenceSyncEngine = structuredEngine,
        peerAddressResolver = discovery::addressesFor
    )

    @Synchronized
    fun start() = node.start(allowEphemeralFallback = true)

    @Synchronized
    fun stop() = node.stop()

    fun status(): Map<String, Any?> = linkedMapOf(
        "protocol" to SYNC_PROTOCOL_ID,
        "peerId" to identity.peerId.toBase58(),
        "listenAddresses" to runCatching(node::advertisedAddresses).getOrDefault(emptyList()),
        "trustedPeers" to repository.getTrustedPeers().map { peer ->
            linkedMapOf(
                "peerId" to peer.peerId,
                "deviceName" to peer.deviceName,
                "lastSyncAtEpochMillis" to peer.lastSyncAtEpochMillis,
                "lastSyncError" to peer.lastSyncError
            )
        },
        "dataSyncEnabled" to true,
        "automaticLanDiscovery" to true,
        "categories" to CATEGORY_ORDER
    )

    fun createPairingCode(): String = node.createPairingCode()

    fun pair(pairingCode: String): Map<String, Any?> {
        val peer = node.pair(pairingCode)
        return linkedMapOf("peerId" to peer.peerId, "deviceName" to peer.deviceName)
    }

    fun sync(categoryNames: List<String>?): Map<String, Any?> {
        val categories = categoryNames?.takeIf { it.isNotEmpty() }
            ?.map(::parseCategory)?.toSet() ?: SyncCategory.entries.toSet()
        val peers = repository.getTrustedPeers()
        if (peers.isEmpty()) throw SubscriptionSyncException(
            "Pair a trusted device before synchronizing"
        )
        val attempts = peers.map { peer -> syncPeer(peer, categories) }
        return linkedMapOf(
            "requestedCategories" to CATEGORY_ORDER.filter { parseCategory(it) in categories },
            "peers" to attempts,
            "succeeded" to attempts.count { it["error"] == null },
            "failed" to attempts.count { it["error"] != null }
        )
    }

    private fun syncPeer(
        initialPeer: TrustedPeer,
        categories: Set<SyncCategory>
    ): Map<String, Any?> {
        var peer = initialPeer
        val results = linkedMapOf<String, Any?>()
        val errors = mutableListOf<String>()
        categories.forEach { category ->
            val attempt = runCatching { synchronizeCategory(peer, category) }
            val retried = if (attempt.exceptionOrNull().isReachabilityFailure()) {
                node.refreshPeerAddresses(peer)?.let { refreshed ->
                    peer = refreshed
                    runCatching { synchronizeCategory(peer, category) }
                } ?: attempt
            } else attempt
            retried.onSuccess { results[category.wireName] = it }
                .onFailure { error ->
                    val message = error.diagnosticMessage()
                    results[category.wireName] = linkedMapOf("error" to message)
                    errors += "${category.wireName}: $message"
                }
        }
        repository.updateTrustedPeerSyncStatus(
            peer.peerId,
            if (errors.isEmpty()) System.currentTimeMillis() else null,
            errors.takeIf(List<String>::isNotEmpty)?.joinToString("; ")
        )
        return linkedMapOf(
            "peerId" to peer.peerId,
            "deviceName" to peer.deviceName,
            "results" to results,
            "error" to errors.takeIf { it.isNotEmpty() }?.joinToString("; ")
        )
    }

    private fun synchronizeCategory(peer: TrustedPeer, category: SyncCategory): Map<String, Any> =
        when (category) {
            SyncCategory.SUBSCRIPTIONS -> node.syncSubscriptions(peer, false).let { result ->
                linkedMapOf(
                    "sent" to result.sentChanges,
                    "received" to result.receivedChanges,
                    "changed" to result.addedSubscriptions + result.removedSubscriptions,
                    "rounds" to result.rounds
                )
            }
            SyncCategory.PLAYLISTS -> node.syncPlaylists(peer, false).let { result ->
                linkedMapOf(
                    "sent" to result.sentChanges,
                    "received" to result.receivedChanges,
                    "changed" to result.changedPlaylists,
                    "rounds" to result.rounds
                )
            }
            SyncCategory.WATCH_HISTORY -> historyResult(
                node.syncHistory(peer, HistorySyncCategory.WATCH, false)
            )
            SyncCategory.SEARCH_HISTORY -> historyResult(
                node.syncHistory(peer, HistorySyncCategory.SEARCH, false)
            )
            SyncCategory.LEARNING_NOTES -> historyResult(
                node.syncHistory(peer, HistorySyncCategory.LEARNING_NOTES, false)
            )
            SyncCategory.FEED_GROUPS -> structuredResult(
                node.syncStructuredPreferences(peer, StructuredPreferenceCategory.FEED_GROUPS, false)
            )
            SyncCategory.HOME_TABS -> structuredResult(
                node.syncStructuredPreferences(peer, StructuredPreferenceCategory.HOME_TABS, false)
            )
            SyncCategory.CHANNEL_PROFILES -> structuredResult(
                node.syncStructuredPreferences(peer, StructuredPreferenceCategory.CHANNEL_PROFILES, false)
            )
            SyncCategory.FILTERS -> structuredResult(
                node.syncStructuredPreferences(peer, StructuredPreferenceCategory.FILTERS, false)
            )
            SyncCategory.SETTINGS -> structuredResult(
                node.syncStructuredPreferences(peer, StructuredPreferenceCategory.SETTINGS, false)
            )
            SyncCategory.COMPLETED_DOWNLOADS -> structuredResult(
                node.syncStructuredPreferences(
                    peer,
                    StructuredPreferenceCategory.COMPLETED_DOWNLOADS,
                    false
                )
            )
        }

    private fun historyResult(result: HistorySyncResult): Map<String, Any> = linkedMapOf(
        "sent" to result.sentChanges,
        "received" to result.receivedChanges,
        "changed" to result.affectedRecords,
        "rounds" to result.rounds
    )

    private fun structuredResult(result: StructuredPreferenceSyncResult): Map<String, Any> =
        linkedMapOf(
            "sent" to result.sentChanges,
            "received" to result.receivedChanges,
            "changed" to result.affectedRecords,
            "rounds" to result.rounds
        )

    private fun advertisedAddresses(host: Host): List<String> {
        val port = host.listenAddresses().asSequence().mapNotNull(::tcpPort).firstOrNull()
            ?: throw PairingException("The desktop synchronization listener has no TCP port")
        val suffix = "/tcp/$port/p2p/${identity.peerId.toBase58()}"
        val addresses = NetworkInterface.getNetworkInterfaces().toList().asSequence()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .flatMap { it.inetAddresses.toList().asSequence() }
            .filterIsInstance<Inet4Address>()
            .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            .sortedByDescending(Inet4Address::isSiteLocalAddress)
            .map { "/ip4/${it.hostAddress}$suffix" }
            .distinct()
            .take(8)
            .toList()
        if (addresses.isEmpty()) throw PairingException(
            "No local network address is available for pairing"
        )
        return addresses
    }

    private fun tcpPort(address: Multiaddr): Int? = TCP_PORT.find(address.toString())
        ?.groupValues?.get(1)?.toIntOrNull()

    private fun parseCategory(value: String): SyncCategory = SyncCategory.entries.firstOrNull {
        it.wireName == value
    } ?: throw IllegalArgumentException("Unknown synchronization category: $value")

    private fun Throwable?.isReachabilityFailure(): Boolean = this != null &&
        generateSequence(this) { it.cause }.take(8).mapNotNull { error -> error.message }
            .any { it.startsWith("Could not reach ") }

    private fun Throwable.diagnosticMessage(): String = generateSequence(this) { it.cause }
        .take(4)
        .joinToString(" → ") { error ->
            error.message?.takeIf(String::isNotBlank)?.let {
                "${error.javaClass.simpleName}: $it"
            } ?: error.javaClass.simpleName
        }.take(2_048)

    private enum class SyncCategory(val wireName: String) {
        SUBSCRIPTIONS("subscriptions"),
        PLAYLISTS("playlists"),
        WATCH_HISTORY("watchHistory"),
        SEARCH_HISTORY("searchHistory"),
        LEARNING_NOTES("learningNotes"),
        FEED_GROUPS("feedGroups"),
        HOME_TABS("homeTabs"),
        CHANNEL_PROFILES("channelProfiles"),
        FILTERS("filters"),
        SETTINGS("settings"),
        COMPLETED_DOWNLOADS("completedDownloads")
    }

    companion object {
        private val TCP_PORT = Regex("/tcp/(\\d+)")
        private val CATEGORY_ORDER = SyncCategory.entries.map(SyncCategory::wireName)
    }
}
