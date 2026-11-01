package org.schabi.newpipe.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import androidx.core.content.ContextCompat
import org.schabi.newpipe.R

private const val FILL_ALPHA = 55
private const val SEPARATOR_ALPHA = 140
private const val SEPARATOR_STROKE_RATIO = 0.6f
private const val CONTROL_POINT_DIVISOR = 3f

class DistributionCurveView(
    context: Context,
    private val bins: IntArray,
    private val distribution: IntArray,
    strokePx: Float
) : View(context) {
    private val curvePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.tournesol_score)
        style = Paint.Style.STROKE
        strokeWidth = strokePx
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.tournesol_score)
        alpha = FILL_ALPHA
        style = Paint.Style.FILL
    }
    private val separatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.tournesol_content)
        alpha = SEPARATOR_ALPHA
        style = Paint.Style.STROKE
        strokeWidth = strokePx * SEPARATOR_STROKE_RATIO
    }
    private val curvePath = Path()
    private val fillPath = Path()
    private var curve: DistributionCurve? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        curve = DistributionCurve.of(bins, distribution, w, h)?.also { rebuildPaths(it, h) }
    }

    private fun rebuildPaths(curve: DistributionCurve, height: Int) {
        val xs = curve.xs
        val ys = curve.ys
        val m = curve.tangents
        val last = xs.lastIndex

        curvePath.reset()
        curvePath.moveTo(xs[0], ys[0])
        for (i in 0 until last) {
            val seg = (xs[i + 1] - xs[i]) / CONTROL_POINT_DIVISOR
            curvePath.cubicTo(
                xs[i] + seg,
                ys[i] + m[i] * seg,
                xs[i + 1] - seg,
                ys[i + 1] - m[i + 1] * seg,
                xs[i + 1],
                ys[i + 1]
            )
        }

        fillPath.set(curvePath)
        fillPath.lineTo(xs[last], height.toFloat())
        fillPath.lineTo(xs[0], height.toFloat())
        fillPath.close()
    }

    override fun onDraw(canvas: Canvas) {
        val zeroX = curve?.zeroX ?: return
        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(curvePath, curvePaint)
        canvas.drawLine(zeroX, 0f, zeroX, height.toFloat(), separatorPaint)
    }
}
