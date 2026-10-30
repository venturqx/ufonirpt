package org.schabi.newpipe.util

import android.content.SharedPreferences

class TournesolFilterStore(private val prefs: SharedPreferences) {
    fun load(
        defaultLanguages: String = TournesolHelper.getDefaultFilterLanguages()
    ): TournesolFilterConfig = TournesolFilterConfig(
        languages = TournesolHelper.parseFilterLanguages(
            prefs.getString(TournesolHelper.PREF_TOURNESOL_FILTER_LANGUAGES, defaultLanguages).orEmpty()
        ),
        dateKey = prefs.getString(
            TournesolHelper.PREF_TOURNESOL_FILTER_DATE_KEY,
            TournesolHelper.DEFAULT_TOURNESOL_FILTER_DATE_KEY
        ) ?: TournesolHelper.DEFAULT_TOURNESOL_FILTER_DATE_KEY,
        includeLowScoreVideos = prefs.getBoolean(
            TournesolHelper.PREF_TOURNESOL_FILTER_INCLUDE_LOW_SCORE,
            TournesolHelper.DEFAULT_TOURNESOL_FILTER_INCLUDE_LOW_SCORE
        ),
        durationMinSeconds = prefs.getInt(
            TournesolHelper.PREF_TOURNESOL_FILTER_DURATION_MIN,
            TournesolHelper.DEFAULT_TOURNESOL_FILTER_DURATION_MIN
        ),
        durationMaxSeconds = prefs.getInt(
            TournesolHelper.PREF_TOURNESOL_FILTER_DURATION_MAX,
            TournesolHelper.DEFAULT_TOURNESOL_FILTER_DURATION_MAX
        ),
        weights = TournesolFilterCriterion.entries.associateWith { criterion ->
            prefs.getInt(criterion.prefKey, TournesolHelper.DEFAULT_TOURNESOL_FILTER_WEIGHT)
        }
    )

    fun save(config: TournesolFilterConfig) {
        val editor = prefs.edit()
            .putString(
                TournesolHelper.PREF_TOURNESOL_FILTER_LANGUAGES,
                config.languages.joinToString(",")
            )
            .putString(TournesolHelper.PREF_TOURNESOL_FILTER_DATE_KEY, config.dateKey)
            .putBoolean(
                TournesolHelper.PREF_TOURNESOL_FILTER_INCLUDE_LOW_SCORE,
                config.includeLowScoreVideos
            )
            .putInt(
                TournesolHelper.PREF_TOURNESOL_FILTER_DURATION_MIN,
                config.durationMinSeconds
            )
            .putInt(
                TournesolHelper.PREF_TOURNESOL_FILTER_DURATION_MAX,
                config.durationMaxSeconds
            )
        for (criterion in TournesolFilterCriterion.entries) {
            editor.putInt(criterion.prefKey, config.weightFor(criterion))
        }
        editor.apply()
    }
}
