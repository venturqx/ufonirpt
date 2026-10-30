package org.schabi.newpipe.fragments.detail

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser

internal object CompareJsonParser {
    const val COMPARE_CRITERIA = "largely_recommended"

    private val TITLE_KEYS = listOf("title", "name")
    private val UPLOADER_KEYS = listOf("uploader", "uploader_name", "channel_name", "author")
    private val URL_KEYS = listOf("url", "video_url", "link")
    private val THUMBNAIL_KEYS = listOf(
        "thumbnail_url",
        "thumbnail",
        "thumbnailUrl",
        "preview_url",
        "image",
        "images",
        "thumbnails"
    )

    fun parseComparisons(responseBody: String): CompareComparisonsResult {
        val root = JsonParser.`object`().from(responseBody)
        val totalCount = root.getInt("count", -1).takeIf { it >= 0 }
        val results = root.getArray("results") ?: JsonArray()
        val parsed = ArrayList<CompareRecommendationItem>(results.size)
        for (index in 0 until results.size) {
            val result = results[index] as? JsonObject ?: continue
            parsed.add(parseComparisonItem(result, index))
        }
        return CompareComparisonsResult(totalCount = totalCount, comparisons = parsed)
    }

    fun parseWeeklyComparisons(responseBody: String): Int {
        val json = JsonParser.`object`().from(responseBody)
        val polls = json.getArray("polls")
        if (polls != null && polls.size > 0) {
            val poll = polls.getObject(0)
            val comparisons = poll?.getObject("comparisons")
            return comparisons?.getInt("added_current_week", 0) ?: 0
        }
        return 0
    }

    fun parseSuggestedVideos(responseBody: String): List<CompareComparisonVideo> {
        val trimmed = responseBody.trim()
        val results = if (trimmed.startsWith("[")) {
            JsonParser.array().from(trimmed)
        } else {
            val root = JsonParser.`object`().from(trimmed)
            root.getArray("results") ?: JsonArray()
        }
        val parsed = ArrayList<CompareComparisonVideo>(results.size)
        for (index in 0 until results.size) {
            val item = results[index] as? JsonObject ?: continue
            val entity = item["entity"] as? JsonObject ?: item
            val uid = entity.getString("uid")?.takeIf { it.isNotBlank() } ?: continue
            parsed.add(
                parseComparisonVideo(
                    entity = entity,
                    contexts = item.getArray("entity_contexts"),
                    fallbackUid = uid
                )
            )
        }
        return parsed
    }

    fun collectObjectCandidates(
        value: Any?,
        candidates: MutableList<JsonObject>,
        depth: Int = 0
    ) {
        if (value == null || depth > 5) {
            return
        }
        when (value) {
            is JsonObject -> {
                (value["metadata"] as? JsonObject)?.let { metadata ->
                    collectObjectCandidates(metadata, candidates, depth + 1)
                }
                candidates.add(value)
                for ((key, child) in value.entries) {
                    if (key != "metadata") {
                        collectObjectCandidates(child, candidates, depth + 1)
                    }
                }
            }

            is JsonArray -> {
                for (index in 0 until value.size) {
                    collectObjectCandidates(value[index], candidates, depth + 1)
                }
            }
        }
    }

    fun buildFallbackVideoUrl(uid: String): String? {
        val videoId = TournesolUid.parse(uid)?.youTubeId ?: return null
        return "https://www.youtube.com/watch?v=$videoId"
    }

    private fun parseComparisonItem(
        result: JsonObject,
        index: Int
    ): CompareRecommendationItem {
        val entityA = result.getObject("entity_a")
        val entityB = result.getObject("entity_b")
        val videoA = parseComparisonVideo(
            entity = entityA,
            contexts = result.getArray("entity_a_contexts"),
            fallbackUid = "entity_a_$index"
        )
        val videoB = parseComparisonVideo(
            entity = entityB,
            contexts = result.getArray("entity_b_contexts"),
            fallbackUid = "entity_b_$index"
        )

        val scoreData = findCriteriaScore(
            result.getArray("criteria_scores"),
            COMPARE_CRITERIA
        )
        val score = scoreData?.optNullableInt("score")
        val scoreMax = scoreData?.optNullableInt("score_max")
        val uid = "${videoA.uid}|${videoB.uid}|$index"

        return CompareRecommendationItem(
            uid = uid,
            videoA = videoA,
            videoB = videoB,
            largelyRecommendedScore = score,
            scoreMax = scoreMax
        )
    }

