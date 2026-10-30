package org.schabi.newpipe.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.LinkedHashSet
import java.util.Locale
import java.util.TimeZone

data class TournesolCacheEntry(val score: Long, val unsafeReasons: List<String>)

class TournesolLruScoreCache(private val maxEntries: Int = 200) {
    private val cache = LinkedHashMap<String, TournesolCacheEntry>(32, 0.75f, true)

    fun put(url: String, score: Long, unsafeReasons: List<String> = emptyList()) {
        synchronized(cache) {
            cache[url] = TournesolCacheEntry(score, unsafeReasons)
            if (cache.size > maxEntries) {
                cache.remove(cache.keys.first())
            }
        }
    }

    fun get(url: String): Long? = synchronized(cache) { cache[url]?.score }

    fun getEntry(url: String): TournesolCacheEntry? = synchronized(cache) { cache[url] }

    fun clear() = synchronized(cache) { cache.clear() }
}

object TournesolScoreCache {
    private val delegate = TournesolLruScoreCache(maxEntries = 200)

    fun put(url: String, score: Long, unsafeReasons: List<String> = emptyList()) = delegate.put(url, score, unsafeReasons)

    fun get(url: String): Long? = delegate.get(url)

    fun getEntry(url: String): TournesolCacheEntry? = delegate.getEntry(url)

    fun clear() = delegate.clear()
}

enum class TournesolFilterCriterion(val apiKey: String, val prefKey: String) {
    LARGELY_RECOMMENDED(
        "largely_recommended",
        TournesolHelper.PREF_TOURNESOL_FILTER_WEIGHT_LARGELY_RECOMMENDED
    ),
    RELIABILITY(
        "reliability",
        TournesolHelper.PREF_TOURNESOL_FILTER_WEIGHT_RELIABILITY
    ),
    IMPORTANCE(
        "importance",
        TournesolHelper.PREF_TOURNESOL_FILTER_WEIGHT_IMPORTANCE
    ),
    PEDAGOGY(
        "pedagogy",
        TournesolHelper.PREF_TOURNESOL_FILTER_WEIGHT_PEDAGOGY
    ),
    LAYMAN_FRIENDLY(
        "layman_friendly",
        TournesolHelper.PREF_TOURNESOL_FILTER_WEIGHT_LAYMAN_FRIENDLY
    ),
    ENTERTAINING_RELAXING(
        "entertaining_relaxing",
        TournesolHelper.PREF_TOURNESOL_FILTER_WEIGHT_ENTERTAINING_RELAXING
    ),
    ENGAGING(
        "engaging",
        TournesolHelper.PREF_TOURNESOL_FILTER_WEIGHT_ENGAGING
    ),
    DIVERSITY_INCLUSION(
        "diversity_inclusion",
        TournesolHelper.PREF_TOURNESOL_FILTER_WEIGHT_DIVERSITY_INCLUSION
    ),
    BETTER_HABITS(
        "better_habits",
        TournesolHelper.PREF_TOURNESOL_FILTER_WEIGHT_BETTER_HABITS
    ),
    BACKFIRE_RISK(
        "backfire_risk",
        TournesolHelper.PREF_TOURNESOL_FILTER_WEIGHT_BACKFIRE_RISK
    )
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

object TournesolHelper {
    const val KIOSK_ID = "Tournesol"
    const val PREF_TOURNESOL_FILTER_LANGUAGES = "tournesol_filter_languages"
    const val PREF_TOURNESOL_FILTER_DATE_KEY = "tournesol_filter_date_key"
    const val PREF_TOURNESOL_FILTER_INCLUDE_LOW_SCORE = "tournesol_filter_include_low_score"
    const val PREF_TOURNESOL_FILTER_DURATION_MIN = "tournesol_filter_duration_min"
    const val PREF_TOURNESOL_FILTER_DURATION_MAX = "tournesol_filter_duration_max"
    const val PREF_TOURNESOL_FILTER_WEIGHT_LARGELY_RECOMMENDED = "tournesol_filter_weight_largely_recommended"
    const val PREF_TOURNESOL_FILTER_WEIGHT_RELIABILITY = "tournesol_filter_weight_reliability"
    const val PREF_TOURNESOL_FILTER_WEIGHT_IMPORTANCE = "tournesol_filter_weight_importance"
    const val PREF_TOURNESOL_FILTER_WEIGHT_PEDAGOGY = "tournesol_filter_weight_pedagogy"
    const val PREF_TOURNESOL_FILTER_WEIGHT_LAYMAN_FRIENDLY = "tournesol_filter_weight_layman_friendly"
    const val PREF_TOURNESOL_FILTER_WEIGHT_ENTERTAINING_RELAXING = "tournesol_filter_weight_entertaining_relaxing"
    const val PREF_TOURNESOL_FILTER_WEIGHT_ENGAGING = "tournesol_filter_weight_engaging"
    const val PREF_TOURNESOL_FILTER_WEIGHT_DIVERSITY_INCLUSION = "tournesol_filter_weight_diversity_inclusion"
    const val PREF_TOURNESOL_FILTER_WEIGHT_BETTER_HABITS = "tournesol_filter_weight_better_habits"
    const val PREF_TOURNESOL_FILTER_WEIGHT_BACKFIRE_RISK = "tournesol_filter_weight_backfire_risk"

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
    const val DEFAULT_TOURNESOL_FILTER_DATE_KEY = "3_months"
    const val DEFAULT_TOURNESOL_FILTER_INCLUDE_LOW_SCORE = false
    const val DEFAULT_TOURNESOL_FILTER_DURATION_MIN = -1
    const val DEFAULT_TOURNESOL_FILTER_DURATION_MAX = -1
    const val DEFAULT_TOURNESOL_FILTER_WEIGHT = -1
    private const val REASON_INSUFFICIENT_TOURNESOL_SCORE = "insufficient_tournesol_score"
    private const val REASON_INSUFFICIENT_TRUST = "insufficient_trust"
    private const val REASON_MODERATION_BY_ASSOCIATION = "moderation_by_association"
    private const val REASON_MODERATION_BY_CONTRIBUTORS = "moderation_by_contributors"

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

