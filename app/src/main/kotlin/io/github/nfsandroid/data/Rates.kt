package io.github.nfsandroid.data

/** The units a server's transfer caps are typed in (k = 1000; b: bits, B: bytes), as bytes per second. */
object Rates {
    val UNITS = listOf("kbps" to "kb/s", "Mbps" to "Mb/s", "kBps" to "kB/s", "MBps" to "MB/s")
    private val BYTES = mapOf("kbps" to 125L, "Mbps" to 125_000L, "kBps" to 1_000L, "MBps" to 1_000_000L)

    fun bytes(amount: Int, unit: String) = amount.coerceAtLeast(0) * (BYTES[unit] ?: BYTES.getValue("Mbps"))
}
