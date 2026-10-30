package org.schabi.newpipe.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.View.MeasureSpec
import java.util.Locale
import org.schabi.newpipe.util.CriterionScore

private const val MAX_WIDTH_DP = 400f
private const val BAR_WIDTH_DP = 12f
private const val SCORE_TEXT_SIZE_DP = 10f
private const val ZERO_LINE_WIDTH_DP = 1f
private const val SELECTED_GLOW_GAP_DP = 3f
private const val SELECTED_GLOW_WIDTH_DP = 4f
private const val SELECTED_GLOW_ALPHA = 160

class LollipopChartView(
    context: Context,
    private val criteria: List<CriterionScore>,
    private val icons: List<Drawable?>,
    private val colors: IntArray,
    iconSizePx: Int
) : View(context) {
    fun interface OnSelectionChangedListener {
        fun onSelectionChanged(index: Int?)
    }

    var onSelectionChangedListener: OnSelectionChangedListener? = null

    private val scores = DoubleArray(criteria.size) { criteria[it].score }
    private val scoreLabels = Array(criteria.size) { "%.0f".format(Locale.US, scores[it]) }
    private val metrics = LollipopChartMetrics.of(iconSizePx, ::dp)
    private val selectedGlowRadius = (metrics.circleRadius + dp(SELECTED_GLOW_GAP_DP)).toFloat()

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        strokeCap = Paint.Cap.BUTT
        strokeWidth = dp(BAR_WIDTH_DP).toFloat()
    }
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#0F0F0F")
    }
    private val circleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = metrics.circleStrokeWidth.toFloat()
    }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(SCORE_TEXT_SIZE_DP).toFloat()
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
    }
    private val zeroLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#40FFFFFF")
        strokeWidth = dp(ZERO_LINE_WIDTH_DP).toFloat()
    }
    private val selectedGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(SELECTED_GLOW_WIDTH_DP).toFloat()
    }

    private var geometry: LollipopChartGeometry? = null
    private var selectedIndex: Int? = null

    init {
        isClickable = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxWidth = dp(MAX_WIDTH_DP) + paddingLeft + paddingRight
        val clampedWidthSpec = if (MeasureSpec.getSize(widthMeasureSpec) > maxWidth) {
            MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.getMode(widthMeasureSpec))
        } else {
            widthMeasureSpec
        }
        super.onMeasure(clampedWidthSpec, heightMeasureSpec)

        val desiredHeight = LollipopChartGeometry.desiredHeight(scores, metrics) +
            paddingTop + paddingBottom

        setMeasuredDimension(measuredWidth, desiredHeight)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        geometry = LollipopChartGeometry.of(scores, metrics, w, h)?.also(::placeIcons)
    }

    private fun placeIcons(chart: LollipopChartGeometry) {
        for (i in 0 until minOf(criteria.size, icons.size)) {
            val icon = icons[i] ?: continue
            val bounds = chart.iconBounds(i, LollipopIconFit.of(criteria[i].criteria))
            icon.setBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)
            icon.setTintList(null)
        }
    }

    private fun dp(dpVal: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dpVal,
        resources.displayMetrics
    ).toInt()

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val hit = geometry?.hitTest(event.x, event.y)
            if (hit != null) {
                selectedIndex = selectionAfterTap(selectedIndex, hit)
                invalidate()
                onSelectionChangedListener?.onSelectionChanged(selectedIndex)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val chart = geometry ?: return

        val zeroY = chart.zeroY
        val circleRadius = metrics.circleRadius.toFloat()

        canvas.drawLine(chart.zeroLineStartX, zeroY, chart.zeroLineEndX, zeroY, zeroLinePaint)

        for (i in criteria.indices) {
            val color = colors[i]
            val cx = chart.centerXs[i]
            val barEndY = chart.barEndYs[i]

            barPaint.color = color
            canvas.drawLine(cx, zeroY, cx, barEndY, barPaint)

            if (i == selectedIndex) {
                selectedGlowPaint.color = color
                selectedGlowPaint.alpha = SELECTED_GLOW_ALPHA
                canvas.drawCircle(cx, barEndY, selectedGlowRadius, selectedGlowPaint)
            }

            canvas.drawCircle(cx, barEndY, circleRadius, circlePaint)
            circleStrokePaint.color = color
            canvas.drawCircle(cx, barEndY, circleRadius, circleStrokePaint)

            icons.getOrNull(i)?.draw(canvas)

            scorePaint.color = color
            canvas.drawText(scoreLabels[i], cx, chart.labelYs[i], scorePaint)
        }
    }
}
