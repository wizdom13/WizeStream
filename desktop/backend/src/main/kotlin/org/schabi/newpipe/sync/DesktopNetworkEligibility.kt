/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import java.net.Inet4Address
import java.net.NetworkInterface

internal open class DesktopNetworkEligibility {
    open fun status(): Map<String, Any> {
        val eligible = runCatching {
            NetworkInterface.getNetworkInterfaces().toList().asSequence()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .flatMap { it.inetAddresses.toList().asSequence() }
                .filterIsInstance<Inet4Address>()
                .any { it.isSiteLocalAddress && !it.isLoopbackAddress && !it.isLinkLocalAddress }
        }.getOrDefault(false)
        return linkedMapOf(
            "eligible" to eligible,
            "reason" to if (eligible) "privateLocalNetwork" else "offlineOrNoPrivateNetwork"
        )
    }

    fun isEligible(): Boolean = status()["eligible"] == true
}
