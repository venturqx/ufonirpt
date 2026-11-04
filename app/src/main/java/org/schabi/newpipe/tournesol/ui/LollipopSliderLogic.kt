package org.schabi.newpipe.tournesol.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import kotlin.math.roundToInt
import org.schabi.newpipe.R
import org.schabi.newpipe.tournesol.COMPACT_MAIN_CRITERION_ID
import org.schabi.newpipe.tournesol.CriteriaScore
import org.schabi.newpipe.tournesol.SCORE_MAX
import org.schabi.newpipe.tournesol.SCORE_MIN

internal data class QualifierLine(
    val leftArrow: String,
    val rightArrow: String,
    @StringRes val textRes: Int
)

private enum class ScoreStrength(val leftArrow: String, val rightArrow: String) {
    NEUTRAL(leftArrow = "", rightArrow = ""),
    SLIGHT(leftArrow = "<", rightArrow = ">"),
    STRONG(leftArrow = "<<", rightArrow = ">>")
}

private class QualifierTexts(
    @StringRes val neutral: Int,
    @StringRes val slight: Int,
    @StringRes val strong: Int
) {
    @StringRes
    fun at(strength: ScoreStrength): Int = when (strength) {
        ScoreStrength.NEUTRAL -> neutral
        ScoreStrength.SLIGHT -> slight
        ScoreStrength.STRONG -> strong
    }
}

private fun scoreStrength(score: Int): ScoreStrength = when (score) {
    in -15..15 -> ScoreStrength.NEUTRAL
    in -69..69 -> ScoreStrength.SLIGHT
    else -> ScoreStrength.STRONG
}

private val RECOMMENDED_QUALIFIERS = QualifierTexts(
    neutral = R.string.compare_qualifier_largely_recommended_even,
    slight = R.string.compare_qualifier_largely_recommended_slight,
    strong = R.string.compare_qualifier_largely_recommended_strong
)

private val GOODNESS_QUALIFIERS = QualifierTexts(
    neutral = R.string.compare_qualifier_goodness_even,
    slight = R.string.compare_qualifier_goodness_slight,
    strong = R.string.compare_qualifier_goodness_strong
)

private val CRITERION_QUALIFIERS = mapOf(
    "reliability" to QualifierTexts(
        neutral = R.string.compare_qualifier_reliability_even,
        slight = R.string.compare_qualifier_reliability_slight,
        strong = R.string.compare_qualifier_reliability_strong
    ),
    "pedagogy" to QualifierTexts(
        neutral = R.string.compare_qualifier_pedagogy_even,
        slight = R.string.compare_qualifier_pedagogy_slight,
        strong = R.string.compare_qualifier_pedagogy_strong
    ),
    "importance" to QualifierTexts(
        neutral = R.string.compare_qualifier_importance_even,
        slight = R.string.compare_qualifier_importance_slight,
        strong = R.string.compare_qualifier_importance_strong
    ),
    "layman_friendly" to QualifierTexts(
        neutral = R.string.compare_qualifier_layman_friendly_even,
        slight = R.string.compare_qualifier_layman_friendly_slight,
        strong = R.string.compare_qualifier_layman_friendly_strong
    ),
    "entertaining_relaxing" to QualifierTexts(
        neutral = R.string.compare_qualifier_entertaining_relaxing_even,
        slight = R.string.compare_qualifier_entertaining_relaxing_slight,
        strong = R.string.compare_qualifier_entertaining_relaxing_strong
    ),
    "engaging" to QualifierTexts(
        neutral = R.string.compare_qualifier_engaging_even,
        slight = R.string.compare_qualifier_engaging_slight,
        strong = R.string.compare_qualifier_engaging_strong
    ),
    "diversity_inclusion" to QualifierTexts(
        neutral = R.string.compare_qualifier_diversity_inclusion_even,
        slight = R.string.compare_qualifier_diversity_inclusion_slight,
        strong = R.string.compare_qualifier_diversity_inclusion_strong
    ),
    "better_habits" to GOODNESS_QUALIFIERS,
    "backfire_risk" to GOODNESS_QUALIFIERS
)

internal fun buildQualifierLine(criterionId: String, score: Int): QualifierLine {
    val strength = scoreStrength(score)
    return QualifierLine(
        leftArrow = if (score < 0) strength.leftArrow else "",
        rightArrow = if (score > 0) strength.rightArrow else "",
        textRes = (CRITERION_QUALIFIERS[criterionId] ?: RECOMMENDED_QUALIFIERS).at(strength)
    )
}

