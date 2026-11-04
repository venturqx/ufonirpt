package org.schabi.newpipe.views

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val WIDTH = 100
private const val HEIGHT = 100
private const val TOP_PAD = 12f
private const val DELTA = 1e-3f

class DistributionCurveTest {
    private fun curveOf(bins: IntArray, counts: IntArray) = DistributionCurve.of(bins, counts, WIDTH, HEIGHT)!!

    private fun evenBins(count: Int) = IntArray(count + 1) { it * 10 }

    private fun hermite(y0: Float, y1: Float, m0: Float, m1: Float, dx: Float, t: Float): Float {
        val t2 = t * t
        val t3 = t2 * t
        return (2 * t3 - 3 * t2 + 1) * y0 +
            (t3 - 2 * t2 + t) * dx * m0 +
            (-2 * t3 + 3 * t2) * y1 +
            (t3 - t2) * dx * m1
    }

    private fun assertRejected(bins: IntArray, counts: IntArray) {
        assertFalse(DistributionCurve.isDrawable(bins, counts))
        assertNull(DistributionCurve.of(bins, counts, WIDTH, HEIGHT))
    }

    @Test
    fun `empty input is rejected`() {
        assertRejected(intArrayOf(), intArrayOf())
    }

    @Test
    fun `single bucket is rejected`() {
        assertRejected(intArrayOf(0, 10), intArrayOf(3))
    }

    @Test
    fun `too few bins are rejected`() {
        assertRejected(intArrayOf(0, 10), intArrayOf(1, 2))
    }

    @Test
    fun `too many bins are rejected`() {
        assertRejected(intArrayOf(0, 10, 20, 30), intArrayOf(1, 2))
    }

    @Test
    fun `zero bin range is rejected`() {
        assertRejected(intArrayOf(5, 5, 5), intArrayOf(1, 2))
    }

    @Test
    fun `all zero counts are rejected`() {
        assertRejected(evenBins(3), intArrayOf(0, 0, 0))
    }

    @Test
    fun `zero size is rejected even for drawable input`() {
        val bins = evenBins(2)
        val counts = intArrayOf(1, 2)
        assertTrue(DistributionCurve.isDrawable(bins, counts))
        assertNull(DistributionCurve.of(bins, counts, 0, HEIGHT))
        assertNull(DistributionCurve.of(bins, counts, WIDTH, 0))
    }

    @Test
    fun `two buckets form a straight line`() {
        val curve = curveOf(evenBins(2), intArrayOf(1, 2))
        val slope = (TOP_PAD - 56f) / 50f

        assertArrayEquals(floatArrayOf(25f, 75f), curve.xs, DELTA)
        assertArrayEquals(floatArrayOf(56f, TOP_PAD), curve.ys, DELTA)
        assertArrayEquals(floatArrayOf(slope, slope), curve.tangents, DELTA)
    }

    @Test
    fun `flat counts have zero tangents`() {
        val curve = curveOf(evenBins(3), intArrayOf(5, 5, 5))

        assertArrayEquals(floatArrayOf(TOP_PAD, TOP_PAD, TOP_PAD), curve.ys, DELTA)
        assertArrayEquals(floatArrayOf(0f, 0f, 0f), curve.tangents, DELTA)
    }

    @Test
    fun `local peak has a flat tangent`() {
        val curve = curveOf(evenBins(3), intArrayOf(0, 8, 0))

        assertEquals(0f, curve.tangents[1], DELTA)
    }

    @Test
    fun `steep monotonic counts do not overshoot`() {
        val curve = curveOf(evenBins(5), intArrayOf(0, 0, 1, 100, 100))

        for (i in 0 until curve.xs.lastIndex) {
            val y0 = curve.ys[i]
            val y1 = curve.ys[i + 1]
            val dx = curve.xs[i + 1] - curve.xs[i]
            for (step in 0..20) {
                val y = hermite(y0, y1, curve.tangents[i], curve.tangents[i + 1], dx, step / 20f)
                assertTrue("segment $i step $step: $y", y <= maxOf(y0, y1) + DELTA)
                assertTrue("segment $i step $step: $y", y >= minOf(y0, y1) - DELTA)
            }
        }
    }

    @Test
    fun `highest smoothed point reaches the top padding`() {
        val curve = curveOf(evenBins(5), intArrayOf(0, 8, 8, 8, 0))

        assertEquals(TOP_PAD, curve.ys[2], DELTA)
    }

    @Test
    fun `isolated peak is smoothed to half height`() {
        val curve = curveOf(evenBins(5), intArrayOf(0, 0, 8, 0, 0))

        assertEquals(TOP_PAD + 0.5f * (HEIGHT - TOP_PAD), curve.ys[2], DELTA)
    }

    @Test
    fun `endpoints are not smoothed`() {
        val curve = curveOf(evenBins(4), intArrayOf(8, 0, 0, 8))

        assertEquals(TOP_PAD, curve.ys[0], DELTA)
        assertEquals(TOP_PAD, curve.ys[3], DELTA)
        assertEquals(TOP_PAD + 0.75f * (HEIGHT - TOP_PAD), curve.ys[1], DELTA)
    }

    @Test
    fun `zero line is proportional to the bin range`() {
        val centered = curveOf(intArrayOf(-100, 0, 100), intArrayOf(1, 2))
        val shifted = curveOf(intArrayOf(-25, 25, 75), intArrayOf(1, 2))

        assertEquals(50f, centered.zeroX, DELTA)
        assertEquals(25f, shifted.zeroX, DELTA)
    }
}
