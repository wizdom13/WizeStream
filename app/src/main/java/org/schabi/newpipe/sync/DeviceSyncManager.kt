/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import android.content.Context
import android.os.Build

class DeviceSyncManager private constructor(context: Context) {
    private val stateRepository = AndroidSyncStateRepository(context.applicationContext)
    private val deviceName = listOf(Build.MANUFACTURER, Build.MODEL)
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .joinToString(" ")
    private val node by lazy {
        Libp2pSyncNode(
            stateRepository = stateRepository,
            pairingSecurity = PairingSecurity(),
            deviceName = deviceName,
            advertisedAddressProvider = AndroidNetworkAddressProvider::addresses
        )
    }

    val peerId: String
        get() = stateRepository.loadOrCreateIdentity().peerId.toBase58()

    val trustedPeers: List<TrustedPeer>
        get() = stateRepository.getTrustedPeers()

    @Synchronized
    fun createPairingCode(): String {
        node.start()
        return node.createPairingCode()
    }

    @Synchronized
    fun pair(pairingCode: String): TrustedPeer {
        node.start()
        return node.pair(pairingCode)
    }

    fun clearTrustedPeers() {
        stateRepository.clearTrustedPeers()
    }

    companion object {
        @Volatile
        private var instance: DeviceSyncManager? = null

        fun get(context: Context): DeviceSyncManager {
            return instance ?: synchronized(this) {
                instance ?: DeviceSyncManager(context).also { instance = it }
            }
        }
    }
}
