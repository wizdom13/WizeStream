/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.TRANSPORT_ETHERNET
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.ArrayDeque
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Advertises and discovers running WizeStream synchronization listeners on the local network.
 *
 * Discovery does not establish trust. A discovered address is used only for dialing the already
 * trusted PeerID, which is authenticated by the libp2p Noise connection before it is saved.
 */
internal class AndroidPeerDiscovery(context: Context) {
    private val connectivityManager = context.applicationContext
        .getSystemService(ConnectivityManager::class.java)
    private val nsdManager = context.applicationContext
        .getSystemService(NsdManager::class.java)
    private val lock = Object()
    private val addressesByPeerId = mutableMapOf<String, LinkedHashSet<String>>()
    private val servicePeerIds = mutableMapOf<String, String>()
    private val serviceInfoByPeerId = mutableMapOf<String, NsdServiceInfo>()
    private val pendingResolutions = ArrayDeque<NsdServiceInfo>()
    private val lastWaitAtByPeerId = mutableMapOf<String, Long>()

    @Volatile
    private var localPeerId: String? = null

    @Volatile
    private var discoveryStarted = false

    @Volatile
    private var registrationStarted = false

    private var resolving = false

    private val registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
            Log.i(TAG, "Registered local synchronization discovery service")
        }

        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            registrationStarted = false
            Log.w(TAG, "Could not advertise synchronization service: $errorCode")
        }

        override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit

        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            Log.w(TAG, "Could not stop synchronization service advertisement: $errorCode")
        }
    }

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) {
            Log.i(TAG, "Started local synchronization peer discovery")
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            if (serviceInfo.serviceName == serviceName(localPeerId.orEmpty())) {
                return
            }
            synchronized(lock) {
                pendingResolutions.addLast(serviceInfo)
            }
            resolveNext()
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            synchronized(lock) {
                servicePeerIds.remove(serviceInfo.serviceName)?.let { peerId ->
                    addressesByPeerId.remove(peerId)
                    serviceInfoByPeerId.remove(peerId)
                }
            }
        }

        override fun onDiscoveryStopped(serviceType: String) = Unit

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            discoveryStarted = false
            Log.w(TAG, "Could not start synchronization peer discovery: $errorCode")
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.w(TAG, "Could not stop synchronization peer discovery: $errorCode")
        }
    }

    @Synchronized
    fun start(peerId: String, port: Int) {
        localPeerId = peerId
        if (!registrationStarted) {
            registrationStarted = true
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = serviceName(peerId)
                serviceType = SERVICE_TYPE
                setPort(port)
                setAttribute(PEER_ID_ATTRIBUTE, peerId)
            }
            runCatching {
                nsdManager.registerService(
                    serviceInfo,
                    NsdManager.PROTOCOL_DNS_SD,
                    registrationListener
                )
            }.onFailure {
                registrationStarted = false
                Log.w(TAG, "Could not advertise synchronization service", it)
            }
        }
        if (!discoveryStarted) {
            discoveryStarted = true
            runCatching {
                nsdManager.discoverServices(
                    SERVICE_TYPE,
                    NsdManager.PROTOCOL_DNS_SD,
                    discoveryListener
                )
            }.onFailure {
                discoveryStarted = false
                Log.w(TAG, "Could not discover synchronization peers", it)
            }
        }
    }

    fun addressesFor(peer: TrustedPeer): List<String> {
        val mdnsAddresses = mdnsAddressesFor(peer.peerId)
        val subnetAddresses = subnetAddressesFor(peer)
        return combinePeerDiscoveryCandidates(mdnsAddresses, subnetAddresses)
    }

    private fun mdnsAddressesFor(peerId: String): List<String> {
        var requestResolution = false
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val lastWait = lastWaitAtByPeerId[peerId] ?: 0L
            if (now - lastWait < EMPTY_RESULT_COOLDOWN_MILLIS) {
                return addressesByPeerId[peerId]?.toList().orEmpty()
            }
            lastWaitAtByPeerId[peerId] = now
            serviceInfoByPeerId[peerId]?.let { serviceInfo ->
                addressesByPeerId.remove(peerId)
                pendingResolutions.addFirst(serviceInfo)
                requestResolution = true
            }
        }
        if (requestResolution) {
            resolveNext()
        }
        synchronized(lock) {
            val deadline = now + DISCOVERY_WAIT_MILLIS
            while (addressesByPeerId[peerId].isNullOrEmpty()) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) {
                    break
                }
                lock.wait(remaining)
            }
            return addressesByPeerId[peerId]?.toList().orEmpty()
        }
    }

    @Suppress("DEPRECATION")
    private fun subnetAddressesFor(peer: TrustedPeer): List<String> {
        val ports = peer.addresses.asSequence()
            .mapNotNull(::tcpPortFromMultiaddress)
            .filter { it in MIN_PORT..MAX_PORT }
            .distinct()
            .take(MAX_SCAN_PORTS)
            .toList()
        if (ports.isEmpty()) {
            return emptyList()
        }

        val targets = connectivityManager.allNetworks
            .asSequence()
            .filter(::isLocalNetwork)
            .flatMap { network ->
                connectivityManager.getLinkProperties(network)
                    ?.linkAddresses
                    .orEmpty()
                    .asSequence()
                    .filter { it.address is Inet4Address }
                    .filter { it.address.isSiteLocalAddress }
                    .map { SubnetScanTarget(network, it) }
            }
            .distinctBy { target ->
                "${target.network}:${target.linkAddress.address.hostAddress}"
            }
            .toList()
        if (targets.isEmpty()) {
            return emptyList()
        }

        Log.i(TAG, "Scanning the local subnet for refreshed trusted-peer addresses")
        val probes = targets.flatMap { target ->
            subnetHostAddresses(target.linkAddress).flatMap { address ->
                ports.map { port -> SubnetProbe(target.network, address, port) }
            }
        }.take(MAX_SUBNET_PROBES)
        if (probes.isEmpty()) {
            return emptyList()
        }

        val executor = Executors.newFixedThreadPool(
            minOf(MAX_CONCURRENT_PROBES, probes.size)
        )
        return try {
            executor.invokeAll(
                probes.map { probe ->
                    Callable {
                        if (isTcpPortOpen(probe)) {
                            "/ip4/${probe.address.hostAddress}/tcp/${probe.port}" +
                                "/p2p/${peer.peerId}"
                        } else {
                            null
                        }
                    }
                },
                SUBNET_SCAN_TIMEOUT_MILLIS,
                TimeUnit.MILLISECONDS
            ).asSequence()
                .filterNot { it.isCancelled }
                .mapNotNull { future -> runCatching(future::get).getOrNull() }
                .distinct()
                .take(MAX_PAIRING_ADDRESSES)
                .toList()
        } finally {
            executor.shutdownNow()
        }
    }

    private fun isLocalNetwork(network: Network): Boolean {
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(TRANSPORT_WIFI) ||
            capabilities.hasTransport(TRANSPORT_ETHERNET)
    }

    private fun isTcpPortOpen(probe: SubnetProbe): Boolean {
        return runCatching {
            probe.network.socketFactory.createSocket().use { socket ->
                socket.connect(
                    InetSocketAddress(probe.address, probe.port),
                    TCP_PROBE_TIMEOUT_MILLIS
                )
            }
            true
        }.getOrDefault(false)
    }

    @Suppress("DEPRECATION")
    private fun resolveNext() {
        val serviceInfo = synchronized(lock) {
            if (resolving || pendingResolutions.isEmpty()) {
                return
            }
            resolving = true
            pendingResolutions.removeFirst()
        }
        runCatching {
            nsdManager.resolveService(
                serviceInfo,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        finishResolution()
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        recordResolvedService(serviceInfo)
                        finishResolution()
                    }
                }
            )
        }.onFailure {
            finishResolution()
        }
    }

    @Suppress("DEPRECATION")
    private fun recordResolvedService(serviceInfo: NsdServiceInfo) {
        val peerId = serviceInfo.attributes[PEER_ID_ATTRIBUTE]
            ?.toString(Charsets.UTF_8)
            ?.takeIf(String::isNotBlank)
            ?: return
        val address = serviceInfo.host as? Inet4Address ?: return
        if (peerId == localPeerId || serviceInfo.port !in MIN_PORT..MAX_PORT) {
            return
        }
        val multiaddress =
            "/ip4/${address.hostAddress}/tcp/${serviceInfo.port}/p2p/$peerId"
        synchronized(lock) {
            servicePeerIds[serviceInfo.serviceName] = peerId
            serviceInfoByPeerId[peerId] = serviceInfo
            addressesByPeerId.getOrPut(peerId, ::linkedSetOf).apply {
                add(multiaddress)
                while (size > MAX_PAIRING_ADDRESSES) {
                    remove(first())
                }
            }
            lock.notifyAll()
        }
    }

    private fun finishResolution() {
        synchronized(lock) {
            resolving = false
        }
        resolveNext()
    }

    private fun serviceName(peerId: String): String {
        return "$SERVICE_NAME_PREFIX${peerId.takeLast(SERVICE_PEER_ID_SUFFIX_LENGTH)}"
    }

    companion object {
        private const val TAG = "DeviceSyncDiscovery"
        private const val SERVICE_TYPE = "_wizestream-sync._tcp."
        private const val SERVICE_NAME_PREFIX = "WizeStream-"
        private const val PEER_ID_ATTRIBUTE = "peer"
        private const val SERVICE_PEER_ID_SUFFIX_LENGTH = 16
        private const val DISCOVERY_WAIT_MILLIS = 4_000L
        private const val EMPTY_RESULT_COOLDOWN_MILLIS = 30_000L
        private const val TCP_PROBE_TIMEOUT_MILLIS = 180
        private const val SUBNET_SCAN_TIMEOUT_MILLIS = 3_000L
        private const val MAX_CONCURRENT_PROBES = 32
        private const val MAX_SUBNET_PROBES = 512
        private const val MAX_SCAN_PORTS = 2
        private const val MIN_PORT = 1
        private const val MAX_PORT = 65_535
    }
}

