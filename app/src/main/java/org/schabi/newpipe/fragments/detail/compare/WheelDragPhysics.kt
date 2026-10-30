package org.schabi.newpipe.fragments.detail.compare

import kotlin.math.abs

private const val FIRST_FRAME_DELTA_MS = 16L
private const val MS_PER_SECOND = 1000f
private const val SPEED_RETAIN = 0.75f
private const val SPEED_BLEND = 0.25f
private const val FULL_SPEED_PAGES_PER_SEC = 10f
private const val BASE_ACCELERATION = 0.72f
private const val ACCELERATION_RANGE = 0.83f
private const val DEAD_ZONE_PAGES = 0.5f
private const val BASE_DAMPING = 0.28f
private const val DAMPING_RANGE = 0.52f
private const val MAX_PAGE_OFFSET = 0.5f
private const val SWIPE_THRESHOLD_PAGES = 0.28f
private const val MIN_FLING_OFFSET_PAGES = 0.08f
private const val FLING_VELOCITY_PAGES_PER_SEC = 2.5f

internal data class WheelPageTarget(val page: Int, val offset: Float)

internal data class WheelDragState(
    val startPage: Int,
    val startPosition: Float,
    val position: Float,
    val minPage: Int,
    val maxPage: Int,
    val dragDistance: Float = 0f,
    val lastTimestampMs: Long? = null,
    val smoothedSpeedPxPerSec: Float = 0f
) {
    val pageTarget: WheelPageTarget
        get() {
            val floorPage = position.toInt()
            val page = if (position - floorPage > MAX_PAGE_OFFSET) floorPage + 1 else floorPage
            return WheelPageTarget(
                page,
                (position - page).coerceIn(-MAX_PAGE_OFFSET, MAX_PAGE_OFFSET)
            )
        }

    fun onDrag(dragDeltaY: Float, timestampMs: Long, pageStepPx: Float): WheelDragState {
        val deltaMs = lastTimestampMs?.let { (timestampMs - it).coerceAtLeast(1L) }
            ?: FIRST_FRAME_DELTA_MS
        val instantSpeedPxPerSec = abs(dragDeltaY) * MS_PER_SECOND / deltaMs
        val smoothedSpeed = if (smoothedSpeedPxPerSec == 0f) {
            instantSpeedPxPerSec
        } else {
            smoothedSpeedPxPerSec * SPEED_RETAIN + instantSpeedPxPerSec * SPEED_BLEND
        }

        // Slow drag => finer control; fast swipe => more travel.
        val normalizedSpeed =
            (smoothedSpeed / (pageStepPx * FULL_SPEED_PAGES_PER_SEC)).coerceIn(0f, 1f)
        val accelerationMultiplier = BASE_ACCELERATION + normalizedSpeed * ACCELERATION_RANGE
        val newDragDistance = dragDistance + dragDeltaY * accelerationMultiplier
        val visualDelta = dampedDelta(-newDragDistance / pageStepPx, normalizedSpeed)
        return copy(
            position = (startPosition + visualDelta).coerceIn(minPage.toFloat(), maxPage.toFloat()),
            dragDistance = newDragDistance,
            lastTimestampMs = timestampMs,
            smoothedSpeedPxPerSec = smoothedSpeed
        )
    }

    fun releaseTarget(velocityY: Float, pageStepPx: Float): Int {
        val deltaFromStart = -dragDistance / pageStepPx
        val isFling = abs(velocityY) > pageStepPx * FLING_VELOCITY_PAGES_PER_SEC &&
            abs(deltaFromStart) > MIN_FLING_OFFSET_PAGES
        val target = when {
            deltaFromStart > SWIPE_THRESHOLD_PAGES -> startPage + 1
            deltaFromStart < -SWIPE_THRESHOLD_PAGES -> startPage - 1
            isFling -> if (velocityY < 0f) startPage + 1 else startPage - 1
            else -> startPage
        }
        return target.coerceIn(minPage, maxPage)
    }

    companion object {
        fun start(page: Int, pageOffsetFraction: Float, lastIndex: Int): WheelDragState {
            val position = page + pageOffsetFraction
            return WheelDragState(
                startPage = page,
                startPosition = position,
                position = position,
                minPage = (page - 1).coerceIn(0, lastIndex),
                maxPage = (page + 1).coerceIn(0, lastIndex)
            )
        }
    }
}

private fun dampedDelta(rawDelta: Float, normalizedSpeed: Float): Float {
    val absRawDelta = abs(rawDelta)
    if (absRawDelta <= DEAD_ZONE_PAGES) {
        return rawDelta
    }
    val excess = absRawDelta - DEAD_ZONE_PAGES
    val dampingSlope = BASE_DAMPING + normalizedSpeed * DAMPING_RANGE
    val damped = DEAD_ZONE_PAGES + excess * dampingSlope
    return if (rawDelta < 0f) -damped else damped
}
