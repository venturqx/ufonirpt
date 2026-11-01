package org.schabi.newpipe.tournesol.ui

import androidx.compose.runtime.staticCompositionLocalOf
import org.schabi.newpipe.R
import org.schabi.newpipe.tournesol.COMPACT_MAIN_CRITERION_ID

private const val COMPARE_SCALE_REFERENCE_WIDTH = 500f
private const val COMPARE_SCALE_MIN_WIDTH = 200f
private const val COMPARE_SCALE_FLOOR = 0.55f

internal fun computeCompareScale(maxWidthDp: Float): Float {
    val range = COMPARE_SCALE_REFERENCE_WIDTH - COMPARE_SCALE_MIN_WIDTH
    val raw = (maxWidthDp - COMPARE_SCALE_MIN_WIDTH) / range
    return raw.coerceIn(COMPARE_SCALE_FLOOR, 1f)
}

internal val LocalCompareScale = staticCompositionLocalOf { 1f }

internal enum class OverlayTarget {
    LEFT,
    RIGHT
}

internal fun compactDescriptionRes(criterionId: String): Int {
    return when (criterionId) {
        COMPACT_MAIN_CRITERION_ID -> R.string.compare_criteria_desc_largely_recommended
        "reliability" -> R.string.compare_criteria_desc_reliability
        "pedagogy" -> R.string.compare_criteria_desc_pedagogy
        "importance" -> R.string.compare_criteria_desc_importance
        "layman_friendly" -> R.string.compare_criteria_desc_layman_friendly
        "entertaining_relaxing" -> R.string.compare_criteria_desc_entertaining_relaxing
        "engaging" -> R.string.compare_criteria_desc_engaging
        "diversity_inclusion" -> R.string.compare_criteria_desc_diversity_inclusion
        "better_habits" -> R.string.compare_criteria_desc_better_habits
        "backfire_risk" -> R.string.compare_criteria_desc_backfire_risk
        else -> R.string.compare_criteria_desc_largely_recommended
    }
}
