package org.schabi.newpipe.views

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.fragments.detail.EXTRA_CRITERIA

private const val CRITERIA_COUNT = 9
private const val ALPHA_SHIFT = 24
private const val OPAQUE_ALPHA = 0xFF

class TournesolCriterionStyleTest {
    private val catalogIds = TournesolCriterionStyle.ALL.map { it.id }

    @Test
    fun `table has nine unique ids`() {
        assertEquals(CRITERIA_COUNT, catalogIds.size)
        assertEquals(CRITERIA_COUNT, catalogIds.toSet().size)
    }

    @Test
    fun `ids match compare criteria in the same order`() {
        assertEquals(EXTRA_CRITERIA.map { it.id }, catalogIds)
    }

    @Test
    fun `every colour keeps full alpha`() {
        TournesolCriterionStyle.ALL.forEach { style ->
            assertEquals(style.id, OPAQUE_ALPHA, style.color ushr ALPHA_SHIFT)
        }
    }

    @Test
    fun `find returns the style of every known id`() {
        TournesolCriterionStyle.ALL.forEach { style ->
            assertSame(style, TournesolCriterionStyle.find(style.id))
        }
    }

    @Test
    fun `find returns null for an unknown id`() {
        assertNull(TournesolCriterionStyle.find("unknown_criterion"))
    }

    @Test
    fun `sort restores catalog order`() {
        val sorted = TournesolCriterionStyle.sortedByCatalogOrder(catalogIds.reversed()) { it }

        assertEquals(catalogIds, sorted)
    }

    @Test
    fun `sort puts unknown ids last and keeps equal ids stable`() {
        val items = listOf(
            "zeta" to 1,
            "pedagogy" to 2,
            "alpha" to 3,
            "reliability" to 4,
            "pedagogy" to 5
        )

        val sorted = TournesolCriterionStyle.sortedByCatalogOrder(items) { it.first }

        assertEquals(
            listOf(
                "reliability" to 4,
                "pedagogy" to 2,
                "pedagogy" to 5,
                "zeta" to 1,
                "alpha" to 3
            ),
            sorted
        )
    }

    @Test
    fun `sort of an empty list is empty`() {
        val sorted = TournesolCriterionStyle.sortedByCatalogOrder(emptyList<String>()) { it }

        assertTrue(sorted.isEmpty())
    }
}
