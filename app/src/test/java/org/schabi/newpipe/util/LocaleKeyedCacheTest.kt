package org.schabi.newpipe.util

import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Test

class LocaleKeyedCacheTest {

    @Test
    fun `same locale reuses the built value`() {
        val built = AtomicInteger()
        val cache = LocaleKeyedCache { built.incrementAndGet() }

        assertEquals(1, cache.get(Locale.ENGLISH))
        assertEquals(1, cache.get(Locale.ENGLISH))
    }

    @Test
    fun `a locale change rebuilds`() {
        val built = AtomicInteger()
        val cache = LocaleKeyedCache { built.incrementAndGet() }

        assertEquals(1, cache.get(Locale.ENGLISH))
        assertEquals(2, cache.get(Locale.FRENCH))
        assertEquals(2, cache.get(Locale.FRENCH))
    }

    @Test
    fun `the factory receives the requested locale`() {
        val cache = LocaleKeyedCache { it.language }

        assertEquals("en", cache.get(Locale.ENGLISH))
        assertEquals("fr", cache.get(Locale.FRENCH))
    }

    @Test
    fun `each thread gets its own instance`() {
        val built = AtomicInteger()
        val cache = LocaleKeyedCache { built.incrementAndGet() }

        val mine = cache.get(Locale.ENGLISH)
        var theirs = 0
        thread { theirs = cache.get(Locale.ENGLISH) }.join()

        assertEquals(1, mine)
        assertEquals(2, theirs)
        assertEquals(1, cache.get(Locale.ENGLISH))
    }
}
