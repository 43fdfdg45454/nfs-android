package io.github.nfsandroid.core

import java.net.InetAddress

/** Subnets as "address/length" (IPv4 or IPv6), and whether one lies within another. */
object Subnet {
    /** The subnet of [address] with a prefix of [length] bits: 198.51.100.7 and 24 give 198.51.100.0/24. */
    fun of(address: InetAddress, length: Int): String {
        val bytes = mask(address.address, length)
        return "${InetAddress.getByAddress(bytes).hostAddress}/$length"
    }

    /** Whether the phone's [subnet] (its address and prefix) lies within [wanted]. */
    fun within(subnet: String, wanted: String): Boolean {
        val (address, _) = parse(subnet) ?: return false
        val (network, length) = parse(wanted) ?: return false
        return address.size == network.size && mask(address, length).contentEquals(mask(network, length))
    }

    fun valid(subnet: String) = parse(subnet) != null

    private fun parse(subnet: String): Pair<ByteArray, Int>? = runCatching {
        val (address, length) = subnet.trim().split('/').let { it[0] to (it.getOrNull(1)?.toInt()) }
        // Only literal addresses: a name here would be looked up on the network.
        require(address.isNotEmpty() && address.all { it.isDigit() || it in ".:abcdefABCDEF" })
        val bytes = InetAddress.getByName(address).address
        bytes to (length ?: (bytes.size * 8)).also { require(it in 0..bytes.size * 8) }
    }.getOrNull()

    private fun mask(bytes: ByteArray, length: Int) = ByteArray(bytes.size) { i ->
        val bits = (length - i * 8).coerceIn(0, 8)
        (bytes[i].toInt() and (0xFF shl (8 - bits))).toByte()
    }
}
