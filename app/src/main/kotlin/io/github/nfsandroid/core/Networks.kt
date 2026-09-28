package io.github.nfsandroid.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import io.github.nfsandroid.data.Server
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Every network the phone has now (a VPN, Wi-Fi or cable), with the subnets it gives the phone:
 * a server tied to one ("vpn" or "lan", and optionally its subnet) is tried only while it is up.
 * Only one VPN can be up at a time; which one it is shows in the address it gives the phone.
 */
object Networks {
    /** A network: "vpn", "lan" (Wi-Fi or cable) or "other", and the phone's subnets on it. */
    data class Link(val kind: String, val subnets: List<String>)

    private val links = ConcurrentHashMap<Network, Link>()
    private val current = MutableStateFlow(emptyList<Link>())
    val state: StateFlow<List<Link>> = current

    fun init(context: Context) {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        // By default a request leaves VPNs out.
        val all = NetworkRequest.Builder().removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build()
        connectivity.registerNetworkCallback(all, object : ConnectivityManager.NetworkCallback() {
            override fun onLinkPropertiesChanged(network: Network, link: LinkProperties) {
                val kind = kind(connectivity.getNetworkCapabilities(network))
                val before = links.put(network, Link(kind, link.linkAddresses.map { Subnet.of(it.address, it.prefixLength) }))
                publish(before != links[network])
            }

            override fun onLost(network: Network) = publish(links.remove(network) != null)
        })
    }

    private fun publish(changed: Boolean) {
        if (!changed) return
        current.value = links.values.toList()
        // A server whose network came back is tried again at once; one whose network went, closed.
        Mounts.networksChanged()
    }

    private fun kind(caps: NetworkCapabilities?) = when {
        caps == null -> "other"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "lan"
        else -> "other"
    }

    /** Whether the network [server] is tied to is up (always, for one tied to none). */
    fun allows(server: Server, now: List<Link> = current.value) = server.networkKind == "any" ||
        now.any { it.kind == server.networkKind && (server.networkSubnet.isBlank() || it.subnets.any { s -> Subnet.within(s, server.networkSubnet) }) }

    /** The subnet of the network of this kind up now, to tie a server to it (IPv4 first). */
    fun subnetNow(kind: String): String? =
        current.value.filter { it.kind == kind }.flatMap { it.subnets }.sortedBy { ':' in it }.firstOrNull()
}
