package org.schabi.newpipe.util

import java.net.URLDecoder
import org.schabi.newpipe.extractor.services.youtube.extractors.kiosk.TournesolKioskExtractor

data class TournesolKioskQuery(
    val includeUnsafe: Boolean,
    val languages: List<String>? = null,
    val dateGte: String? = null,
    val uploader: String? = null,
    val durationGte: Int = UNSET,
    val durationLte: Int = UNSET,
    val weights: Map<TournesolFilterCriterion, Int> = emptyMap()
) {
    fun weightFor(criterion: TournesolFilterCriterion): Int = weights[criterion] ?: UNSET

    fun applyTo(extractor: TournesolKioskExtractor) {
        languages?.let { extractor.setLanguages(it) }
        extractor.setDateGte(dateGte)
        extractor.setUploader(uploader)
        extractor.setIncludeUnsafe(includeUnsafe)
        extractor.setDurationGte(durationGte)
        extractor.setDurationLte(durationLte)
        for (criterion in TournesolFilterCriterion.entries) {
            extractor.setWeight(criterion, weightFor(criterion))
        }
    }

    private fun withParam(key: String, value: String): TournesolKioskQuery = when (key) {
        "languages" -> copy(languages = value.split(",").dropLastWhile { it.isEmpty() })

        "date_gte" -> copy(dateGte = value)

        "uploader" -> copy(uploader = value)

        "unsafe" -> copy(includeUnsafe = value.equals("true", ignoreCase = true))

        "duration_gte" -> copy(durationGte = value.toIntOrNull() ?: UNSET)

        "duration_lte" -> copy(durationLte = value.toIntOrNull() ?: UNSET)

        else -> CRITERIA_BY_PARAM[key]
            ?.let { copy(weights = weights + (it to (value.toIntOrNull() ?: UNSET))) }
            ?: this
    }

    companion object {
        private const val UNSET = -1
        private val CRITERIA_BY_PARAM = TournesolFilterCriterion.entries
            .associateBy { "weight_${it.apiKey}" }

        @JvmStatic
        fun parse(url: String, defaultIncludeUnsafe: Boolean): TournesolKioskQuery = url
            .substringAfter('?', "")
            .split('&')
            .map { it.split('=') }
            .filter { it.size == 2 }
            .fold(TournesolKioskQuery(includeUnsafe = defaultIncludeUnsafe)) { query, (key, value) ->
                query.withParam(key, decodeOrRaw(value))
            }

        private fun decodeOrRaw(value: String): String = try {
            URLDecoder.decode(value, Charsets.UTF_8.name())
        } catch (_: IllegalArgumentException) {
            value
        }

        private fun TournesolKioskExtractor.setWeight(criterion: TournesolFilterCriterion, weight: Int) {
            when (criterion) {
                TournesolFilterCriterion.LARGELY_RECOMMENDED -> setWeightLargelyRecommended(weight)
                TournesolFilterCriterion.RELIABILITY -> setWeightReliability(weight)
                TournesolFilterCriterion.IMPORTANCE -> setWeightImportance(weight)
                TournesolFilterCriterion.PEDAGOGY -> setWeightPedagogy(weight)
                TournesolFilterCriterion.LAYMAN_FRIENDLY -> setWeightLaymanFriendly(weight)
                TournesolFilterCriterion.ENTERTAINING_RELAXING -> setWeightEntertainingRelaxing(weight)
                TournesolFilterCriterion.ENGAGING -> setWeightEngaging(weight)
                TournesolFilterCriterion.DIVERSITY_INCLUSION -> setWeightDiversityInclusion(weight)
                TournesolFilterCriterion.BETTER_HABITS -> setWeightBetterHabits(weight)
                TournesolFilterCriterion.BACKFIRE_RISK -> setWeightBackfireRisk(weight)
            }
        }
    }
}
