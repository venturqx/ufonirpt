package org.schabi.newpipe.fragments.detail.compare

import org.junit.Assert.assertEquals
import org.junit.Test

class CompareSignTest {
    @Test
    fun positiveScoreFavoursEntityB_whichIsTheRightVideo() {
        val entities = entitiesForLeftRight(leftUid = "yt:left", rightUid = "yt:right")
        assertEquals("yt:left", entities.entityA)
        assertEquals("yt:right", entities.entityB)
    }
}
