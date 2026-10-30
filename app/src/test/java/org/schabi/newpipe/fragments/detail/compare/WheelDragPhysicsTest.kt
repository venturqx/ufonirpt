package org.schabi.newpipe.fragments.detail.compare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WheelDragPhysicsTest {
    private val pageStep = 100f
    private val tolerance = 1e-4f

    private fun wheel(page: Int = 5, lastIndex: Int = 10) = WheelDragState.start(page, 0f, lastIndex)

    private fun WheelDragState.dragged(distance: Float) = copy(dragDistance = distance)

    @Test
    fun `start allows one page either side of the start page`() {
        val state = wheel(page = 5)

        assertEquals(4, state.minPage)
        assertEquals(6, state.maxPage)
        assertEquals(5, state.startPage)
        assertNull(state.lastTimestampMs)
    }

    @Test
    fun `start clamps limits at the first page`() {
        val state = wheel(page = 0)

        assertEquals(0, state.minPage)
        assertEquals(1, state.maxPage)
    }

    @Test
    fun `start clamps limits at the last page`() {
        val state = wheel(page = 10)

        assertEquals(9, state.minPage)
        assertEquals(10, state.maxPage)
    }

    @Test
    fun `start pins limits to page zero on a single entry list`() {
        val state = wheel(page = 0, lastIndex = 0)

        assertEquals(0, state.minPage)
        assertEquals(0, state.maxPage)
    }

    @Test
    fun `start clamps limits when the page is past the last index`() {
        val state = wheel(page = 12)

        assertEquals(10, state.minPage)
        assertEquals(10, state.maxPage)
    }

    @Test
    fun `start includes the pager offset fraction in the position`() {
        val state = WheelDragState.start(3, 0.25f, 10)

        assertEquals(3.25f, state.startPosition, tolerance)
        assertEquals(3.25f, state.position, tolerance)
    }

    @Test
    fun `drag cannot travel more than one page from the start`() {
        assertEquals(6f, wheel().onDrag(-10_000f, 1000L, pageStep).position, tolerance)
        assertEquals(4f, wheel().onDrag(10_000f, 1000L, pageStep).position, tolerance)
    }

    @Test
    fun `drag stops at the first page`() {
        val state = wheel(page = 0).onDrag(10_000f, 1000L, pageStep)

        assertEquals(WheelPageTarget(0, 0f), state.pageTarget)
    }

    @Test
    fun `drag stops at the last page`() {
        val state = wheel(page = 10).onDrag(-10_000f, 1000L, pageStep)

        assertEquals(WheelPageTarget(10, 0f), state.pageTarget)
    }

    @Test
    fun `single entry list stays on page zero`() {
        val state = wheel(page = 0, lastIndex = 0).onDrag(-10_000f, 1000L, pageStep)

        assertEquals(WheelPageTarget(0, 0f), state.pageTarget)
        assertEquals(0, state.cancelTarget())
        assertEquals(0, state.releaseTarget(-10_000f, pageStep))
    }

    @Test
    fun `zero drag keeps the position`() {
        val state = wheel().onDrag(0f, 1000L, pageStep)

        assertEquals(5f, state.position, tolerance)
        assertEquals(0f, state.dragDistance, tolerance)
        assertEquals(WheelPageTarget(5, 0f), state.pageTarget)
    }

    @Test
    fun `dead zone tracks the finger without damping`() {
        val state = wheel().onDrag(-1f, 1000L, pageStep)

        assertEquals(-0.771875f, state.dragDistance, tolerance)
        assertEquals(5.0077188f, state.position, tolerance)
    }

    @Test
    fun `fast drag past the dead zone uses the widest damping slope`() {
        assertEquals(5.72f, wheel().onDrag(-50f, 1000L, pageStep).position, tolerance)
        assertEquals(4.28f, wheel().onDrag(50f, 1000L, pageStep).position, tolerance)
    }

    @Test
    fun `slow drag past the dead zone is damped harder`() {
        val state = wheel()
            .onDrag(0f, 1000L, pageStep)
            .onDrag(-100f, 2000L, pageStep)

        assertEquals(5.600596f, state.position, tolerance)
    }

    @Test
    fun `first frame assumes a 16ms delta`() {
        val state = wheel().onDrag(-1f, 1000L, pageStep)

        assertEquals(62.5f, state.smoothedSpeedPxPerSec, tolerance)
        assertEquals(1000L, state.lastTimestampMs)
    }

    @Test
    fun `later frames blend speed with the measured delta`() {
        val state = wheel()
            .onDrag(-1f, 1000L, pageStep)
            .onDrag(-1f, 1010L, pageStep)

        assertEquals(71.875f, state.smoothedSpeedPxPerSec, tolerance)
    }

    @Test
    fun `identical timestamps floor the delta at 1ms`() {
        val state = wheel()
            .onDrag(-1f, 1000L, pageStep)
            .onDrag(-1f, 1000L, pageStep)

        assertEquals(296.875f, state.smoothedSpeedPxPerSec, tolerance)
    }

    @Test
    fun `zero speed reseeds the smoothing`() {
        val state = wheel()
            .onDrag(0f, 1000L, pageStep)
            .onDrag(-1f, 1010L, pageStep)

        assertEquals(100f, state.smoothedSpeedPxPerSec, tolerance)
    }

    @Test
    fun `drag distance accumulates across frames`() {
        val state = wheel()
            .onDrag(-1f, 1000L, pageStep)
            .onDrag(-1f, 1016L, pageStep)

        assertEquals(-1.54375f, state.dragDistance, tolerance)
    }

    @Test
    fun `page target keeps the offset up to half a page`() {
        assertEquals(WheelPageTarget(5, 0.25f), wheel().copy(position = 5.25f).pageTarget)
        assertEquals(WheelPageTarget(5, 0.5f), wheel().copy(position = 5.5f).pageTarget)
    }

    @Test
    fun `page target rolls over past half a page`() {
        assertEquals(WheelPageTarget(6, -0.25f), wheel().copy(position = 5.75f).pageTarget)
    }

    @Test
    fun `release past the swipe threshold moves one page in the drag direction`() {
        assertEquals(6, wheel().dragged(-50f).releaseTarget(0f, pageStep))
        assertEquals(4, wheel().dragged(50f).releaseTarget(0f, pageStep))
    }

    @Test
    fun `release at the swipe threshold stays on the start page`() {
        assertEquals(5, wheel().dragged(-28f).releaseTarget(0f, pageStep))
        assertEquals(5, wheel().dragged(28f).releaseTarget(0f, pageStep))
    }

    @Test
    fun `fling moves one page in the velocity direction`() {
        assertEquals(6, wheel().dragged(-10f).releaseTarget(-300f, pageStep))
        assertEquals(4, wheel().dragged(10f).releaseTarget(300f, pageStep))
    }

    @Test
    fun `fling direction follows velocity over drag direction`() {
        assertEquals(4, wheel().dragged(-10f).releaseTarget(300f, pageStep))
    }

    @Test
    fun `release below the fling velocity stays on the start page`() {
        assertEquals(5, wheel().dragged(-10f).releaseTarget(-250f, pageStep))
    }

    @Test
    fun `fling below the minimum offset stays on the start page`() {
        assertEquals(5, wheel().dragged(-5f).releaseTarget(-10_000f, pageStep))
    }

    @Test
    fun `release is clamped at the list edges`() {
        assertEquals(0, wheel(page = 0).dragged(50f).releaseTarget(0f, pageStep))
        assertEquals(0, wheel(page = 0).dragged(10f).releaseTarget(300f, pageStep))
        assertEquals(10, wheel(page = 10).dragged(-50f).releaseTarget(0f, pageStep))
        assertEquals(10, wheel(page = 10).dragged(-10f).releaseTarget(-300f, pageStep))
    }

    @Test
    fun `cancel settles on the nearest page`() {
        assertEquals(6, wheel().copy(position = 5.6f).cancelTarget())
        assertEquals(5, wheel().copy(position = 5.4f).cancelTarget())
    }

    @Test
    fun `cancel without a drag stays within the limits`() {
        assertEquals(0, WheelDragState.start(0, -0.4f, 10).cancelTarget())
        assertEquals(10, WheelDragState.start(10, 0.4f, 10).cancelTarget())
    }
}
