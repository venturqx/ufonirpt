package org.schabi.newpipe.tournesol

import org.junit.Assert.assertEquals
import org.junit.Test

class TournesolDailyProgressTest {
    @Test
    fun `same day counts comparisons since the baseline and writes nothing`() {
        assertEquals(
            DailyProgress(3, null),
            dailyProgress(13, TODAY, TODAY, 10)
        )
    }

    @Test
    fun `new day resets the baseline to the current count`() {
        assertEquals(
            DailyProgress(0, DailyBaseline(TODAY, 13)),
            dailyProgress(13, TODAY, YESTERDAY, 10)
        )
    }

    @Test
    fun `no stored date resets the baseline even with a stored count`() {
        assertEquals(
            DailyProgress(0, DailyBaseline(TODAY, 13)),
            dailyProgress(13, TODAY, null, 10)
        )
    }

    @Test
    fun `nothing stored starts a baseline at the current count`() {
        assertEquals(
            DailyProgress(0, DailyBaseline(TODAY, 13)),
            dailyProgress(13, TODAY, null, null)
        )
    }

    @Test
    fun `a missing baseline count on the same day defaults to the current count`() {
        assertEquals(
            DailyProgress(0, null),
            dailyProgress(13, TODAY, TODAY, null)
        )
    }

    @Test
    fun `a count lower than the baseline gives zero and keeps the baseline`() {
        assertEquals(
            DailyProgress(0, null),
            dailyProgress(7, TODAY, TODAY, 10)
        )
    }

    @Test
    fun `an unknown count gives zero and writes nothing`() {
        assertEquals(
            DailyProgress(0, null),
            dailyProgress(null, TODAY, YESTERDAY, 10)
        )
        assertEquals(
            DailyProgress(0, null),
            dailyProgress(null, TODAY, TODAY, 10)
        )
    }

    private companion object {
        const val TODAY = "2026-10-31"
        const val YESTERDAY = "2026-10-30"
    }
}