    private fun parseComparisonVideo(
        entity: JsonObject?,
        contexts: JsonArray?,
        fallbackUid: String
    ): CompareComparisonVideo {
        val metadataCandidates = ArrayList<JsonObject>()
        metadataCandidates.addAll(extractEntityMetadataCandidates(entity))
        metadataCandidates.addAll(extractContextMetadataCandidates(contexts))

        val uid = entity?.getString("uid").orEmpty().takeIf { it.isNotBlank() } ?: fallbackUid
        val title = findFirstText(metadataCandidates, TITLE_KEYS) ?: uid
        val uploader = findFirstText(metadataCandidates, UPLOADER_KEYS).orEmpty()
        val thumbnailUrl = findFirstThumbnailUrl(metadataCandidates) ?: buildFallbackThumbnailUrl(uid)
        val videoUrl = findFirstText(metadataCandidates, URL_KEYS)?.let(::normalizeUrl)
            ?: buildFallbackVideoUrl(uid)

        return CompareComparisonVideo(
            uid = uid,
            title = title,
            uploader = uploader,
            thumbnailUrl = thumbnailUrl,
            videoUrl = videoUrl
        )
    }

    private fun extractEntityMetadataCandidates(entity: JsonObject?): List<JsonObject> {
        if (entity == null) {
            return emptyList()
        }
        val candidates = ArrayList<JsonObject>(8)
        collectObjectCandidates(entity, candidates)
        return candidates
    }

    private fun extractContextMetadataCandidates(contexts: JsonArray?): List<JsonObject> {
        if (contexts == null) {
            return emptyList()
        }
        val candidates = ArrayList<JsonObject>(contexts.size * 8)
        for (index in 0 until contexts.size) {
            collectObjectCandidates(contexts[index], candidates)
        }
        return candidates
    }

    private fun findFirstText(candidates: List<JsonObject>, keys: List<String>): String? {
        for (candidate in candidates) {
            extractText(candidate, keys)?.let { return it }
        }
        return null
    }

    private fun findFirstThumbnailUrl(candidates: List<JsonObject>): String? {
        for (candidate in candidates) {
            extractThumbnailUrl(candidate)?.let { return it }
        }
        return null
    }

    private fun findCriteriaScore(criteriaScores: JsonArray?, criteria: String): JsonObject? {
        if (criteriaScores == null) {
            return null
        }
        for (index in 0 until criteriaScores.size) {
            val item = criteriaScores.getObject(index) ?: continue
            if (item.getString("criteria") == criteria) {
                return item
            }
        }
        return null
    }

    private fun extractText(metadata: JsonObject?, keys: List<String>): String? {
        if (metadata == null) {
            return null
        }
        keys.forEach { key ->
            val raw = metadata[key]
            val value = (raw as? String)?.trim().orEmpty()
            if (value.isNotEmpty() && value != "null") {
                return value
            }
        }
        return null
    }

    private fun extractThumbnailUrl(metadata: JsonObject?): String? {
        if (metadata == null) {
            return null
        }
        for (key in THUMBNAIL_KEYS) {
            val raw = metadata[key]
            when (raw) {
                is String -> {
                    val normalized = raw.trim()
                    if (normalized.isNotEmpty() && normalized != "null") {
                        return normalizeUrl(normalized)
                    }
                }

                is JsonObject -> {
                    extractText(raw, URL_KEYS)?.let { return normalizeUrl(it) }
                }

                is JsonArray -> {
                    for (index in 0 until raw.size) {
                        when (val entry = raw[index]) {
                            is String -> {
                                val normalized = entry.trim()
                                if (normalized.isNotEmpty() && normalized != "null") {
                                    return normalizeUrl(normalized)
                                }
                            }

                            is JsonObject -> {
                                extractText(entry, URL_KEYS)?.let { return normalizeUrl(it) }
                            }
                        }
                    }
                }
            }
        }
        return null
    }

    private fun normalizeUrl(value: String): String {
        val normalized = value.trim()
        return if (normalized.startsWith("//")) {
            "https:$normalized"
        } else {
            normalized
        }
    }

    private fun buildFallbackThumbnailUrl(uid: String): String? {
        val videoId = TournesolUid.parse(uid)?.youTubeId ?: return null
        return "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
    }

    private fun JsonObject.optNullableInt(name: String): Int? {
        val raw = this[name]
        return (raw as? Number)?.toInt()
    }
}