    fun buildTournesolUrl(
        languages: List<String>?,
        dateKey: String,
        includeLowScoreVideos: Boolean,
        durationMinSeconds: Int = DEFAULT_TOURNESOL_FILTER_DURATION_MIN,
        durationMaxSeconds: Int = DEFAULT_TOURNESOL_FILTER_DURATION_MAX,
        weightLargelyRecommended: Int = DEFAULT_TOURNESOL_FILTER_WEIGHT,
        weightReliability: Int = DEFAULT_TOURNESOL_FILTER_WEIGHT,
        weightImportance: Int = DEFAULT_TOURNESOL_FILTER_WEIGHT,
        weightPedagogy: Int = DEFAULT_TOURNESOL_FILTER_WEIGHT,
        weightLaymanFriendly: Int = DEFAULT_TOURNESOL_FILTER_WEIGHT,
        weightEntertainingRelaxing: Int = DEFAULT_TOURNESOL_FILTER_WEIGHT,
        weightEngaging: Int = DEFAULT_TOURNESOL_FILTER_WEIGHT,
        weightDiversityInclusion: Int = DEFAULT_TOURNESOL_FILTER_WEIGHT,
        weightBetterHabits: Int = DEFAULT_TOURNESOL_FILTER_WEIGHT,
        weightBackfireRisk: Int = DEFAULT_TOURNESOL_FILTER_WEIGHT
    ): String = buildTournesolUrl(
        TournesolFilterConfig(
            languages = languages.orEmpty(),
            dateKey = dateKey,
            includeLowScoreVideos = includeLowScoreVideos,
            durationMinSeconds = durationMinSeconds,
            durationMaxSeconds = durationMaxSeconds,
            weights = mapOf(
                TournesolFilterCriterion.LARGELY_RECOMMENDED to weightLargelyRecommended,
                TournesolFilterCriterion.RELIABILITY to weightReliability,
                TournesolFilterCriterion.IMPORTANCE to weightImportance,
                TournesolFilterCriterion.PEDAGOGY to weightPedagogy,
                TournesolFilterCriterion.LAYMAN_FRIENDLY to weightLaymanFriendly,
                TournesolFilterCriterion.ENTERTAINING_RELAXING to weightEntertainingRelaxing,
                TournesolFilterCriterion.ENGAGING to weightEngaging,
                TournesolFilterCriterion.DIVERSITY_INCLUSION to weightDiversityInclusion,
                TournesolFilterCriterion.BETTER_HABITS to weightBetterHabits,
                TournesolFilterCriterion.BACKFIRE_RISK to weightBackfireRisk
            )
        )
    )

    fun buildTournesolUrl(languages: List<String>?, dateKey: String): String = buildTournesolUrl(languages, dateKey, false)

    @JvmStatic
    fun formatUnsafeReasonCodes(reasons: List<String>?): String {
        if (reasons.isNullOrEmpty()) {
            return ""
        }

        val reasonLabels = LinkedHashSet<String>()
        for (reason in reasons) {
            val reasonLabel = when (reason) {
                REASON_INSUFFICIENT_TOURNESOL_SCORE,
                REASON_INSUFFICIENT_TRUST -> "Insufficient"

                REASON_MODERATION_BY_ASSOCIATION,
                REASON_MODERATION_BY_CONTRIBUTORS -> "Moderated"

                else -> reason
            }
            if (reasonLabel.isNotBlank()) {
                reasonLabels.add(reasonLabel)
            }
        }

        if (reasonLabels.isEmpty()) {
            return ""
        }

        return "(" + reasonLabels.joinToString("/") + ")"
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

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = utc
        return sdf.format(cal.time)
    }
}
