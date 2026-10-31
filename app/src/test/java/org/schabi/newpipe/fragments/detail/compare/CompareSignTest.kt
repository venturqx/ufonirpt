package org.schabi.newpipe.fragments.detail.compare

import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.fragments.detail.ComparePairSelection
import org.schabi.newpipe.fragments.detail.compareKeyFor

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