internal fun criterionIdAt(activeIndex: Int): String = if (activeIndex < 0 || activeIndex == EXTRA_CRITERIA.size) {
    COMPACT_MAIN_CRITERION_ID
} else {
    EXTRA_CRITERIA[activeIndex].id
}

internal fun criterionLabelRes(criterionId: String): Int = EXTRA_CRITERIA.firstOrNull { it.id == criterionId }?.labelRes ?: R.string.compare_criteria_largely_recommended

internal fun touchedExtraCount(touched: Set<String>): Int = EXTRA_CRITERIA.count { it.id in touched }

internal fun criterionScore(criterionId: String, mainScore: Int, scores: Map<String, Int>): Int = if (criterionId == COMPACT_MAIN_CRITERION_ID) mainScore else scores[criterionId] ?: 0

internal fun firstUntouchedExtraIndex(touched: Set<String>): Int? = EXTRA_CRITERIA.indexOfFirst { it.id !in touched }.takeIf { it >= 0 }

internal fun touchedExtraScores(touched: Set<String>, scores: Map<String, Int>): List<CriteriaScore> = EXTRA_CRITERIA.filter { it.id in touched }.map { CriteriaScore(it.id, scores[it.id] ?: 0) }

internal data class TrackFill(val left: Float, val right: Float)

internal fun trackFill(score: Int, thumbX: Float, centerX: Float, thumbGap: Float): TrackFill? {
    val fill = when {
        score < 0 -> TrackFill(left = thumbX + thumbGap, right = centerX)
        score > 0 -> TrackFill(left = centerX, right = thumbX - thumbGap)
        else -> return null
    }
    return fill.takeIf { it.right > it.left }
}

internal data class SliderDragController(
    val criterionId: String,
    val latestScore: () -> Int,
    val dispatchChange: (Int) -> Unit,
    val onSubmit1: (String, Int, () -> Unit) -> Unit,
    val isSubmitting: () -> Boolean,
    val isMainSaved: () -> Boolean,
    val touchedCriteria: MutableState<Set<String>>,
    val activeIndex: MutableIntState,
    val allExtrasDone: MutableState<Boolean>,
    val isDragging: MutableState<Boolean>,
    val isInCancelZone: MutableState<Boolean>,
    val dragScore: MutableState<Int?>?
) {
    fun start(score: Int): Set<String> {
        isDragging.value = true
        isInCancelZone.value = false
        dragScore?.value = score
        dispatchChange(score)
        val newTouched = touchedCriteria.value + criterionId
        touchedCriteria.value = newTouched
        return newTouched
    }

    fun move(score: Int, inCancelZone: Boolean) {
        isInCancelZone.value = inCancelZone
        dragScore?.value = score
        dispatchChange(score)
    }

    fun adjust(target: Float) {
        if (isSubmitting()) {
            return
        }
        dispatchChange(target.roundToInt().coerceIn(SCORE_MIN, SCORE_MAX))
        touchedCriteria.value += criterionId
    }

    fun confirm() {
        if (isSubmitting()) {
            return
        }
        val score = latestScore()
        release(score, score, start(score))
    }

    fun release(scoreBeforeDrag: Int, finalScore: Int, newTouched: Set<String>) {
        isDragging.value = false
        dragScore?.value = null
        if (isInCancelZone.value) {
            // Cancel: restore original score, don't advance
            isInCancelZone.value = false
            dispatchChange(scoreBeforeDrag)
            touchedCriteria.value = touchedCriteria.value - criterionId
            return
        }
        if (criterionId == COMPACT_MAIN_CRITERION_ID) {
            onSubmit1(criterionId, finalScore) {
                if (criterionIdAt(activeIndex.intValue) == criterionId) {
                    advance(newTouched, mainSaved = true)
                }
            }
        } else {
            advance(newTouched, isMainSaved())
        }
    }

    private fun advance(touched: Set<String>, mainSaved: Boolean) {
        val nextIndex = firstUntouchedExtraIndex(touched)
        when {
            nextIndex != null -> activeIndex.intValue = nextIndex
            mainSaved -> allExtrasDone.value = true
            else -> activeIndex.intValue = EXTRA_CRITERIA.size
        }
    }
}
