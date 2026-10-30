package org.schabi.newpipe.info_list.holder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SCORE = 42L
private const val NEGATIVE_SCORE = -7L
private const val DATE = "3d"
private const val VIEWS = "1.2K"
private const val COMPARISONS = 5
private const val NO_COMPARISONS = -1

class DetailSegmentTest {
    private val score = DetailSegment.Score(SCORE, false)
    private val date = DetailSegment.Date(DATE)
    private val views = DetailSegment.Views(VIEWS)
    private val comparisons = DetailSegment.Comparisons(COMPARISONS)

    @Test
    fun `all segments come in score date views comparisons order`() {
        val segments = detailSegments(SCORE, false, DATE, VIEWS, COMPARISONS)

        assertEquals(listOf(score, date, views, comparisons), segments)
    }

    @Test
    fun `score keeps its insufficient flag`() {
        val segments = detailSegments(SCORE, true, null, null, NO_COMPARISONS)

        assertEquals(listOf(DetailSegment.Score(SCORE, true)), segments)
    }

    @Test
    fun `zero and negative scores are shown`() {
        assertEquals(
            listOf(DetailSegment.Score(0L, false)),
            detailSegments(0L, false, null, null, NO_COMPARISONS)
        )
        assertEquals(
            listOf(DetailSegment.Score(NEGATIVE_SCORE, false)),
            detailSegments(NEGATIVE_SCORE, false, null, null, NO_COMPARISONS)
        )
    }

    @Test
    fun `null score is omitted even when flagged insufficient`() {
        val segments = detailSegments(null, true, DATE, VIEWS, COMPARISONS)

        assertEquals(listOf(date, views, comparisons), segments)
    }

    @Test
    fun `null date is omitted`() {
        val segments = detailSegments(SCORE, false, null, VIEWS, COMPARISONS)

        assertEquals(listOf(score, views, comparisons), segments)
    }

    @Test
    fun `empty date is omitted`() {
        val segments = detailSegments(SCORE, false, "", VIEWS, COMPARISONS)

        assertEquals(listOf(score, views, comparisons), segments)
    }

    @Test
    fun `null view text is omitted`() {
        val segments = detailSegments(SCORE, false, DATE, null, COMPARISONS)

        assertEquals(listOf(score, date, comparisons), segments)
    }

    @Test
    fun `empty view text is still a segment`() {
        val segments = detailSegments(null, false, null, "", NO_COMPARISONS)

        assertEquals(listOf(DetailSegment.Views("")), segments)
    }

    @Test
    fun `negative comparisons are omitted`() {
        val segments = detailSegments(SCORE, false, DATE, VIEWS, NO_COMPARISONS)

        assertEquals(listOf(score, date, views), segments)
    }

    @Test
    fun `zero comparisons are shown`() {
        val segments = detailSegments(null, false, null, null, 0)

        assertEquals(listOf(DetailSegment.Comparisons(0)), segments)
    }

    @Test
    fun `all absent gives an empty list`() {
        assertTrue(detailSegments(null, false, null, null, NO_COMPARISONS).isEmpty())
    }
}
