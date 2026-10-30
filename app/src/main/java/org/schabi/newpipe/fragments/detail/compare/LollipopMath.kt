package org.schabi.newpipe.fragments.detail.compare

import kotlin.math.roundToInt
import org.schabi.newpipe.fragments.detail.SCORE_MAX
import org.schabi.newpipe.fragments.detail.SCORE_MIN

private const val SCORE_RANGE = (SCORE_MAX - SCORE_MIN).toFloat()
private const val DOUBLE_TAP_WINDOW_MS = 300L

internal fun positionToScore(position: Float, minScoreAt: Float, maxScoreAt: Float): Int {
    val span = maxScoreAt - minScoreAt
    if (span == 0f) {
        return 0
    }
    val ratio = ((position - minScoreAt) / span).coerceIn(0f, 1f)
    return (ratio * SCORE_RANGE + SCORE_MIN).roundToInt().coerceIn(SCORE_MIN, SCORE_MAX)
}

internal fun scoreToPosition(score: Int, minScoreAt: Float, maxScoreAt: Float): Float {
    val ratio = (score - SCORE_MIN) / SCORE_RANGE
    return minScoreAt + ratio * (maxScoreAt - minScoreAt)
}

internal fun slotIndexAt(x: Float, width: Float, slotCount: Int): Int {
    val slotWidth = width / slotCount
    return (x / slotWidth).toInt().coerceIn(0, slotCount - 1)
}

internal data class PendingTap(val index: Int, val uptimeMs: Long)

internal data class TapOutcome(val pending: PendingTap?, val isDoubleTap: Boolean)

internal fun registerTap(pending: PendingTap?, index: Int, uptimeMs: Long): TapOutcome {
    val isDoubleTap = pending != null &&
        pending.index == index &&
        uptimeMs - pending.uptimeMs < DOUBLE_TAP_WINDOW_MS
    return if (isDoubleTap) {
        TapOutcome(pending = null, isDoubleTap = true)
    } else {
        TapOutcome(pending = PendingTap(index, uptimeMs), isDoubleTap = false)
    }
}
