package org.schabi.newpipe.views

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val DENSITY = 2f
private const val ICON_SIZE = 36
private const val DELTA = 1e-3f

class LollipopChartGeometryTest {
    private fun metricsOf(density: Float = DENSITY, iconSize: Int = ICON_SIZE) = LollipopChartMetrics.of(iconSize) { (it * density).toInt() }

    private fun desiredHeight(vararg scores: Double) = LollipopChartGeometry.desiredHeight(scores, metricsOf())

    private fun geometryOf(scores: DoubleArray, width: Int, height: Int) = LollipopChartGeometry.of(scores, metricsOf(), width, height)!!

    private val mixed = geometryOf(doubleArrayOf(30.0, -10.0), 300, 332)

    @Test
    fun `dp values are truncated`() {
        val metrics = metricsOf(density = 1.5f, iconSize = 27)

        assertEquals(21, metrics.circleRadius)
        assertEquals(5, metrics.circleStrokeWidth)
        assertEquals(21, metrics.scoreTextHeight)
        assertEquals(4, metrics.textGap)
        assertEquals(3, metrics.scoreUnit)
        assertEquals(390, metrics.maxHeight)
        assertEquals(12, metrics.touchMargin)
        assertEquals(9, metrics.labelGapAbove)
        assertEquals(1, metrics.labelGapBelow)
        assertEquals(27, metrics.iconSize)
        assertEquals(1, metrics.iconNudge)
    }

    @Test
    fun `desired height at a fractional density`() {
        assertEquals(238, LollipopChartGeometry.desiredHeight(doubleArrayOf(50.0), metricsOf(1.5f)))
    }

    @Test
    fun `all positive desired height reserves a label band on top only`() {
        assertEquals(318, desiredHeight(10.0, 50.0))
    }

    @Test
    fun `all negative desired height reserves a label band below only`() {
        assertEquals(290, desiredHeight(-20.0, -40.0))
    }

    @Test
    fun `mixed desired height reserves both label bands`() {
        assertEquals(332, desiredHeight(30.0, -10.0))
    }

    @Test
    fun `all zero desired height has no label band and flat margins`() {
        assertEquals(72, desiredHeight(0.0, 0.0))
    }

    @Test
    fun `no entries measure like all zero`() {
        assertEquals(72, desiredHeight())
    }

    @Test
    fun `small ranges use the minimum margins`() {
        assertEquals(114, desiredHeight(1.0))
        assertEquals(122, desiredHeight(-1.0))
    }

    @Test
    fun `desired height is truncated`() {
        assertEquals(244, desiredHeight(33.3))
    }

    @Test
    fun `desired height is clamped`() {
        assertEquals(520, desiredHeight(-100.0, 100.0))
    }

    @Test
    fun `height just under the clamp is not clamped`() {
        assertEquals(516, desiredHeight(95.0))
    }

    @Test
    fun `all positive layout`() {
        val geometry = geometryOf(doubleArrayOf(10.0, 50.0), 400, 318)

        assertArrayEquals(floatArrayOf(100f, 300f), geometry.centerXs, DELTA)
        assertArrayEquals(floatArrayOf(242f, 82f), geometry.barEndYs, DELTA)
        assertArrayEquals(floatArrayOf(202f, 42f), geometry.labelYs, DELTA)
        assertEquals(282f, geometry.zeroY, DELTA)
        assertEquals(60f, geometry.zeroLineStartX, DELTA)
        assertEquals(340f, geometry.zeroLineEndX, DELTA)
    }

    @Test
    fun `all negative layout`() {
        val geometry = geometryOf(doubleArrayOf(-20.0, -40.0), 400, 290)

        assertArrayEquals(floatArrayOf(116f, 196f), geometry.barEndYs, DELTA)
        assertArrayEquals(floatArrayOf(174f, 254f), geometry.labelYs, DELTA)
        assertEquals(36f, geometry.zeroY, DELTA)
    }

    @Test
    fun `mixed layout`() {
        assertArrayEquals(floatArrayOf(75f, 225f), mixed.centerXs, DELTA)
        assertArrayEquals(floatArrayOf(78f, 238f), mixed.barEndYs, DELTA)
        assertArrayEquals(floatArrayOf(38f, 296f), mixed.labelYs, DELTA)
        assertEquals(198f, mixed.zeroY, DELTA)
        assertEquals(45f, mixed.zeroLineStartX, DELTA)
        assertEquals(255f, mixed.zeroLineEndX, DELTA)
    }

    @Test
    fun `all zero layout sits on the zero line with labels above`() {
        val geometry = geometryOf(doubleArrayOf(0.0, 0.0), 400, 72)

        assertEquals(36f, geometry.zeroY, DELTA)
        assertArrayEquals(floatArrayOf(36f, 36f), geometry.barEndYs, DELTA)
        assertArrayEquals(floatArrayOf(-4f, -4f), geometry.labelYs, DELTA)
    }

