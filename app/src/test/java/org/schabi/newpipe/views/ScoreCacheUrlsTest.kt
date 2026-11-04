package org.schabi.newpipe.views

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreCacheUrlsTest {
    @Test
    fun `identical urls are cached once`() {
        assertEquals(listOf("a"), ScoreCacheUrls.of("a", "a"))
    }

    @Test
    fun `different urls are both cached with the original first`() {
        assertEquals(listOf("original", "url"), ScoreCacheUrls.of("original", "url"))
    }
}
