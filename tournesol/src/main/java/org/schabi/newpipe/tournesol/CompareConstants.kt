package org.schabi.newpipe.tournesol

const val SCORE_MIN = -100
const val SCORE_MAX = 100
const val REGISTER_URL = "https://tournesol.app/signup"
const val COMPACT_MAIN_CRITERION_ID = "largely_recommended"

data class CriteriaScore(
    val criteria: String,
    val score: Int
)

data class ComparePairSelection(
    val leftServiceId: Int,
    val leftUrl: String,
    val rightServiceId: Int,
    val rightUrl: String
)
