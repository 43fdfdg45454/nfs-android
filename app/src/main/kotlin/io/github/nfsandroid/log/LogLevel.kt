package io.github.nfsandroid.log

/** How much a server logs: each level keeps what the one before it does and adds its own. */
enum class LogLevel {
    ERROR, WARN, INFO, DEBUG;

    val key get() = name.lowercase()

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key } ?: INFO
    }
}

/** What a line is about, its third column: each server's Advanced section says what each holds. */
enum class LogCategory { CONNECTION, FILES, UPLOADS, THUMBNAILS, TEST, NETWORK, SERVICE }
