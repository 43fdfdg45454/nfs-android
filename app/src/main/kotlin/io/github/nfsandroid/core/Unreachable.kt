package io.github.nfsandroid.core

import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Servers that did not answer just now: for a while every call to them fails at once, instead of
 * each one (a file manager checking its storages, a picker listing) waiting for another attempt.
 * A new network (the VPN back, Wi-Fi and mobile data) clears them: the next call tries again.
 */
object Unreachable {
    private const val REMEMBER_MS = 30_000L
    private val failed = ConcurrentHashMap<String, Pair<Long, String>>()

    fun check(id: String) {
        val (at, why) = failed[id] ?: return
        if (System.currentTimeMillis() - at < REMEMBER_MS) throw IOException(why) else failed.remove(id)
    }

    fun failed(id: String, why: String) {
        failed[id] = System.currentTimeMillis() to why
    }

    fun reachable(id: String) = failed.remove(id)

    fun clear() = failed.clear()

    /** The servers that failed recently, and why: for the servers' cards. */
    fun now(): Map<String, String> = failed.filterValues { (at, _) -> System.currentTimeMillis() - at < REMEMBER_MS }.mapValues { it.value.second }
}
