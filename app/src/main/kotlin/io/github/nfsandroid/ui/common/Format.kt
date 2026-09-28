package io.github.nfsandroid.ui.common

import android.content.Context
import io.github.nfsandroid.R
import uniffi.nfscore.MountStats

/** Amounts and a server's connections in words, for the screens and the notification. */
object Format {
    private val UNITS = listOf("B", "KB", "MB", "GB", "TB")

    /** In units of 1024, as the sizes typed in the settings: 4 GB of cache shows as 4 GB. */
    fun bytes(bytes: Long): String {
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < UNITS.lastIndex) {
            value /= 1024
            unit++
        }
        return (if (unit == 0 || value >= 100 || value % 1.0 == 0.0) "%.0f %s" else "%.1f %s").format(value, UNITS[unit])
    }

    fun rate(context: Context, perSecond: Long) = context.getString(R.string.rate, bytes(perSecond))

    /** "QUIC 4/4 · 3 in flight · RTT 102 ms · 0.3% lost · reading 2 files · 1 reconnection". */
    fun connections(context: Context, s: MountStats): String {
        val parts = mutableListOf("${if (s.quic) "QUIC" else "TCP"} ${s.alive}/${s.lanes}")
        parts += context.getString(R.string.in_flight, s.inFlight.toInt())
        if (s.rttMs > 0u) parts += context.getString(R.string.rtt, s.rttMs.toInt())
        loss(s)?.takeIf { it > 0 }?.let { parts += context.getString(R.string.loss, it) }
        if (s.openFiles > 0u) parts += context.resources.getQuantityString(R.plurals.reading, s.openFiles.toInt(), s.openFiles.toInt())
        reconnections(s).takeIf { it > 0 }?.let { parts += context.resources.getQuantityString(R.plurals.reconnections, it, it) }
        return parts.joinToString(" · ")
    }

    /** The network a server is tied to: "VPN · 198.51.100.0/24", "Wi-Fi or cable". */
    fun network(context: Context, server: io.github.nfsandroid.data.Server): String {
        val kind = if (server.networkKind == "vpn") "VPN" else context.getString(R.string.network_lan)
        return if (server.networkSubnet.isBlank()) kind else "$kind · ${server.networkSubnet}"
    }

    /** Packets lost of those sent on the QUIC path, in percent. */
    fun loss(s: MountStats): Double? = s.packetsSent.takeIf { it > 0u }?.let { s.packetsLost.toDouble() * 100 / it.toDouble() }

    fun reconnections(s: MountStats) = (s.connects.toInt() - s.lanes.toInt()).coerceAtLeast(0)
}
