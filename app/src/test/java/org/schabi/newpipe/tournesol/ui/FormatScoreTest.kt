package org.schabi.newpipe.tournesol.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatScoreTest {
    @Test
    fun `small negative score rounds to plain zero`() {
        assertEquals("0", formatScore(-0.4, 0))
    }

    @Test
    fun `negative zero formats as zero`() {
        assertEquals("0", formatScore(-0.0, 0))
        assertEquals("0.0", formatScore(-0.0, 1))
    }

    @Test
    fun `small negative score with one decimal rounds to plain zero`() {
        assertEquals("0.0", formatScore(-0.04, 1))
    }

    @Test
    fun `negative scores keep their sign`() {
        assertEquals("-5", formatScore(-4.6, 0))
        assertEquals("-6.5", formatScore(-6.5, 1))
    }

    @Test
    fun `positive scores are rounded`() {
        assertEquals("2", formatScore(1.6, 0))
        assertEquals("56.7", formatScore(56.66, 1))
        assertEquals("0", formatScore(0.2, 0))
    }
}
