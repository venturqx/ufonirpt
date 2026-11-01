package org.schabi.newpipe.tournesol

import org.junit.Assert.assertEquals
import org.junit.Test

class CompareSignTest {
    @Test
    fun positiveScoreFavoursEntityB_whichIsTheRightVideo() {
        val selection = ComparePairSelection(
            leftServiceId = 0,
            leftUrl = "yt:left",
            rightServiceId = 0,
            rightUrl = "yt:right"
        )

        val key = compareKeyFor(selection) { url, _ -> url }

        assertEquals("yt:left", key?.entityA)
        assertEquals("yt:right", key?.entityB)
    }
}