internal fun combinePeerDiscoveryCandidates(
    mdnsAddresses: List<String>,
    subnetAddresses: List<String>
): List<String> {
    return sequence {
        // Preserve mDNS as the first choice, but reserve room for subnet recovery. A stale,
        // non-empty mDNS cache must not prevent dialing a peer that moved to a hotspot subnet.
        mdnsAddresses.firstOrNull()?.let { yield(it) }
        yieldAll(subnetAddresses)
        yieldAll(mdnsAddresses.drop(1))
    }.distinct()
        .take(MAX_PAIRING_ADDRESSES)
        .toList()
}

private data class SubnetScanTarget(
    val network: Network,
    val linkAddress: LinkAddress
)

private data class SubnetProbe(
    val network: Network,
    val address: Inet4Address,
    val port: Int
)

internal fun subnetHostAddresses(linkAddress: LinkAddress): List<Inet4Address> {
    val localAddress = linkAddress.address as? Inet4Address ?: return emptyList()
    return subnetHostAddresses(localAddress, linkAddress.prefixLength)
}

internal fun subnetHostAddresses(
    localAddress: Inet4Address,
    configuredPrefixLength: Int
): List<Inet4Address> {
    if (!localAddress.isSiteLocalAddress) {
        return emptyList()
    }

    // Never scan more than the local /24, even when the configured LAN is broader.
    val prefixLength = maxOf(configuredPrefixLength, MIN_SCAN_PREFIX_LENGTH)
        .coerceIn(MIN_SCAN_PREFIX_LENGTH, IPV4_ADDRESS_BITS)
    val localValue = ipv4ToLong(localAddress)
    val hostBits = IPV4_ADDRESS_BITS - prefixLength
    val hostMask = if (hostBits == IPV4_ADDRESS_BITS) {
        IPV4_MAX_VALUE
    } else {
        (1L shl hostBits) - 1L
    }
    val networkValue = localValue and hostMask.inv() and IPV4_MAX_VALUE
    val broadcastValue = networkValue or hostMask

    return ((networkValue + 1) until broadcastValue)
        .asSequence()
        .filter { it != localValue }
        .map(::longToIpv4)
        .take(MAX_HOSTS_PER_SUBNET)
        .toList()
}

private fun ipv4ToLong(address: Inet4Address): Long {
    return address.address.fold(0L) { value, byte ->
        (value shl Byte.SIZE_BITS) or (byte.toLong() and 0xffL)
    }
}

private fun longToIpv4(value: Long): Inet4Address {
    val bytes = ByteArray(IPV4_ADDRESS_BITS / Byte.SIZE_BITS) { index ->
        (value shr ((3 - index) * Byte.SIZE_BITS)).toByte()
    }
    return InetAddress.getByAddress(bytes) as Inet4Address
}

private const val IPV4_ADDRESS_BITS = 32
private const val IPV4_MAX_VALUE = 0xffff_ffffL
private const val MIN_SCAN_PREFIX_LENGTH = 24
private const val MAX_HOSTS_PER_SUBNET = 254
