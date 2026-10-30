package org.schabi.newpipe.util

enum class TournesolFilterCriterion(val apiKey: String) {
    LARGELY_RECOMMENDED("largely_recommended"),
    RELIABILITY("reliability"),
    IMPORTANCE("importance"),
    PEDAGOGY("pedagogy"),
    LAYMAN_FRIENDLY("layman_friendly"),
    ENTERTAINING_RELAXING("entertaining_relaxing"),
    ENGAGING("engaging"),
    DIVERSITY_INCLUSION("diversity_inclusion"),
    BETTER_HABITS("better_habits"),
    BACKFIRE_RISK("backfire_risk");

    val prefKey: String = "tournesol_filter_weight_$apiKey"
}

data class TournesolFilterConfig(
    val languages: List<String> = emptyList(),
    val dateKey: String = TournesolHelper.DEFAULT_TOURNESOL_FILTER_DATE_KEY,
    val includeLowScoreVideos: Boolean = TournesolHelper.DEFAULT_TOURNESOL_FILTER_INCLUDE_LOW_SCORE,
    val durationMinSeconds: Int = TournesolHelper.DEFAULT_TOURNESOL_FILTER_DURATION_MIN,
    val durationMaxSeconds: Int = TournesolHelper.DEFAULT_TOURNESOL_FILTER_DURATION_MAX,
    val weights: Map<TournesolFilterCriterion, Int> = emptyMap()
) {
    fun weightFor(criterion: TournesolFilterCriterion): Int = weights[criterion] ?: TournesolHelper.DEFAULT_TOURNESOL_FILTER_WEIGHT

    fun hasActiveAdvancedFilters(quickDateKeys: Set<String>): Boolean = !quickDateKeys.contains(dateKey) ||
        durationMinSeconds >= 0 ||
        durationMaxSeconds >= 0 ||
        TournesolFilterCriterion.entries.any { weightFor(it) >= 0 }
}
