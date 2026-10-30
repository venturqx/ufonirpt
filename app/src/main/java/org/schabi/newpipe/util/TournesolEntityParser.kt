package org.schabi.newpipe.util

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonParserException

data class CriterionScore(val criteria: String, val score: Double)

data class TournesolRating(
    val score: Double,
    val unsafeReasons: List<String>,
    val comparisonCount: Int,
    val contributorCount: Int,
    val criterionScores: List<CriterionScore>
)

class CriterionDistribution private constructor(
    val bins: IntArray,
    val distribution: IntArray
) {
    companion object {
        private const val MIN_BUCKETS = 2

        fun of(bins: IntArray, distribution: IntArray): CriterionDistribution? {
            val isDrawable = distribution.size >= MIN_BUCKETS && bins.size == distribution.size + 1
            return if (isDrawable) CriterionDistribution(bins, distribution) else null
        }
    }
}

object TournesolEntityParser {
    const val LARGELY_RECOMMENDED = "largely_recommended"

    fun parseRating(body: String): TournesolRating? {
        val rating = parseObject(body)?.get("collective_rating") as? JsonObject ?: return null
        val score = (rating["tournesol_score"] as? Number)?.toDouble()
            ?.takeIf { it.isFinite() }
            ?: return null
        return TournesolRating(
            score = score,
            unsafeReasons = parseUnsafeReasons(rating),
            comparisonCount = rating.getInt("n_comparisons", 0),
            contributorCount = rating.getInt("n_contributors", 0),
            criterionScores = parseCriterionScores(rating)
        )
    }

    fun parseDistributions(body: String): Map<String, CriterionDistribution>? {
        val entries = parseObject(body)?.get("criteria_scores_distributions") as? JsonArray
            ?: return null
        return entries.filterIsInstance<JsonObject>().mapNotNull(::parseDistribution).toMap()
    }

    private fun parseObject(body: String): JsonObject? = try {
        JsonParser.`object`().from(body)
    } catch (_: JsonParserException) {
        null
    } catch (_: IndexOutOfBoundsException) {
        null
    }

    private fun parseUnsafeReasons(rating: JsonObject): List<String> {
        val unsafe = rating.getObject("unsafe")
        if (!unsafe.getBoolean("status", false)) {
            return emptyList()
        }
        return unsafe.getArray("reasons").filterIsInstance<String>().filter { it.isNotBlank() }
    }

    private fun parseCriterionScores(rating: JsonObject): List<CriterionScore> = rating
        .getArray("criteria_scores")
        .filterIsInstance<JsonObject>()
        .map { CriterionScore(it.getString("criteria", ""), it.getDouble("score", 0.0)) }
        .filter { it.criteria != LARGELY_RECOMMENDED }

    private fun parseDistribution(entry: JsonObject): Pair<String, CriterionDistribution>? {
        val criteria = entry.getString("criteria")?.takeIf { it.isNotEmpty() } ?: return null
        val distribution = CriterionDistribution.of(
            bins = entry.getArray("bins").intValues(),
            distribution = entry.getArray("distribution").intValues()
        ) ?: return null
        return criteria to distribution
    }

    private fun JsonArray.intValues() = IntArray(size) { getInt(it) }
}
