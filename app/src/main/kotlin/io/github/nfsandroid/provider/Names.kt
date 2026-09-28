package io.github.nfsandroid.provider

import uniffi.nfscore.Mount
import uniffi.nfscore.NfsException

/** Paths under an export, and names that do not overwrite anything. */
object Names {
    /** [name] in the directory at [dir] ("" is the export's root). */
    fun join(dir: String, name: String) = if (dir.isEmpty()) name else "$dir/$name"

    /** [name], else "name (1).ext", "name (2).ext"...: the first one [dir] does not have. */
    suspend fun free(mount: Mount, dir: String, name: String): String {
        val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
        val (base, extension) = name.substring(0, dot) to name.substring(dot)
        for (n in 0..999) {
            val candidate = if (n == 0) name else "$base ($n)$extension"
            try {
                mount.stat(join(dir, candidate))
            } catch (e: NfsException.NotFound) {
                return candidate
            }
        }
        throw NfsException.AlreadyExists("$dir: no free name for $name")
    }
}
