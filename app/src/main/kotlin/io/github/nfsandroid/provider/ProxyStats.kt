package io.github.nfsandroid.provider

import java.util.concurrent.atomic.AtomicLong

/** What the file proxy served, for diagnosis and the tests: calls and time spent. */
object ProxyStats {
    /** Reads served, for diagnosis: calls, bytes, and nanoseconds in the core and in all. */
    object Reads {
        val calls = AtomicLong()
        val bytes = AtomicLong()
        val coreNanos = AtomicLong()
        val totalNanos = AtomicLong()
        fun reset() = listOf(calls, bytes, coreNanos, totalNanos).forEach { it.set(0) }
        override fun toString(): String {
            val n = maxOf(calls.get(), 1)
            return "${calls.get()} reads of ${bytes.get() / n / 1024} KiB, ${coreNanos.get() / n / 1000} µs in the core, " +
                "${totalNanos.get() / n / 1000} µs in all"
        }
    }

    /** Writes served, for diagnosis: the app's calls, the core's, and nanoseconds in each. */
    object Writes {
        val calls = AtomicLong()
        val core = AtomicLong()
        val nanos = AtomicLong()
        val coreNanos = AtomicLong()
        fun reset() = listOf(calls, core, nanos, coreNanos).forEach { it.set(0) }
        override fun toString() = "${calls.get()} writes into ${core.get()} core calls; " +
            "${nanos.get() / maxOf(calls.get(), 1) / 1000} µs per write, ${coreNanos.get() / maxOf(core.get(), 1) / 1000} µs per core call"
    }
}
