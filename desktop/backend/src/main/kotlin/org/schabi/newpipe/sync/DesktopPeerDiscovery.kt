/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Resolves changed LAN addresses for already-trusted peers without weakening libp2p identity. */
internal class DesktopPeerDiscovery {
    fun addressesFor(peer: TrustedPeer): List<String> {
        val ports = peer.addresses.asSequence()
            .mapNotNull { TCP_PORT.find(it)?.groupValues?.get(1)?.toIntOrNull() }
            .filter { it in 1..65_535 }
            .distinct()
            .take(MAX_PORTS)
            .toList()
        if (ports.isEmpty()) return emptyList()
        val localAddresses = NetworkInterface.getNetworkInterfaces().toList().asSequence()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .flatMap { it.inetAddresses.toList().asSequence() }
            .filterIsInstance<Inet4Address>()
            .filter(Inet4Address::isSiteLocalAddress)
            .distinctBy(Inet4Address::getHostAddress)
            .toList()
        val probes = localAddresses.flatMap(::subnetCandidates)
            .distinctBy(Inet4Address::getHostAddress)
            .flatMap { address -> ports.map { port -> address to port } }
            .take(MAX_PROBES)
        if (probes.isEmpty()) return emptyList()
        val executor = Executors.newFixedThreadPool(minOf(MAX_WORKERS, probes.size))
        return try {
            executor.invokeAll(
                probes.map { (address, port) ->
                    Callable {
                        if (isOpen(address, port)) {
                            "/ip4/${address.hostAddress}/tcp/$port/p2p/${peer.peerId}"
                        } else null
                    }
                },
                SCAN_TIMEOUT_MILLIS,
                TimeUnit.MILLISECONDS
            ).asSequence()
                .filterNot { it.isCancelled }
                .mapNotNull { runCatching(it::get).getOrNull() }
                .distinct()
                .take(MAX_PAIRING_ADDRESSES)
                .toList()
        } finally {
            executor.shutdownNow()
        }
    }

    private fun isOpen(address: Inet4Address, port: Int): Boolean = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(address, port), PROBE_TIMEOUT_MILLIS)
        }
        true
    }.getOrDefault(false)

    companion object {
        private val TCP_PORT = Regex("/tcp/(\\d+)")
        private const val MAX_PORTS = 2
        private const val MAX_PROBES = 512
        private const val MAX_WORKERS = 32
        private const val PROBE_TIMEOUT_MILLIS = 180
        private const val SCAN_TIMEOUT_MILLIS = 3_000L
    }
}

internal fun subnetCandidates(localAddress: Inet4Address): List<Inet4Address> {
    if (!localAddress.isSiteLocalAddress) return emptyList()
    val bytes = localAddress.address
    return (1..254).asSequence()
        .map { last ->
            java.net.InetAddress.getByAddress(
                byteArrayOf(bytes[0], bytes[1], bytes[2], last.toByte())
            ) as Inet4Address
        }
        .filterNot { it == localAddress }
        .toList()
}
