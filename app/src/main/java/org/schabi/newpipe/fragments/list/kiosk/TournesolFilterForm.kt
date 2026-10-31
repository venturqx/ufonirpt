package org.schabi.newpipe.fragments.list.kiosk

import org.schabi.newpipe.util.TournesolDateRange
import org.schabi.newpipe.util.TournesolFilterConfig
import org.schabi.newpipe.util.TournesolFilterCriterion

internal const val DURATION_SLIDER_MAX = 120f

internal const val PREF_TOURNESOL_FILTER_WEIGHTS_ENABLED = "tournesol_filter_weights_enabled"

private const val UNSET = -1
private const val SECONDS_PER_MINUTE = 60f

private val WEIGHT_LEVELS = intArrayOf(0, 25, 50, 75, 100)

internal val TournesolFilterCriterion.weightLevelPrefKey: String
    get() = "tournesol_filter_weight_level_$apiKey"

internal fun quantizeToLevel(value: Int): Int {
    if (value < 0) return 50
    return WEIGHT_LEVELS.minByOrNull { kotlin.math.abs(it - value) } ?: 50
}

internal data class TournesolFilterForm(
    val languages: List<String>,
    val dateRange: TournesolDateRange,
    val includeLowScoreVideos: Boolean,
    val durationMinMinutes: Float,
    val durationMaxMinutes: Float,
    val weightsEnabled: Boolean,
    val weightLevels: Map<TournesolFilterCriterion, Int>
) {
    fun toConfig(): TournesolFilterConfig = TournesolFilterConfig(
        languages = languages,
        dateRange = dateRange,
        includeLowScoreVideos = includeLowScoreVideos,
        durationMinSeconds = if (durationMinMinutes <= 0f) UNSET else toSeconds(durationMinMinutes),
        durationMaxSeconds = if (durationMaxMinutes >= DURATION_SLIDER_MAX) UNSET else toSeconds(durationMaxMinutes),
        weights = TournesolFilterCriterion.entries.associateWith { criterion ->
            if (weightsEnabled) weightLevels.getValue(criterion) else UNSET
        }
    )

    companion object {
        fun from(
            config: TournesolFilterConfig,
            weightsEnabled: Boolean,
            storedLevel: (TournesolFilterCriterion) -> Int?
        ): TournesolFilterForm = TournesolFilterForm(
            languages = config.languages,
            dateRange = config.dateRange,
            includeLowScoreVideos = config.includeLowScoreVideos,
            durationMinMinutes = if (config.durationMinSeconds >= 0) toMinutes(config.durationMinSeconds) else 0f,
            durationMaxMinutes = if (config.durationMaxSeconds >= 0) toMinutes(config.durationMaxSeconds) else DURATION_SLIDER_MAX,
            weightsEnabled = weightsEnabled,
            weightLevels = TournesolFilterCriterion.entries.associateWith { criterion ->
                quantizeToLevel(storedLevel(criterion) ?: config.weightFor(criterion))
            }
        )
    }
}

private fun toSeconds(minutes: Float): Int = (minutes * SECONDS_PER_MINUTE).toInt()

private fun toMinutes(seconds: Int): Float = seconds / SECONDS_PER_MINUTE
