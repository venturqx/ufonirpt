package org.schabi.newpipe.fragments.detail

internal fun compareKeyFor(
    selection: ComparePairSelection,
    uidOf: (url: String, serviceId: Int) -> String?
): CompareKey? {
    val leftUid = uidOf(selection.leftUrl, selection.leftServiceId) ?: return null
    val rightUid = uidOf(selection.rightUrl, selection.rightServiceId) ?: return null
    return CompareKey(entityA = leftUid, entityB = rightUid)
}

internal fun scoresWithCriterion(
    stored: ComparisonScores?,
    criterionId: String,
    value: Int
): ComparisonScores {
    val base = stored ?: ComparisonScores()
    return if (criterionId == COMPACT_MAIN_CRITERION_ID) {
        base.copy(mainScore = value)
    } else {
        base.copy(extraScores = base.extraScores + (criterionId to value))
    }
}
