package org.schabi.newpipe.tournesol.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareHistoryStepTest {
    private val entries = listOf("a", "b", "c", "d").map { historyEntry(it) }

    @Test
    fun `same video matches on service and url, not stream id`() {
        assertTrue(isSameVideo(historyEntry("a", streamId = 1), historyEntry("a", streamId = -7)))
        assertFalse(isSameVideo(historyEntry("a"), historyEntry("a", serviceId = 1)))
        assertFalse(isSameVideo(historyEntry("a"), null))
    }

    @Test
    fun `step moves one entry when nothing is avoided`() {
        assertEquals(2, stepHistoryIndex(entries, 1, 1, null))
        assertEquals(0, stepHistoryIndex(entries, 1, -1, null))
    }

    @Test
    fun `step skips the video shown on the other side`() {
        assertEquals(3, stepHistoryIndex(entries, 1, 1, historyEntry("c")))
        assertEquals(0, stepHistoryIndex(entries, 2, -1, historyEntry("b")))
    }

    @Test
    fun `step returns null at the ends or when only the avoided video remains`() {
        assertNull(stepHistoryIndex(entries, 3, 1, null))
        assertNull(stepHistoryIndex(entries, 0, -1, null))
        assertNull(stepHistoryIndex(entries, 2, 1, historyEntry("d")))
    }

    @Test
    fun `distinct index keeps a non-colliding selection`() {
        assertEquals(1, distinctHistoryIndex(entries, 1, historyEntry("c")))
    }

    @Test
    fun `distinct index moves forward first, then backward`() {
        assertEquals(2, distinctHistoryIndex(entries, 1, historyEntry("b")))
        assertEquals(2, distinctHistoryIndex(entries, 3, historyEntry("d")))
    }

    @Test
    fun `distinct index is null when the only entry collides`() {
        assertNull(distinctHistoryIndex(listOf(historyEntry("a")), 0, historyEntry("a")))
    }
}
