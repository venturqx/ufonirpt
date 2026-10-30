package org.schabi.newpipe.util

import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TournesolScoreCacheTest {

    @Test
    fun `getEntry returns the score and reasons stored by put`() {
        val cache = TournesolLruScoreCache()
        cache.put("url", 42L, listOf("insufficient_trust"))

        assertEquals(TournesolCacheEntry(42L, listOf("insufficient_trust")), cache.getEntry("url"))
    }

    @Test
    fun `put defaults to no unsafe reasons`() {
        val cache = TournesolLruScoreCache()
        cache.put("url", 42L)

        assertEquals(TournesolCacheEntry(42L, emptyList()), cache.getEntry("url"))
    }

    @Test
    fun `getEntry returns null for an unknown url`() {
        assertNull(TournesolLruScoreCache().getEntry("missing"))
    }

    @Test
    fun `a read protects an entry from eviction`() {
        val cache = TournesolLruScoreCache(maxEntries = 2)
        cache.put("old", 1L)
        cache.put("new", 2L)

        cache.getEntry("old")
        cache.put("third", 3L)

        assertEquals(1L, cache.getEntry("old")?.score)
        assertNull(cache.getEntry("new"))
    }

    @Test
    fun `exceeding capacity evicts the oldest entry`() {
        val cache = TournesolLruScoreCache(maxEntries = 2)
        cache.put("first", 1L)
        cache.put("second", 2L)
        cache.put("third", 3L)

        assertNull(cache.getEntry("first"))
        assertEquals(2L, cache.getEntry("second")?.score)
        assertEquals(3L, cache.getEntry("third")?.score)
    }

    @Test
    fun `overwriting a url replaces the entry without evicting another`() {
        val cache = TournesolLruScoreCache(maxEntries = 2)
        cache.put("first", 1L, listOf("insufficient_trust"))
        cache.put("second", 2L)

        cache.put("first", 10L)

        assertEquals(TournesolCacheEntry(10L, emptyList()), cache.getEntry("first"))
        assertEquals(2L, cache.getEntry("second")?.score)
    }

    @Test
    fun `clear removes every entry`() {
        val cache = TournesolLruScoreCache()
        cache.put("first", 1L)
        cache.put("second", 2L)

        cache.clear()

        assertNull(cache.getEntry("first"))
        assertNull(cache.getEntry("second"))
    }

    @Test
    fun `a single entry capacity keeps only the latest put`() {
        val cache = TournesolLruScoreCache(maxEntries = 1)
        cache.put("first", 1L)
        cache.put("second", 2L)

        assertNull(cache.getEntry("first"))
        assertEquals(2L, cache.getEntry("second")?.score)
    }

    @Test
    fun `concurrent puts never exceed capacity`() {
        val capacity = 16
        val threadCount = 8
        val putsPerThread = 1_000
        val cache = TournesolLruScoreCache(maxEntries = capacity)
        val start = CountDownLatch(1)

        val workers = (0 until threadCount).map { t ->
            thread {
                start.await()
                repeat(putsPerThread) { i -> cache.put("url-$t-$i", i.toLong()) }
            }
        }
        start.countDown()
        workers.forEach { it.join() }

        val survivors = (0 until threadCount).sumOf { t ->
            (0 until putsPerThread).count { i -> cache.getEntry("url-$t-$i") != null }
        }
        assertEquals(capacity, survivors)
    }
}
