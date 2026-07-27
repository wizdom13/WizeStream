/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import java.net.InetAddress
import org.junit.Assert.assertEquals
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
}
