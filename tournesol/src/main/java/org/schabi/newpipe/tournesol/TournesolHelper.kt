package org.schabi.newpipe.tournesol

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory

object TournesolHelper {
    const val KIOSK_ID = "Tournesol"
    const val PREF_TOURNESOL_FILTER_LANGUAGES = "tournesol_filter_languages"
    const val PREF_TOURNESOL_FILTER_DATE_KEY = "tournesol_filter_date_key"
    const val PREF_TOURNESOL_FILTER_INCLUDE_LOW_SCORE = "tournesol_filter_include_low_score"
    const val PREF_TOURNESOL_FILTER_DURATION_MIN = "tournesol_filter_duration_min"
    const val PREF_TOURNESOL_FILTER_DURATION_MAX = "tournesol_filter_duration_max"

    /**
     * Returns the default filter languages based on system locale:
     * - French or English system → that language only
     * - Any other language → English + that language
     */
    @JvmOverloads
    fun getDefaultFilterLanguages(systemLang: String = Locale.getDefault().language): String {
        return when {
            systemLang == "fr" || systemLang == "en" -> systemLang
            systemLang.isBlank() -> "en"
            else -> "en,$systemLang"
        }
    }

    fun parseFilterLanguages(languages: String): List<String> = languages.split(",").filter { it.isNotEmpty() }

    const val DEFAULT_TOURNESOL_FILTER_INCLUDE_LOW_SCORE = false
    const val DEFAULT_TOURNESOL_FILTER_DURATION_MIN = -1
    const val DEFAULT_TOURNESOL_FILTER_DURATION_MAX = -1
    const val DEFAULT_TOURNESOL_FILTER_WEIGHT = -1
    private const val REASON_INSUFFICIENT_TOURNESOL_SCORE = "insufficient_tournesol_score"
    private const val REASON_INSUFFICIENT_TRUST = "insufficient_trust"

    @JvmOverloads
    fun buildTournesolUrl(
        config: TournesolFilterConfig,
        nowMillis: Long = System.currentTimeMillis()
    ): String = TournesolKioskQuery(
        includeUnsafe = config.includeLowScoreVideos,
        languages = config.languages,
        dateGte = calculateDateGte(config.dateRange, nowMillis),
        durationGte = config.durationMinSeconds,
        durationLte = config.durationMaxSeconds,
        weights = config.weights
    ).toUrl()

    @JvmStatic
    fun hasInsufficientReason(reasons: List<String>?): Boolean {
        return reasons?.any {
            it == REASON_INSUFFICIENT_TOURNESOL_SCORE || it == REASON_INSUFFICIENT_TRUST
        } == true
    }

    @JvmStatic
    fun withLowScoreSearchFilter(
        contentFilters: List<String>,
        includeLowScoreVideos: Boolean
    ): List<String> {
        val isTournesolSearch = contentFilters.firstOrNull() == YoutubeSearchQueryHandlerFactory.TOURNESOL
        val hasUnsafeFilter = YoutubeSearchQueryHandlerFactory.TOURNESOL_UNSAFE in contentFilters
        return if (isTournesolSearch && includeLowScoreVideos && !hasUnsafeFilter) {
            contentFilters + YoutubeSearchQueryHandlerFactory.TOURNESOL_UNSAFE
        } else {
            contentFilters
        }
    }

    @JvmOverloads
    fun calculateDateGte(
        dateRange: TournesolDateRange,
        nowMillis: Long = System.currentTimeMillis()
    ): String? {
        val utc = TimeZone.getTimeZone("UTC")
        val cal = Calendar.getInstance(utc, Locale.US).apply {
            timeInMillis = nowMillis
        }
        when (dateRange) {
            TournesolDateRange.YEAR -> cal.add(Calendar.YEAR, -1)
            TournesolDateRange.THREE_MONTHS -> cal.add(Calendar.MONTH, -3)
            TournesolDateRange.MONTH -> cal.add(Calendar.MONTH, -1)
            TournesolDateRange.WEEK -> cal.add(Calendar.WEEK_OF_YEAR, -1)
            TournesolDateRange.DAY -> cal.add(Calendar.DAY_OF_YEAR, -1)
            TournesolDateRange.FOREVER -> return null
        }
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = utc
        return sdf.format(cal.time)
    }
}
