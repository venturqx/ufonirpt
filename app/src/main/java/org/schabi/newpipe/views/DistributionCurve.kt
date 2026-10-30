package org.schabi.newpipe.views

import kotlin.math.sqrt

private const val MIN_BUCKETS = 2
private const val TOP_PADDING_RATIO = 0.12f
private const val NEIGHBOUR_WEIGHT = 0.25f
private const val CENTER_WEIGHT = 0.5f
private const val MAX_TANGENT_RATIO = 3f

internal class DistributionCurve private constructor(
    val xs: FloatArray,
    val ys: FloatArray,
    val tangents: FloatArray,
    val zeroX: Float
) {
    companion object {
        @JvmStatic
        fun isDrawable(bins: IntArray, counts: IntArray): Boolean {
            if (counts.size < MIN_BUCKETS || bins.size != counts.size + 1) {
                return false
            }
            return xRange(bins) != 0f && counts.max() > 0
        }

        @JvmStatic
        fun of(bins: IntArray, counts: IntArray, width: Int, height: Int): DistributionCurve? {
            if (width <= 0 || height <= 0 || !isDrawable(bins, counts)) {
                return null
            }
            val xMin = bins.first().toFloat()
            val xRange = xRange(bins)
            val xs = FloatArray(counts.size) { i ->
                val binCenter = (bins[i] + bins[i + 1]) / 2f
                (binCenter - xMin) / xRange * width
            }
            val topPad = height * TOP_PADDING_RATIO
            val maxCount = counts.max()
            val smoothed = smooth(counts)
            val ys = FloatArray(counts.size) { i ->
                topPad + (1f - smoothed[i] / maxCount) * (height - topPad)
            }
            return DistributionCurve(
                xs = xs,
                ys = ys,
                tangents = monotoneTangents(slopes(xs, ys)),
                zeroX = (0f - xMin) / xRange * width
            )
        }

        private fun xRange(bins: IntArray): Float = bins.last() - bins.first().toFloat()

        private fun smooth(counts: IntArray): FloatArray = FloatArray(counts.size) { i ->
            if (i == 0 || i == counts.lastIndex) {
                counts[i].toFloat()
            } else {
                counts[i - 1] * NEIGHBOUR_WEIGHT +
                    counts[i] * CENTER_WEIGHT +
                    counts[i + 1] * NEIGHBOUR_WEIGHT
            }
        }

        private fun slopes(xs: FloatArray, ys: FloatArray): FloatArray = FloatArray(xs.size - 1) { i ->
            val dx = xs[i + 1] - xs[i]
            if (dx == 0f) 0f else (ys[i + 1] - ys[i]) / dx
        }

        private fun monotoneTangents(slopes: FloatArray): FloatArray {
            val tangents = FloatArray(slopes.size + 1)
            tangents[0] = slopes.first()
            tangents[slopes.size] = slopes.last()
            for (i in 1 until slopes.size) {
                val isExtremum = slopes[i - 1] * slopes[i] <= 0f
                tangents[i] = if (isExtremum) 0f else (slopes[i - 1] + slopes[i]) / 2f
            }

            for (i in slopes.indices) {
                val slope = slopes[i]
                if (slope == 0f) {
                    tangents[i] = 0f
                    tangents[i + 1] = 0f
                } else {
                    val alpha = tangents[i] / slope
                    val beta = tangents[i + 1] / slope
                    val magnitude = alpha * alpha + beta * beta
                    if (magnitude > MAX_TANGENT_RATIO * MAX_TANGENT_RATIO) {
                        val scale = MAX_TANGENT_RATIO / sqrt(magnitude)
                        tangents[i] = scale * alpha * slope
                        tangents[i + 1] = scale * beta * slope
                    }
                }
            }
            return tangents
        }
    }
}
