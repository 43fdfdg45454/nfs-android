package io.github.nfsandroid.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import io.github.nfsandroid.log.LogCategory
import io.github.nfsandroid.log.LogLevel
import io.github.nfsandroid.log.NfsLog
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The default network: when it changes (Wi-Fi and mobile data, a VPN coming up) or its addresses
 * do, the mounts reconnect at once instead of waiting for their connections to time out. Android
 * blocking the app's network (in the background) goes to the log.
 */
object NetworkMonitor {
    /** What the diagnostics screen shows: the network, and whether Android blocks the app's. */
    val state = MutableStateFlow("—")
    private var current: Pair<Network, Set<String>>? = null

    fun init(context: Context) {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        connectivity.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onLinkPropertiesChanged(network: Network, link: LinkProperties) {
                val addresses = link.linkAddresses.map { it.address.hostAddress.orEmpty() }.toSet()
                val previous = current
                current = network to addresses
                val kind = describe(connectivity.getNetworkCapabilities(network))
                state.value = "$kind (${link.interfaceName})"
                if (previous != null && previous != current) {
                    NfsLog.log(LogLevel.INFO, LogCategory.NETWORK, null, "changed", "kind" to kind, "interface" to link.interfaceName)
                    Mounts.networkChanged()
                }
            }

            override fun onLost(network: Network) {
                if (current?.first == network) state.value = "—"
                NfsLog.log(LogLevel.WARN, LogCategory.NETWORK, null, "lost")
            }

            override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
                NfsLog.log(if (blocked) LogLevel.WARN else LogLevel.INFO, LogCategory.NETWORK, null, if (blocked) "blocked by Android" else "allowed again")
                state.value = state.value.substringBefore(" · ") + if (blocked) " · blocked" else ""
            }
        })
    }

    private fun describe(caps: NetworkCapabilities?): String = when {
        caps == null -> "?"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile data"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        else -> "other"
    }
}
