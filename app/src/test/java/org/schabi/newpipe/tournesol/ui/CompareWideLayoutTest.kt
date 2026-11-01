package org.schabi.newpipe.tournesol.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareWideLayoutTest {
    @Test
    fun `landscape phone and tablet are wide`() {
        assertTrue(isCompareWideLayout(maxWidthDp = 914f, windowHeightDp = 411f))
        assertTrue(isCompareWideLayout(maxWidthDp = 1280f, windowHeightDp = 800f))
    }

    @Test
    fun `portrait phone and tablet stay stacked`() {
        assertFalse(isCompareWideLayout(maxWidthDp = 411f, windowHeightDp = 914f))
        assertFalse(isCompareWideLayout(maxWidthDp = 800f, windowHeightDp = 1280f))
    }

    @Test
    fun `a narrow pane stays stacked in a landscape window`() {
        assertFalse(isCompareWideLayout(maxWidthDp = 590f, windowHeightDp = 400f))
    }
}
