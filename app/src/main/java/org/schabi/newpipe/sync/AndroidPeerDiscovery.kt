/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import java.net.Inet4Address
import java.util.ArrayDeque

/**
 * Advertises and discovers running WizeStream synchronization listeners on the local network.
 *
 * Discovery does not establish trust. A discovered address is used only for dialing the already
 * trusted PeerID, which is authenticated by the libp2p Noise connection before it is saved.
 */
internal class AndroidPeerDiscovery(context: Context) {
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

    fun addressesFor(peerId: String): List<String> {
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
        private const val MIN_PORT = 1
        private const val MAX_PORT = 65_535
    }
}
