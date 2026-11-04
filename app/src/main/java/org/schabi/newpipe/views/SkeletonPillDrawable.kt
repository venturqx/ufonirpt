package org.schabi.newpipe.views

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

class SkeletonPillDrawable(color: Int, private val heightFraction: Float) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    override fun draw(canvas: Canvas) {
        val height = bounds.height() * heightFraction
        val top = bounds.exactCenterY() - height / 2f
        val radius = height / 2f
        canvas.drawRoundRect(
            bounds.left.toFloat(),
            top,
            bounds.right.toFloat(),
            top + height,
            radius,
            radius,
            paint
        )
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
