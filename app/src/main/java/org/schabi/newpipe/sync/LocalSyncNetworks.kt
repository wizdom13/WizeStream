package org.schabi.newpipe.sync

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

/** A VPN can also report Wi-Fi or Ethernet as its underlying transport. */
internal fun NetworkCapabilities?.isLocalSyncNetwork(): Boolean = this != null &&
    !hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
    DeviceSyncBackgroundPolicy.hasLocalTransport(
        wifi = hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
        ethernet = hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    )

/** Inspect the physical LAN even when the default network is a VPN or mobile data. */
@Suppress("DEPRECATION")
internal fun localSyncNetworks(manager: ConnectivityManager): List<Network> = manager.allNetworks.filter { manager.getNetworkCapabilities(it).isLocalSyncNetwork() }
