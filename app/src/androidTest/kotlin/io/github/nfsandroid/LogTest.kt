package io.github.nfsandroid

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nfsandroid.data.Server
import io.github.nfsandroid.log.LogCategory
import io.github.nfsandroid.log.LogLevel
import io.github.nfsandroid.log.NfsLog
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** nfs-log.txt: one line per event in columns, each server up to its level or nothing at all. */
@RunWith(AndroidJUnit4::class)
class LogTest {
    @Test
    fun eachServerLogsUpToItsLevelOrNothing() {
        val server = Server(id = "log", name = "Log test", host = "192.0.2.1")
        val mark = "mark=${System.nanoTime()}"
        fun log(level: LogLevel, category: LogCategory, server: Server?, event: String) =
            NfsLog.log(level, category, server, event, "mark" to mark.substringAfter('='), "note" to "with spaces")
        log(LogLevel.INFO, LogCategory.TEST, server, "kept")
        log(LogLevel.DEBUG, LogCategory.TEST, server, "dropped: over its level")
        log(LogLevel.ERROR, LogCategory.TEST, server.copy(logEnabled = false), "dropped: its log is off")
        log(LogLevel.WARN, LogCategory.TEST, server.copy(logLevel = "warn"), "kept")
        log(LogLevel.INFO, LogCategory.NETWORK, null, "kept")
        val lines = NfsLog.file(Provider.context).readLines().filter { mark in it }.map { it.substringAfter(' ') }
        assertEquals(
            listOf(
                "INFO  test       \"Log test\" kept $mark note=\"with spaces\"",
                "WARN  test       \"Log test\" kept $mark note=\"with spaces\"",
                "INFO  network    - kept $mark note=\"with spaces\"",
            ),
            lines,
        )
    }
}
