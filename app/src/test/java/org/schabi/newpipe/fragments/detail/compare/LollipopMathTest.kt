package org.schabi.newpipe.fragments.detail.compare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.fragments.detail.SCORE_MAX
import org.schabi.newpipe.fragments.detail.SCORE_MIN

class LollipopMathTest {
    private val delta = 0.001f

    @Test
    fun `score survives a round trip on an inverted vertical axis`() {
        for (score in SCORE_MIN..SCORE_MAX) {
            val y = scoreToPosition(score, minScoreAt = 300f, maxScoreAt = 20f)
            assertEquals(score, positionToScore(y, minScoreAt = 300f, maxScoreAt = 20f))
        }
    }

    @Test
    fun `score survives a round trip on a horizontal axis`() {
        for (score in SCORE_MIN..SCORE_MAX) {
            val x = scoreToPosition(score, minScoreAt = 28f, maxScoreAt = 452f)
            assertEquals(score, positionToScore(x, minScoreAt = 28f, maxScoreAt = 452f))
        }
    }

    @Test
    fun `axis ends map to score bounds`() {
        assertEquals(SCORE_MIN, positionToScore(100f, minScoreAt = 100f, maxScoreAt = 300f))
        assertEquals(SCORE_MAX, positionToScore(300f, minScoreAt = 100f, maxScoreAt = 300f))
        assertEquals(0, positionToScore(200f, minScoreAt = 100f, maxScoreAt = 300f))
    }

    @Test
    fun `score positions match axis ends and centre`() {
        assertEquals(300f, scoreToPosition(SCORE_MIN, minScoreAt = 300f, maxScoreAt = 100f), delta)
        assertEquals(100f, scoreToPosition(SCORE_MAX, minScoreAt = 300f, maxScoreAt = 100f), delta)
        assertEquals(200f, scoreToPosition(0, minScoreAt = 300f, maxScoreAt = 100f), delta)
    }

    @Test
    fun `positions outside the axis clamp to score bounds`() {
        assertEquals(SCORE_MIN, positionToScore(-500f, minScoreAt = 0f, maxScoreAt = 100f))
        assertEquals(SCORE_MAX, positionToScore(500f, minScoreAt = 0f, maxScoreAt = 100f))
        assertEquals(SCORE_MIN, positionToScore(900f, minScoreAt = 300f, maxScoreAt = 20f))
        assertEquals(SCORE_MAX, positionToScore(-900f, minScoreAt = 300f, maxScoreAt = 20f))
    }

    @Test
    fun `ties round towards positive infinity`() {
        assertEquals(-87, positionToScore(1f, minScoreAt = 0f, maxScoreAt = 16f))
    }

    @Test
    fun `zero height axis yields a neutral score`() {
        assertEquals(0, positionToScore(10f, minScoreAt = 10f, maxScoreAt = 10f))
        assertEquals(0, positionToScore(50f, minScoreAt = 10f, maxScoreAt = 10f))
    }

    @Test
    fun `zero height axis draws every score at the same position`() {
        assertEquals(10f, scoreToPosition(SCORE_MIN, minScoreAt = 10f, maxScoreAt = 10f), delta)
        assertEquals(10f, scoreToPosition(SCORE_MAX, minScoreAt = 10f, maxScoreAt = 10f), delta)
    }

    @Test
    fun `slot index picks the slot containing x`() {
        assertEquals(0, slotIndexAt(0f, width = 1100f, slotCount = 11))
        assertEquals(0, slotIndexAt(99.5f, width = 1100f, slotCount = 11))
        assertEquals(1, slotIndexAt(100f, width = 1100f, slotCount = 11))
        assertEquals(10, slotIndexAt(1099f, width = 1100f, slotCount = 11))
    }

    @Test
    fun `slot index clamps at both edges`() {
        assertEquals(0, slotIndexAt(-10f, width = 1100f, slotCount = 11))
        assertEquals(10, slotIndexAt(1100f, width = 1100f, slotCount = 11))
        assertEquals(10, slotIndexAt(5000f, width = 1100f, slotCount = 11))
    }

    @Test
    fun `slot index stays in range on a zero width canvas`() {
        assertEquals(0, slotIndexAt(0f, width = 0f, slotCount = 11))
        assertEquals(10, slotIndexAt(5f, width = 0f, slotCount = 11))
    }

    @Test
    fun `first tap is pending and not a double tap`() {
        val outcome = registerTap(null, index = 3, uptimeMs = 1000L)
        assertFalse(outcome.isDoubleTap)
        assertEquals(PendingTap(3, 1000L), outcome.pending)
    }

    @Test
    fun `second tap inside the window is a double tap and clears the pending tap`() {
        val outcome = registerTap(PendingTap(3, 1000L), index = 3, uptimeMs = 1299L)
        assertTrue(outcome.isDoubleTap)
        assertNull(outcome.pending)
    }

    @Test
    fun `second tap exactly at the window boundary is not a double tap`() {
        val outcome = registerTap(PendingTap(3, 1000L), index = 3, uptimeMs = 1300L)
        assertFalse(outcome.isDoubleTap)
        assertEquals(PendingTap(3, 1300L), outcome.pending)
    }

    @Test
    fun `second tap outside the window restarts the pending tap`() {
        val outcome = registerTap(PendingTap(3, 1000L), index = 3, uptimeMs = 5000L)
        assertFalse(outcome.isDoubleTap)
        assertEquals(PendingTap(3, 5000L), outcome.pending)
    }

    @Test
    fun `second tap on a different index is not a double tap`() {
        val outcome = registerTap(PendingTap(3, 1000L), index = 4, uptimeMs = 1100L)
        assertFalse(outcome.isDoubleTap)
        assertEquals(PendingTap(4, 1100L), outcome.pending)
    }

    @Test
    fun `third tap after a double tap starts over`() {
        val second = registerTap(PendingTap(3, 1000L), index = 3, uptimeMs = 1100L)
        val third = registerTap(second.pending, index = 3, uptimeMs = 1200L)
        assertFalse(third.isDoubleTap)
    }

    @Test
    fun `tap after a drag reset is not a double tap`() {
        val first = registerTap(null, index = 3, uptimeMs = 1000L)
        assertEquals(PendingTap(3, 1000L), first.pending)
        val afterDrag: PendingTap? = null
        val second = registerTap(afterDrag, index = 3, uptimeMs = 1100L)
        assertFalse(second.isDoubleTap)
    }
}
