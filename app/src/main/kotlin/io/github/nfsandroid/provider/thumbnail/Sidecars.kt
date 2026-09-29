package io.github.nfsandroid.provider.thumbnail

import android.graphics.Bitmap

/**
 * Images next to a file, as media servers keep them (Kodi, Jellyfin, Plex): the file's own first
 * (movie-poster.jpg, movie-thumb.jpg, movie.jpg), then its folder's (poster.jpg, folder.jpg,
 * cover.jpg). A folder's listing is kept 30 s: its files' thumbnails are asked for together.
 */
object Sidecars {
    private val EXTENSIONS = listOf("jpg", "jpeg", "png", "webp")
    private const val KEEP_MS = 30_000L
    private val listings = object : LinkedHashMap<String, Pair<Long, Map<String, String>>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, Map<String, String>>>) = size > 64
    }

    fun names(file: String): List<String> {
        val base = file.substringBeforeLast('.')
        val stems = listOf("$base-poster", "$base-thumb", "$base-cover", base, "poster", "folder", "cover")
        return stems.flatMap { stem -> EXTENSIONS.map { "$stem.$it" } }
    }

    suspend fun find(t: Target): Bitmap? {
        val dir = t.path.substringBeforeLast('/', "")
        val present = listing(t, dir)
        val name = names(t.path.substringAfterLast('/')).firstNotNullOfOrNull { present[it.lowercase()] } ?: return null
        return t.mount.read(if (dir.isEmpty()) name else "$dir/$name").use { image -> decode(t.size) { t.budget.reader(image).stream() } }
    }

    private suspend fun listing(t: Target, dir: String): Map<String, String> {
        val key = "${t.server.id}:$dir"
        val now = System.currentTimeMillis()
        synchronized(listings) { listings[key]?.takeIf { now - it.first < KEEP_MS }?.let { return it.second } }
        val names = t.mount.list(dir).associate { it.name.lowercase() to it.name }
        synchronized(listings) { listings[key] = now to names }
        return names
    }
}
