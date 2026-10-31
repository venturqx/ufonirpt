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

enum class TournesolDateRange(val key: String) {
    FOREVER("forever"),
    YEAR("year"),
    THREE_MONTHS("3_months"),
    MONTH("month"),
    WEEK("week"),
    DAY("day");

    val isQuick: Boolean
        get() = when (this) {
            YEAR, THREE_MONTHS, MONTH, WEEK, DAY -> true
            FOREVER -> false
        }

    companion object {
        val DEFAULT = THREE_MONTHS

        fun fromKey(key: String?): TournesolDateRange = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

data class TournesolFilterConfig(
    val languages: List<String> = emptyList(),
    val dateRange: TournesolDateRange = TournesolDateRange.DEFAULT,
    val includeLowScoreVideos: Boolean = TournesolHelper.DEFAULT_TOURNESOL_FILTER_INCLUDE_LOW_SCORE,
    val durationMinSeconds: Int = TournesolHelper.DEFAULT_TOURNESOL_FILTER_DURATION_MIN,
    val durationMaxSeconds: Int = TournesolHelper.DEFAULT_TOURNESOL_FILTER_DURATION_MAX,
    val weights: Map<TournesolFilterCriterion, Int> = emptyMap()
) {
    fun weightFor(criterion: TournesolFilterCriterion): Int = weights[criterion] ?: TournesolHelper.DEFAULT_TOURNESOL_FILTER_WEIGHT

    fun hasActiveAdvancedFilters(): Boolean = !dateRange.isQuick ||
        durationMinSeconds >= 0 ||
        durationMaxSeconds >= 0 ||
        TournesolFilterCriterion.entries.any { weightFor(it) >= 0 }
}
