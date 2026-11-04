package org.schabi.newpipe.fragments.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.schabi.newpipe.fragments.detail.AppBarChildLimit.Collapsible
import org.schabi.newpipe.fragments.detail.AppBarChildLimit.Pinned
import org.schabi.newpipe.fragments.detail.AppBarChildLimit.ScrollsAway

class CompareAppBarLimitsTest {

    private val heights = listOf(300, 200)

    @Test
    fun `no overflow pins every child`() {
        assertEquals(listOf(Pinned, Pinned), compareAppBarLimits(100, 500, heights))
        assertEquals(listOf(Pinned, Pinned), compareAppBarLimits(500, 500, heights))
    }

    @Test
    fun `overflow less than first child collapses only the first child`() {
        assertEquals(listOf(Collapsible(200), Pinned), compareAppBarLimits(600, 500, heights))
    }

    @Test
    fun `overflow equal to first child collapses it fully and pins the next`() {
        assertEquals(listOf(Collapsible(0), Pinned), compareAppBarLimits(800, 500, heights))
    }

    @Test
    fun `overflow spanning children ends the collapse in the child where it runs out`() {
        assertEquals(listOf(ScrollsAway, Collapsible(100)), compareAppBarLimits(900, 500, heights))
    }

    @Test
    fun `overflow greater than all children scrolls every child away`() {
        assertEquals(listOf(ScrollsAway, ScrollsAway), compareAppBarLimits(2000, 500, heights))
    }

    @Test
    fun `zero-height child is pinned and spends no overflow`() {
        assertEquals(listOf(Pinned, Collapsible(200)), compareAppBarLimits(600, 500, listOf(0, 300)))
        assertEquals(listOf(Collapsible(200), Pinned), compareAppBarLimits(600, 500, listOf(300, 0)))
    }

    @Test
    fun `non-positive visible height gives no plan`() {
        assertNull(compareAppBarLimits(1000, 0, heights))
        assertNull(compareAppBarLimits(1000, -1, heights))
    }

    @Test
    fun `no children gives an empty plan`() {
        assertEquals(emptyList<AppBarChildLimit>(), compareAppBarLimits(1000, 500, emptyList()))
    }

    @Test
    fun `plan gives AppBarLayout a scroll range equal to the overflow`() {
        listOf(0, 100, 300, 400, 500, 900).forEach { overflow ->
            val limits = compareAppBarLimits(500 + overflow, 500, heights)!!

            assertEquals(minOf(overflow, heights.sum()), scrollRange(limits, heights))
        }
    }

    @Test
    fun `feeding the laid out result back yields the same plan`() {
        listOf(400, 600, 800, 900, 1100, 2000).forEach { content ->
            val first = compareAppBarLimits(content, COORDINATOR - heights.sum(), heights)!!
            val laidOut = laidOutHeights(first, heights)

            assertEquals(heights, laidOut)
            assertEquals(first, compareAppBarLimits(content, COORDINATOR - laidOut.sum(), laidOut))
        }
    }

    @Test
    fun `height held up by a stale minimum settles at its natural size`() {
        val natural = listOf(100, 200)
        var current = listOf(350, 200)

        repeat(10) {
            val limits = compareAppBarLimits(580, COORDINATOR - current.sum(), current)!!
            current = laidOutHeights(limits, natural)
        }

        assertEquals(natural, current)
    }

    private fun laidOutHeights(limits: List<AppBarChildLimit>, natural: List<Int>): List<Int> = natural.zip(limits) { height, limit ->
        maxOf(height, (limit as? Collapsible)?.minimumHeight ?: 0)
    }

    private fun scrollRange(limits: List<AppBarChildLimit>, heights: List<Int>): Int {
        var range = 0
        for ((limit, height) in limits.zip(heights)) {
            when (limit) {
                Pinned -> return range
                ScrollsAway -> range += height
                is Collapsible -> return range + height - limit.minimumHeight
            }
        }
        return range
    }

    private companion object {
        const val COORDINATOR = 1000
    }
}