    @Test
    fun `single entry fills its slot`() {
        val geometry = geometryOf(doubleArrayOf(50.0), 400, 318)

        assertArrayEquals(floatArrayOf(200f), geometry.centerXs, DELTA)
        assertArrayEquals(floatArrayOf(82f), geometry.barEndYs, DELTA)
        assertEquals(282f, geometry.zeroY, DELTA)
        assertEquals(120f, geometry.zeroLineStartX, DELTA)
        assertEquals(280f, geometry.zeroLineEndX, DELTA)
    }

    @Test
    fun `clamped height compresses the bars`() {
        val geometry = geometryOf(doubleArrayOf(100.0, -100.0), 400, 520)

        assertArrayEquals(floatArrayOf(92.4615f, 397.0769f), geometry.barEndYs, DELTA)
        assertEquals(244.7692f, geometry.zeroY, DELTA)
    }

    @Test
    fun `view padding only stretches the layout and offsets nothing`() {
        val geometry = geometryOf(doubleArrayOf(10.0, 50.0), 432, 350)

        assertArrayEquals(floatArrayOf(108f, 324f), geometry.centerXs, DELTA)
        assertArrayEquals(floatArrayOf(267.2632f, 84.8070f), geometry.barEndYs, DELTA)
        assertEquals(312.8772f, geometry.zeroY, DELTA)
    }

    @Test
    fun `layout is rejected when there is nothing to draw`() {
        val metrics = metricsOf()

        assertNull(LollipopChartGeometry.of(doubleArrayOf(), metrics, 400, 300))
        assertNull(LollipopChartGeometry.of(doubleArrayOf(1.0), metrics, 0, 300))
        assertNull(LollipopChartGeometry.of(doubleArrayOf(1.0), metrics, 400, 0))
    }

    @Test
    fun `touch on a circle centre hits that entry`() {
        assertEquals(0, mixed.hitTest(75f, 78f))
        assertEquals(1, mixed.hitTest(225f, 238f))
    }

    @Test
    fun `touch in the margin around a circle hits`() {
        assertEquals(1, mixed.hitTest(225f, 281f))
    }

    @Test
    fun `touch exactly on the radius hits`() {
        assertEquals(0, mixed.hitTest(119f, 78f))
    }

    @Test
    fun `touch just outside the radius misses`() {
        assertNull(mixed.hitTest(119.5f, 78f))
    }

    @Test
    fun `touch area is a circle and not a box`() {
        assertEquals(0, mixed.hitTest(106f, 109f))
        assertNull(mixed.hitTest(107f, 110f))
    }

    @Test
    fun `touch on no entry misses`() {
        assertNull(mixed.hitTest(150f, 150f))
    }

    @Test
    fun `lowest index wins where touch areas overlap`() {
        val geometry = geometryOf(doubleArrayOf(10.0, 10.0), 80, 200)

        assertEquals(0, geometry.hitTest(50f, geometry.barEndYs[0]))
    }

    @Test
    fun `standard icon is centred on the circle and inset by half the stroke`() {
        assertEquals(LollipopIconBounds(60, 63, 90, 93), mixed.iconBounds(0, LollipopIconFit.STANDARD))
    }

    @Test
    fun `shrunk icon truncates its half size`() {
        assertEquals(LollipopIconBounds(62, 65, 88, 91), mixed.iconBounds(0, LollipopIconFit.SHRUNK))
    }

    @Test
    fun `nudged icon moves down without resizing`() {
        assertEquals(LollipopIconBounds(60, 65, 90, 95), mixed.iconBounds(0, LollipopIconFit.NUDGED_DOWN))
    }

    @Test
    fun `icon half size truncates both integer divisions`() {
        val metrics = metricsOf(density = 1.5f, iconSize = 27)
        val geometry = LollipopChartGeometry.of(doubleArrayOf(0.0), metrics, 100, 100)!!

        assertEquals(LollipopIconBounds(39, 39, 61, 61), geometry.iconBounds(0, LollipopIconFit.STANDARD))
    }

    @Test
    fun `icon bounds truncate toward zero`() {
        val metrics = metricsOf(iconSize = 200)
        val geometry = LollipopChartGeometry.of(doubleArrayOf(100.0, -100.0), metrics, 400, 520)!!

        assertEquals(LollipopIconBounds(3, -4, 197, 189), geometry.iconBounds(0, LollipopIconFit.STANDARD))
    }

    @Test
    fun `icon fit comes from the criterion id`() {
        assertEquals(LollipopIconFit.SHRUNK, LollipopIconFit.of("layman_friendly"))
        assertEquals(LollipopIconFit.NUDGED_DOWN, LollipopIconFit.of("backfire_risk"))
        assertEquals(LollipopIconFit.STANDARD, LollipopIconFit.of("reliability"))
    }
}
