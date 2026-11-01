package org.schabi.newpipe.tournesol.ui

import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import org.schabi.newpipe.tournesol.COMPACT_MAIN_CRITERION_ID
import org.schabi.newpipe.tournesol.CriteriaScore

internal data class QualifierLine(
    val leftArrow: String,
    val rightArrow: String,
    val qualifier: String,
    val tail: String
)

private enum class ScoreStrength(
    val leftArrow: String,
    val rightArrow: String,
    val comparative: String,
    val goodness: String
) {
    NEUTRAL(leftArrow = "", rightArrow = "", comparative = "just as", goodness = "as good"),
    SLIGHT(leftArrow = "<", rightArrow = ">", comparative = "slightly more", goodness = "slightly better"),
    STRONG(leftArrow = "<<", rightArrow = ">>", comparative = "much more", goodness = "much better")
}

private fun scoreStrength(score: Int): ScoreStrength = when (score) {
    in -15..15 -> ScoreStrength.NEUTRAL
    in -69..69 -> ScoreStrength.SLIGHT
    else -> ScoreStrength.STRONG
}

private val GOODNESS_CRITERIA = setOf("better_habits", "backfire_risk")

private val CRITERION_TAILS = mapOf(
    "reliability" to " reliable",
    "pedagogy" to " pedagogical",
    "importance" to " important",
    "layman_friendly" to " accessible",
    "entertaining_relaxing" to " entertaining",
    "engaging" to " engaging",
    "diversity_inclusion" to " inclusive"
)

internal fun buildQualifierLine(criterionId: String, score: Int): QualifierLine {
    val strength = scoreStrength(score)
    val isGoodness = criterionId in GOODNESS_CRITERIA
    return QualifierLine(
        leftArrow = if (score < 0) strength.leftArrow else "",
        rightArrow = if (score > 0) strength.rightArrow else "",
        qualifier = if (isGoodness) strength.goodness else strength.comparative,
        tail = if (isGoodness) "" else CRITERION_TAILS[criterionId] ?: " recommended"
    )
}

internal fun criterionIdAt(activeIndex: Int): String = if (activeIndex < 0 || activeIndex == EXTRA_CRITERIA.size) {
    COMPACT_MAIN_CRITERION_ID
} else {
    EXTRA_CRITERIA[activeIndex].id
}

internal fun activeExtraNumber(activeIndex: Int): Int = if (activeIndex in EXTRA_CRITERIA.indices) activeIndex + 1 else 0

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
    val onSubmit1: (String, Int) -> Unit,
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
            onSubmit1(criterionId, finalScore)
        }
        val nextIndex = firstUntouchedExtraIndex(newTouched)
        if (nextIndex != null) {
            activeIndex.intValue = nextIndex
        } else {
            allExtrasDone.value = true
        }
    }
}
