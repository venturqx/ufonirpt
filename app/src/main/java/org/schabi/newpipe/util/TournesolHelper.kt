package org.schabi.newpipe.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

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

    const val DEFAULT_TOURNESOL_FILTER_DATE_KEY = "3_months"
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
    ): String {
        val sb = StringBuilder(KIOSK_ID)
        sb.append("?languages=").append(config.languages.joinToString(","))

        val dateGte = calculateDateGte(config.dateKey, nowMillis)
        if (dateGte != null) {
            sb.append("&date_gte=").append(dateGte)
        }
        if (config.includeLowScoreVideos) {
            sb.append("&unsafe=true")
        }
        if (config.durationMinSeconds >= 0) {
            sb.append("&duration_gte=").append(config.durationMinSeconds)
        }
        if (config.durationMaxSeconds >= 0) {
            sb.append("&duration_lte=").append(config.durationMaxSeconds)
        }
        for (criterion in TournesolFilterCriterion.entries) {
            val weight = config.weightFor(criterion)
            if (weight >= 0) {
                sb.append("&weight_").append(criterion.apiKey).append("=").append(weight)
            }
        }
        return sb.toString()
    }

    @JvmStatic
    fun hasInsufficientReason(reasons: List<String>?): Boolean {
        return reasons?.any {
            it == REASON_INSUFFICIENT_TOURNESOL_SCORE || it == REASON_INSUFFICIENT_TRUST
        } == true
    }

    @JvmOverloads
    fun calculateDateGte(
        dateKey: String,
        nowMillis: Long = System.currentTimeMillis()
    ): String? {
        val utc = TimeZone.getTimeZone("UTC")
        val cal = Calendar.getInstance(utc, Locale.US).apply {
            timeInMillis = nowMillis
        }
        when (dateKey) {
            "year" -> cal.add(Calendar.YEAR, -1)
            "3_months" -> cal.add(Calendar.MONTH, -3)
            "month" -> cal.add(Calendar.MONTH, -1)
            "week" -> cal.add(Calendar.WEEK_OF_YEAR, -1)
            "day" -> cal.add(Calendar.DAY_OF_YEAR, -1)
            else -> return null
        }
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = utc
        return sdf.format(cal.time)
    }
}
