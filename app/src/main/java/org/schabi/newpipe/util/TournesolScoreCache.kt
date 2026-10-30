package org.schabi.newpipe.util

data class TournesolCacheEntry(val score: Long, val unsafeReasons: List<String>)

open class TournesolLruScoreCache(private val maxEntries: Int = 200) {
    private val cache = LinkedHashMap<String, TournesolCacheEntry>(32, 0.75f, true)

    fun put(url: String, score: Long, unsafeReasons: List<String> = emptyList()) {
        synchronized(cache) {
            cache[url] = TournesolCacheEntry(score, unsafeReasons)
            if (cache.size > maxEntries) {
                cache.remove(cache.keys.first())
            }
        }
    }

    fun getEntry(url: String): TournesolCacheEntry? = synchronized(cache) { cache[url] }

    fun clear() = synchronized(cache) { cache.clear() }
}

object TournesolScoreCache : TournesolLruScoreCache()
