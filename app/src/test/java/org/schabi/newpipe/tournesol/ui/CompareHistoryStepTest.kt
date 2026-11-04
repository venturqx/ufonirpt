package org.schabi.newpipe.tournesol.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.tournesol.CompareComparisonVideo

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

    @Test
    fun `visible indices skip the avoided video and keep absolute positions`() {
        assertEquals(listOf(0, 2, 3), visibleHistoryIndices(entries, historyEntry("b")))
        assertEquals(listOf(0, 1, 2, 3), visibleHistoryIndices(entries, null))
    }

    private fun suggestion(url: String) = CompareComparisonVideo(
        uid = "history:0:$url",
        title = "title",
        uploader = "uploader",
        thumbnailUrl = null,
        videoUrl = url,
        serviceId = 0
    )

    @Test
    fun `step down from a suggestion found in history moves past it`() {
        assertEquals(2, stepDownIndex(entries, 0, suggestion("b"), null))
        assertEquals(3, stepDownIndex(entries, 0, suggestion("b"), historyEntry("c")))
        assertNull(stepDownIndex(entries, 0, suggestion("d"), null))
    }

    @Test
    fun `step down from a suggestion missing from history starts at the newest entry`() {
        assertEquals(0, stepDownIndex(entries, 2, suggestion("z"), null))
        assertEquals(1, stepDownIndex(entries, 2, suggestion("z"), historyEntry("a")))
        assertNull(stepDownIndex(emptyList(), 0, suggestion("z"), null))
    }

    @Test
    fun `step down without a suggestion steps from the current index`() {
        assertEquals(2, stepDownIndex(entries, 1, null, null))
    }

    @Test
    fun `step up without a suggestion steps from the current index`() {
        assertEquals(0, stepUpIndex(entries, 1, null, null))
        assertEquals(0, stepUpIndex(entries, 2, null, historyEntry("b")))
        assertNull(stepUpIndex(entries, 0, null, null))
    }

    @Test
    fun `step up from a suggestion moves above it or nowhere when missing from history`() {
        assertEquals(1, stepUpIndex(entries, 0, suggestion("c"), null))
        assertNull(stepUpIndex(entries, 0, suggestion("a"), null))
        assertNull(stepUpIndex(entries, 2, suggestion("z"), null))
    }
}
