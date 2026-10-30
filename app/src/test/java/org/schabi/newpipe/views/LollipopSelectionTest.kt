package org.schabi.newpipe.views

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LollipopSelectionTest {
    @Test
    fun `tap selects the hit when nothing is selected`() {
        assertEquals(2, selectionAfterTap(null, 2))
    }

    @Test
    fun `tap on the selected item deselects it`() {
        assertNull(selectionAfterTap(2, 2))
    }

    @Test
    fun `tap on another item switches the selection`() {
        assertEquals(3, selectionAfterTap(2, 3))
    }

    @Test
    fun `miss keeps the selection`() {
        assertEquals(2, selectionAfterTap(2, null))
    }

    @Test
    fun `miss with nothing selected stays unselected`() {
        assertNull(selectionAfterTap(null, null))
    }

    @Test
    fun `first item can be selected and deselected`() {
        assertEquals(0, selectionAfterTap(null, 0))
        assertNull(selectionAfterTap(0, 0))
    }
}
