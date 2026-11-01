package org.schabi.newpipe.tournesol.ui

private const val CIRCLE_RADIUS_DP = 14f
private const val CIRCLE_STROKE_WIDTH_DP = 3.5f
private const val SCORE_TEXT_HEIGHT_DP = 14f
private const val TEXT_GAP_DP = 3f
private const val SCORE_UNIT_DP = 2f
private const val MAX_HEIGHT_DP = 260f
private const val TOUCH_MARGIN_DP = 8f
private const val LABEL_GAP_ABOVE_DP = 6f
private const val LABEL_GAP_BELOW_DP = 1f
private const val ICON_NUDGE_DP = 1f

private const val MIN_TOP_MARGIN = 3.0
private const val TOP_MARGIN_RATIO = 0.1
private const val MIN_BOTTOM_MARGIN = 5.0
private const val BOTTOM_MARGIN_RATIO = 0.2
private const val FLAT_MARGIN = 2.0

private const val SLOT_CENTER = 0.5f
private const val ZERO_LINE_INSET_RATIO = 0.3f

internal class LollipopChartMetrics private constructor(
    val circleRadius: Int,
    val circleStrokeWidth: Int,
    val scoreTextHeight: Int,
    val textGap: Int,
    val scoreUnit: Int,
    val maxHeight: Int,
    val touchMargin: Int,
    val labelGapAbove: Int,
    val labelGapBelow: Int,
    val iconSize: Int,
    val iconNudge: Int
) {
    companion object {
        @JvmStatic
        fun of(iconSize: Int, dp: (Float) -> Int): LollipopChartMetrics = LollipopChartMetrics(
            circleRadius = dp(CIRCLE_RADIUS_DP),
            circleStrokeWidth = dp(CIRCLE_STROKE_WIDTH_DP),
            scoreTextHeight = dp(SCORE_TEXT_HEIGHT_DP),
            textGap = dp(TEXT_GAP_DP),
            scoreUnit = dp(SCORE_UNIT_DP),
            maxHeight = dp(MAX_HEIGHT_DP),
            touchMargin = dp(TOUCH_MARGIN_DP),
            labelGapAbove = dp(LABEL_GAP_ABOVE_DP),
            labelGapBelow = dp(LABEL_GAP_BELOW_DP),
            iconSize = iconSize,
            iconNudge = dp(ICON_NUDGE_DP)
        )
    }
}

internal enum class LollipopIconFit(val scale: Float, val nudgedDown: Boolean) {
    STANDARD(1f, false),
    SHRUNK(0.88f, false),
    NUDGED_DOWN(1f, true);

    companion object {
        @JvmStatic
        fun of(criterionId: String): LollipopIconFit = when (criterionId) {
            "layman_friendly" -> SHRUNK
            "backfire_risk" -> NUDGED_DOWN
            else -> STANDARD
        }
    }
}

internal data class LollipopIconBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

private class ScoreScale(min: Double, max: Double, metrics: LollipopChartMetrics) {
    private val range = max - min
    private val topMargin = if (max > 0.0) maxOf(MIN_TOP_MARGIN, range * TOP_MARGIN_RATIO) else FLAT_MARGIN
    private val bottomMargin = if (min < 0.0) maxOf(MIN_BOTTOM_MARGIN, range * BOTTOM_MARGIN_RATIO) else FLAT_MARGIN
    private val paddedMax = max + topMargin
    private val paddedMin = min - bottomMargin
    private val labelBand = metrics.scoreTextHeight + metrics.textGap

    val topOverhead = metrics.circleRadius + if (max > 0.0) labelBand else 0
    val bottomOverhead = metrics.circleRadius + if (min < 0.0) labelBand else 0
    val desiredBarArea = ((range + topMargin + bottomMargin) * metrics.scoreUnit).toFloat()

    fun yOf(score: Double, height: Int): Float {
        val span = height - bottomOverhead - topOverhead
        return (topOverhead + (paddedMax - score) / (paddedMax - paddedMin) * span).toFloat()
    }

    companion object {
        fun of(scores: DoubleArray, metrics: LollipopChartMetrics): ScoreScale {
            var min = 0.0
            var max = 0.0
            for (score in scores) {
                if (score < min) min = score
                if (score > max) max = score
            }
            return ScoreScale(min, max, metrics)
        }
    }
}

internal class LollipopChartGeometry private constructor(
    private val metrics: LollipopChartMetrics,
    val centerXs: FloatArray,
    val barEndYs: FloatArray,
    val labelYs: FloatArray,
    val zeroY: Float,
    val zeroLineStartX: Float,
    val zeroLineEndX: Float
) {
    private val touchRadius = (metrics.circleRadius + metrics.touchMargin).toFloat()

    fun hitTest(x: Float, y: Float): Int? {
        for (i in centerXs.indices) {
            val dx = x - centerXs[i]
            val dy = y - barEndYs[i]
            if (dx * dx + dy * dy <= touchRadius * touchRadius) {
                return i
            }
        }
        return null
    }

    fun iconBounds(index: Int, fit: LollipopIconFit): LollipopIconBounds {
        val halfSize = ((metrics.iconSize / 2 - metrics.circleStrokeWidth / 2) * fit.scale).toInt()
        val cx = centerXs[index]
        val cy = barEndYs[index] + if (fit.nudgedDown) metrics.iconNudge else 0
        return LollipopIconBounds(
            left = (cx - halfSize).toInt(),
            top = (cy - halfSize).toInt(),
            right = (cx + halfSize).toInt(),
            bottom = (cy + halfSize).toInt()
        )
    }

    companion object {
        @JvmStatic
        fun desiredHeight(scores: DoubleArray, metrics: LollipopChartMetrics): Int {
            val scale = ScoreScale.of(scores, metrics)
            val overhead = scale.topOverhead + scale.bottomOverhead
            return minOf((overhead + scale.desiredBarArea).toInt(), metrics.maxHeight)
        }

        @JvmStatic
        fun of(scores: DoubleArray, metrics: LollipopChartMetrics, width: Int, height: Int): LollipopChartGeometry? {
            if (scores.isEmpty() || width <= 0 || height <= 0) {
                return null
            }
            val scale = ScoreScale.of(scores, metrics)
            val slotWidth = width.toFloat() / scores.size
            val zeroLineInset = slotWidth * ZERO_LINE_INSET_RATIO
            val barEndYs = FloatArray(scores.size) { i -> scale.yOf(scores[i], height) }
            return LollipopChartGeometry(
                metrics = metrics,
                centerXs = FloatArray(scores.size) { i -> slotWidth * (i + SLOT_CENTER) },
                barEndYs = barEndYs,
                labelYs = FloatArray(scores.size) { i -> labelY(scores[i], barEndYs[i], metrics) },
                zeroY = scale.yOf(0.0, height),
                zeroLineStartX = zeroLineInset,
                zeroLineEndX = width - zeroLineInset
            )
        }

        private fun labelY(score: Double, barEndY: Float, metrics: LollipopChartMetrics): Float {
            return if (score >= 0.0) {
                barEndY - metrics.circleRadius - metrics.labelGapAbove
            } else {
                barEndY + metrics.circleRadius + metrics.scoreTextHeight + metrics.labelGapBelow
            }
        }
    }
}
