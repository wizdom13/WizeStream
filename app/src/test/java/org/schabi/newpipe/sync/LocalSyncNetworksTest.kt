package org.schabi.newpipe.sync

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class LocalSyncNetworksTest {
    @Test
    fun `physical LAN remains eligible when the active network is a VPN`() {
        val manager = mock(ConnectivityManager::class.java)
        val wifi = mock(Network::class.java)
        val ethernet = mock(Network::class.java)
        val vpn = mock(Network::class.java)
        val mobile = mock(Network::class.java)
        @Suppress("DEPRECATION")
        `when`(manager.allNetworks).thenReturn(arrayOf(vpn, mobile, wifi, ethernet))
        `when`(manager.activeNetwork).thenReturn(vpn)
        doReturn(capabilities(NetworkCapabilities.TRANSPORT_WIFI)).`when`(manager).getNetworkCapabilities(wifi)
        doReturn(capabilities(NetworkCapabilities.TRANSPORT_ETHERNET)).`when`(manager).getNetworkCapabilities(ethernet)
        doReturn(capabilities(NetworkCapabilities.TRANSPORT_CELLULAR)).`when`(manager).getNetworkCapabilities(mobile)
        doReturn(
            capabilities(NetworkCapabilities.TRANSPORT_VPN, NetworkCapabilities.TRANSPORT_WIFI, NetworkCapabilities.TRANSPORT_ETHERNET)
        ).`when`(manager).getNetworkCapabilities(vpn)

        assertEquals(listOf(wifi, ethernet), localSyncNetworks(manager))
    }

    @Test
    fun `VPN transports and disappearing networks are never treated as a local LAN`() {
        assertFalse(capabilities(NetworkCapabilities.TRANSPORT_VPN, NetworkCapabilities.TRANSPORT_WIFI).isLocalSyncNetwork())
        assertFalse(capabilities(NetworkCapabilities.TRANSPORT_VPN, NetworkCapabilities.TRANSPORT_ETHERNET).isLocalSyncNetwork())
        assertFalse(capabilities(NetworkCapabilities.TRANSPORT_VPN).isLocalSyncNetwork())
        assertFalse(capabilities(NetworkCapabilities.TRANSPORT_CELLULAR).isLocalSyncNetwork())
        assertFalse((null as NetworkCapabilities?).isLocalSyncNetwork())
    }

    private fun capabilities(vararg transports: Int): NetworkCapabilities = mock(NetworkCapabilities::class.java).also { capabilities ->
        transports.forEach { `when`(capabilities.hasTransport(it)).thenReturn(true) }
    }
}
