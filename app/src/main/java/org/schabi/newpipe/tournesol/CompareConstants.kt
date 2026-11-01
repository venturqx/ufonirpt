package org.schabi.newpipe.tournesol

internal const val SCORE_MIN = -100
internal const val SCORE_MAX = 100
internal const val REGISTER_URL = "https://tournesol.app/signup"
internal const val COMPACT_MAIN_CRITERION_ID = "largely_recommended"

internal data class CriteriaScore(
    val criteria: String,
    val score: Int
)

internal data class ComparePairSelection(
    val leftServiceId: Int,
    val leftUrl: String,
    val rightServiceId: Int,
    val rightUrl: String
)
