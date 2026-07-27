/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import io.libp2p.core.Host
import java.net.Inet4Address
import java.net.InetAddress

object AndroidNetworkAddressProvider {
    fun addresses(context: Context, host: Host): List<String> {
        val port = host.listenAddresses()
            .asSequence()
            .mapNotNull(::tcpPortFromMultiaddress)
            .firstOrNull { it in 1..65_535 }
            ?: throw PairingException("The synchronization listener has no TCP address")

        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
            ?: throw PairingException("Could not inspect this device's network connections")
        val activeNetwork = connectivityManager.activeNetwork

        @Suppress("DEPRECATION")
        val networkSnapshot = connectivityManager.allNetworks
        val candidates = networkSnapshot
            .asSequence()
            .flatMap { network ->
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                val isWifiOrEthernet = capabilities?.let {
                    it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                } == true
                connectivityManager.getLinkProperties(network)
                    ?.linkAddresses
                    .orEmpty()
                    .asSequence()
                    .map { linkAddress ->
                        NetworkAddressCandidate(
                            address = linkAddress.address,
                            isWifiOrEthernet = isWifiOrEthernet,
                            isActiveNetwork = network == activeNetwork
                        )
                    }
            }

        val networkAddresses = selectAdvertisableIpv4Addresses(candidates)
            .asSequence()
            .map { address ->
                "/ip4/${address.hostAddress}/tcp/$port/p2p/${host.peerId.toBase58()}"
            }
            .take(MAX_PAIRING_ADDRESSES)
            .toList()

        if (networkAddresses.isEmpty()) {
            throw PairingException(
                "Connect this device to Wi-Fi or Ethernet before pairing"
            )
        }
        return networkAddresses
    }
}

internal data class NetworkAddressCandidate(
    val address: InetAddress,
    val isWifiOrEthernet: Boolean,
    val isActiveNetwork: Boolean
)

internal fun selectAdvertisableIpv4Addresses(
    candidates: Sequence<NetworkAddressCandidate>
): List<Inet4Address> {
    return candidates
        .filter(NetworkAddressCandidate::isWifiOrEthernet)
        .filter { isUsablePeerAddress(it.address) }
        .sortedByDescending(NetworkAddressCandidate::isActiveNetwork)
        .map(NetworkAddressCandidate::address)
        .filterIsInstance<Inet4Address>()
        .distinctBy(InetAddress::getHostAddress)
        .toList()
}

private fun isUsablePeerAddress(address: InetAddress): Boolean {
    return address is Inet4Address &&
        !address.isAnyLocalAddress &&
        !address.isLoopbackAddress &&
        !address.isLinkLocalAddress &&
        !address.isMulticastAddress
}

private val TCP_PORT = Regex("(?:^|/)tcp/(\\d+)(?:/|$)")

internal fun tcpPortFromMultiaddress(value: Any): Int? {
    return TCP_PORT.find(value.toString())?.groupValues?.get(1)?.toIntOrNull()
}
