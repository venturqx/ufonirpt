package org.schabi.newpipe.util

import java.net.URLDecoder
import java.net.URLEncoder
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

    fun toUrl(): String = buildList<String> {
        languages?.let { codes -> add("languages=" + codes.joinToString(",") { encode(it) }) }
        dateGte?.let { add("date_gte=" + encodeKeepingColons(it)) }
        uploader?.let { add("uploader=" + encode(it)) }
        if (includeUnsafe) add("unsafe=true")
        if (durationGte >= 0) add("duration_gte=$durationGte")
        if (durationLte >= 0) add("duration_lte=$durationLte")
        for ((param, criterion) in CRITERIA_BY_PARAM) {
            val weight = weightFor(criterion)
            if (weight >= 0) add("$param=$weight")
        }
    }.joinToString("&", prefix = "${TournesolHelper.KIOSK_ID}?")

    private fun withParam(key: String, rawValue: String): TournesolKioskQuery {
        val value = decodeOrRaw(rawValue)
        return when (key) {
            "languages" -> copy(
                languages = rawValue.split(",").dropLastWhile { it.isEmpty() }.map { decodeOrRaw(it) }
            )

            "date_gte" -> copy(dateGte = value)

            "uploader" -> copy(uploader = value)

            "unsafe" -> copy(includeUnsafe = value.equals("true", ignoreCase = true))

            "duration_gte" -> copy(durationGte = value.toIntOrNull() ?: UNSET)

            "duration_lte" -> copy(durationLte = value.toIntOrNull() ?: UNSET)

            else -> CRITERIA_BY_PARAM[key]
                ?.let { copy(weights = weights + (it to (value.toIntOrNull() ?: UNSET))) }
                ?: this
        }
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
                query.withParam(key, value)
            }

        @JvmStatic
        fun forUploader(uploader: String): TournesolKioskQuery = TournesolKioskQuery(
            includeUnsafe = true,
            languages = emptyList(),
            uploader = uploader
        )

        private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

        private fun encodeKeepingColons(value: String): String = encode(value).replace("%3A", ":")

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
