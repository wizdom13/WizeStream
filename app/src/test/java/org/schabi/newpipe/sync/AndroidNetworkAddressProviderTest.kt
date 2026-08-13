/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import java.net.Inet4Address
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidNetworkAddressProviderTest {
    @Test
    fun `only reachable wifi or ethernet addresses are advertised`() {
        val addresses = selectAdvertisableIpv4Addresses(
            sequenceOf(
                candidate("127.0.0.1", wifiOrEthernet = true),
                candidate("10.125.51.60", wifiOrEthernet = false),
                candidate("192.168.0.19", wifiOrEthernet = true)
            )
        )

        assertEquals(listOf("192.168.0.19"), addresses.map(InetAddress::getHostAddress))
    }

    @Test
    fun `active network addresses are advertised before other lan addresses`() {
        val addresses = selectAdvertisableIpv4Addresses(
            sequenceOf(
                candidate("192.168.2.10", wifiOrEthernet = true),
                candidate(
                    "192.168.0.19",
                    wifiOrEthernet = true,
                    activeNetwork = true
                ),
                candidate("192.168.2.10", wifiOrEthernet = true)
            )
        )

        assertEquals(
            listOf("192.168.0.19", "192.168.2.10"),
            addresses.map(InetAddress::getHostAddress)
        )
    }

    @Test
    fun `subnet scan candidates stay inside the active private subnet`() {
        val addresses = subnetHostAddresses(
            ipv4("10.54.175.20"),
            24
        ).map(InetAddress::getHostAddress)

        assertEquals(253, addresses.size)
        assertEquals("10.54.175.1", addresses.first())
        assertEquals("10.54.175.254", addresses.last())
        assertFalse("10.54.175.20" in addresses)
        assertTrue("10.54.175.38" in addresses)
    }

    @Test
    fun `broad private networks are bounded to the local slash 24`() {
        val addresses = subnetHostAddresses(
            ipv4("10.54.175.20"),
            16
        ).map(InetAddress::getHostAddress)

        assertEquals(253, addresses.size)
        assertTrue(addresses.all { it.startsWith("10.54.175.") })
    }

    @Test
    fun `public addresses are not scanned`() {
        assertTrue(
            subnetHostAddresses(
                ipv4("8.8.8.8"),
                24
            ).isEmpty()
        )
    }

    @Test
    fun `stale mdns candidates do not block hotspot subnet recovery`() {
        val staleMdns = "/ip4/10.113.36.244/tcp/44065/p2p/trusted-peer"
        val currentHotspot = "/ip4/10.197.178.244/tcp/44065/p2p/trusted-peer"
        val secondMdns = "/ip4/192.168.0.244/tcp/44065/p2p/trusted-peer"

        assertEquals(
            listOf(staleMdns, currentHotspot, secondMdns),
            combinePeerDiscoveryCandidates(
                mdnsAddresses = listOf(staleMdns, secondMdns),
                subnetAddresses = listOf(currentHotspot)
            )
        )
    }

    @Test
    fun `discovery candidates are deduplicated and bounded without starving subnet recovery`() {
        val mdns = (1..MAX_PAIRING_ADDRESSES).map { index ->
            "/ip4/10.0.0.$index/tcp/44065/p2p/trusted-peer"
        }
        val recovered = "/ip4/10.197.178.244/tcp/44065/p2p/trusted-peer"

        val candidates = combinePeerDiscoveryCandidates(
            mdnsAddresses = mdns,
            subnetAddresses = listOf(recovered, recovered)
        )

        assertEquals(MAX_PAIRING_ADDRESSES, candidates.size)
        assertEquals(mdns.first(), candidates.first())
        assertEquals(recovered, candidates[1])
        assertEquals(candidates.size, candidates.distinct().size)
    }

    private fun candidate(
        address: String,
        wifiOrEthernet: Boolean,
        activeNetwork: Boolean = false
    ): NetworkAddressCandidate {
        return NetworkAddressCandidate(
            address = InetAddress.getByName(address),
            isWifiOrEthernet = wifiOrEthernet,
            isActiveNetwork = activeNetwork
        )
    }

    private fun ipv4(address: String): Inet4Address {
        return InetAddress.getByName(address) as Inet4Address
    }
}
