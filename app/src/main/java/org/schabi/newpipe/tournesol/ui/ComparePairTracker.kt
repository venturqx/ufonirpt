package org.schabi.newpipe.tournesol.ui

import org.schabi.newpipe.tournesol.COMPACT_MAIN_CRITERION_ID
import org.schabi.newpipe.tournesol.CompareKey
import org.schabi.newpipe.tournesol.ComparePairSelection
import org.schabi.newpipe.tournesol.CompareScoreStore
import org.schabi.newpipe.tournesol.ComparisonScores
import org.schabi.newpipe.tournesol.CriteriaScore
import org.schabi.newpipe.tournesol.compareKeyFor
import org.schabi.newpipe.tournesol.scoresWithCriterion

internal class ComparePairTracker(
    private val state: CompareUiState,
    private val store: CompareScoreStore,
    private val uidOf: (url: String, serviceId: Int) -> String?
) {
    private var selection: ComparePairSelection? = null

    val hasSelection: Boolean
        get() = selection != null

    val key: CompareKey?
        get() = selection?.let { compareKeyFor(it, uidOf) }

    fun select(newSelection: ComparePairSelection?): Boolean {
        if (selection == newSelection) {
            return false
        }
        selection = newSelection
        return true
    }

    fun clearSelection() {
        selection = null
    }

    fun isSubmitted(key: CompareKey): Boolean = store.isSubmitted(key)

    fun reload() {
        state.score = 0
        state.extraScores = defaultExtraScores()
        state.storedScores = ComparisonScores()
        applyStoredScores()
        state.submitted = key?.let(store::isSubmitted) == true
    }

    fun restoreScores(criteriaScores: List<CriteriaScore>) {
        criteriaScores.forEach {
            if (it.criteria == COMPACT_MAIN_CRITERION_ID) {
                state.score = it.score
            } else {
                state.updateExtraScore(it.criteria, it.score)
            }
        }
    }

    fun applyRemoteResult(key: CompareKey, exists: Boolean) {
        if (this.key != key) {
            return
        }
        if (exists) {
            markSubmitted(key)
        } else {
            store.forget(key)
            state.storedScores = ComparisonScores()
            state.submitted = false
        }
    }

    fun recordCriterion(key: CompareKey, criterionId: String, value: Int) {
        markSubmitted(key)
        val updated = scoresWithCriterion(store.scoresFor(key), criterionId, value)
        record(key, updated.mainScore, updated.extraScores)
    }

    fun recordExtras(key: CompareKey, criteriaScores: List<CriteriaScore>) {
        val merged = store.scoresFor(key)?.extraScores.orEmpty() +
            criteriaScores.associate { it.criteria to it.score }
        record(key, extraScores = merged)
    }

    private fun applyStoredScores() {
        val key = key ?: return
        val stored = store.scoresFor(key) ?: return
        state.storedScores = stored
        stored.mainScore?.let { state.score = it }
        if (stored.extraScores.isNotEmpty()) {
            state.extraScores = defaultExtraScores() + stored.extraScores
        }
    }

    private fun markSubmitted(key: CompareKey) {
        store.markSubmitted(key)
        if (this.key == key) {
            state.submitted = true
        }
    }

    private fun record(key: CompareKey, mainScore: Int? = null, extraScores: Map<String, Int>? = null) {
        val merged = store.recordScores(key, mainScore, extraScores) ?: return
        if (this.key == key) {
            state.storedScores = merged
        }
    }
}
