/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import io.libp2p.core.Host
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

object AndroidNetworkAddressProvider {
    fun addresses(host: Host): List<String> {
        val port = host.listenAddresses()
            .asSequence()
            .map(MultiAddressPort::from)
            .firstOrNull()
            ?: throw PairingException("The synchronization listener has no TCP address")

        val networkAddresses = NetworkInterface.getNetworkInterfaces()
            ?.toList()
            .orEmpty()
            .asSequence()
            .filter { it.isUp }
            .flatMap { it.inetAddresses.toList().asSequence() }
            .filterIsInstance<Inet4Address>()
            .filterNot(InetAddress::isAnyLocalAddress)
            .filterNot(InetAddress::isLinkLocalAddress)
            .filterNot(InetAddress::isMulticastAddress)
            .distinctBy(InetAddress::getHostAddress)
            .map { address ->
                "/ip4/${address.hostAddress}/tcp/$port/p2p/${host.peerId.toBase58()}"
            }
            .take(MAX_PAIRING_ADDRESSES)
            .toList()

        return networkAddresses.ifEmpty {
            listOf("/ip4/127.0.0.1/tcp/$port/p2p/${host.peerId.toBase58()}")
        }
    }

    private object MultiAddressPort {
        private val tcpPort = Regex("(?:^|/)tcp/(\\d+)(?:/|$)")

        fun from(value: Any): Int? {
            return tcpPort.find(value.toString())?.groupValues?.get(1)?.toIntOrNull()
        }
    }
}
