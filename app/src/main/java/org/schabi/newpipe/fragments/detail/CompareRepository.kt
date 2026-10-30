package org.schabi.newpipe.fragments.detail

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonStringWriter
import com.grack.nanojson.JsonWriter
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import java.io.IOException
import java.net.URLEncoder
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.DownloaderImpl
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.linkhandler.LinkHandlerFactory

object CompareRepository {
    fun buildTournesolUid(url: String, serviceId: Int): String? {
        val prefix = getTournesolServicePrefix(serviceId) ?: return null
        return try {
            val service = NewPipe.getService(serviceId)
            val factory: LinkHandlerFactory = service.getStreamLHFactory()
            val id = factory.getId(url)
            "$prefix:$id"
        } catch (_: ExtractionException) {
            null
        }
    }

    fun submitComparison(
        token: String,
        lastUid: String,
        currentUid: String,
        score: Int
    ): Single<Int> = submitComparisonWithCriteria(
        token = token,
        lastUid = lastUid,
        currentUid = currentUid,
        criteriaScores = listOf(CriteriaScore(COMPARE_CRITERIA, score))
    )

    internal fun submitComparisonWithCriteria(
        token: String,
        lastUid: String,
        currentUid: String,
        criteriaScores: List<CriteriaScore>
    ): Single<Int> = Single.fromCallable {
        val payload = buildComparisonPayloadJson(lastUid, currentUid, criteriaScores)
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = payload.toRequestBody(mediaType)
        val request = Request.Builder()
            .url(COMPARE_URL)
            .post(body)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .build()

        val client = getHttpClient()
        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            return@fromCallable resolveSubmitStatusRes(response.code, responseBody)
        }
    }.subscribeOn(Schedulers.io())

    internal fun resolveSubmitStatusRes(httpCode: Int, responseBody: String): Int {
        if (httpCode in 200..299) {
            return R.string.compare_submitted
        }
        if (httpCode == 400 && responseBody.contains("already compared")) {
            return R.string.compare_already_submitted
        }
        throw IOException("HTTP $httpCode $responseBody")
    }

    fun checkComparison(
        token: String,
        uidA: String,
        uidB: String
    ): Single<Boolean> = Single.fromCallable {
        val url = "$COMPARE_URL/$uidA/$uidB/"
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Authorization", "Bearer $token")
            .build()

        val client = getHttpClient()
        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            return@fromCallable when (response.code) {
                200 -> true
                404 -> false
                else -> throw IOException("HTTP ${response.code} $responseBody")
            }
        }
    }.subscribeOn(Schedulers.io())

    internal fun patchComparison(
        token: String,
        lastUid: String,
        currentUid: String,
        criteriaScores: List<CriteriaScore>
    ): Single<Int> = Single.fromCallable {
        val payload = buildPatchPayloadJson(criteriaScores)
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = payload.toRequestBody(mediaType)
        val request = Request.Builder()
            .url("$COMPARE_URL/$lastUid/$currentUid/")
            .patch(body)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .build()

        val client = getHttpClient()
        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (response.isSuccessful) {
                return@fromCallable R.string.compare_more_submitted
            }
            throw IOException("HTTP ${response.code} $responseBody")
        }
    }.subscribeOn(Schedulers.io())

    fun fetchUserComparisons(
        token: String,
        username: String = "me",
        limit: Int = 20
    ): Single<CompareComparisonsResult> = Single.fromCallable {
        val encodedUsername = if (username == "me") {
            "me"
        } else {
            URLEncoder.encode(username, Charsets.UTF_8.name())
        }
        val safeLimit = limit.coerceAtLeast(1)
        val url = "$BASE_URL/users/$encodedUsername/comparisons/videos?limit=$safeLimit"
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Authorization", "Bearer $token")
            .build()

        val client = getHttpClient()
        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} $responseBody")
            }
            return@fromCallable parseComparisons(responseBody)
        }
    }.subscribeOn(Schedulers.io())

    internal fun getTournesolServicePrefix(serviceId: Int): String? {
        return when (serviceId) {
            ServiceList.YouTube.serviceId -> "yt"
            ServiceList.SoundCloud.serviceId -> "sc"
            ServiceList.PeerTube.serviceId -> "peertube"
            else -> null
        }
    }

    internal fun buildComparisonPayloadJson(
        lastUid: String,
        currentUid: String,
        criteriaScores: List<CriteriaScore>
    ): String = JsonWriter.string()
        .`object`()
        .value("pollName", COMPARE_POLL)
        .`object`("entity_a").value("uid", lastUid).end()
        .`object`("entity_b").value("uid", currentUid).end()
        .writeCriteriaScores(criteriaScores)
        .end()
        .done()

    internal fun buildPatchPayloadJson(
        criteriaScores: List<CriteriaScore>
    ): String = JsonWriter.string()
        .`object`()
        .writeCriteriaScores(criteriaScores)
        .end()
        .done()

    private fun JsonStringWriter.writeCriteriaScores(
        criteriaScores: List<CriteriaScore>
    ): JsonStringWriter {
        array("criteria_scores")
        criteriaScores.forEach { scoreItem ->
            `object`()
                .value("criteria", scoreItem.criteria)
                .value("score", scoreItem.score)
                .value("score_max", SCORE_MAX)
                .end()
        }
        return end()
    }

    internal fun parseComparisons(responseBody: String): CompareComparisonsResult {
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

    internal fun collectObjectCandidates(
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
        val videoId = extractYouTubeId(uid) ?: return null
        return "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
    }

    private fun buildFallbackVideoUrl(uid: String): String? {
        val videoId = extractYouTubeId(uid) ?: return null
        return "https://www.youtube.com/watch?v=$videoId"
    }

    private fun extractYouTubeId(uid: String): String? {
        if (!uid.startsWith("yt:")) {
            return null
        }
        val videoId = uid.removePrefix("yt:").trim()
        return videoId.takeIf { it.isNotEmpty() }
    }

    private fun JsonObject.optNullableInt(name: String): Int? {
        val raw = this[name]
        return (raw as? Number)?.toInt()
    }

    fun fetchWeeklyComparisons(): Single<Int> = Single.fromCallable {
        val url = "$BASE_URL/stats/?poll=$COMPARE_POLL"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val client = getHttpClient()
        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} $responseBody")
            }
            parseWeeklyComparisons(responseBody)
        }
    }.subscribeOn(Schedulers.io())

    internal fun parseWeeklyComparisons(responseBody: String): Int {
        val json = JsonParser.`object`().from(responseBody)
        val polls = json.getArray("polls")
        if (polls != null && polls.size > 0) {
            val poll = polls.getObject(0)
            val comparisons = poll?.getObject("comparisons")
            return comparisons?.getInt("added_current_week", 0) ?: 0
        }
        return 0
    }

    private fun getHttpClient(): OkHttpClient {
        return DownloaderImpl.getInstance()?.getClient() ?: OkHttpClient.Builder().build()
    }

    fun fetchSuggestedVideos(
        token: String
    ): Single<List<CompareComparisonVideo>> = Single.fromCallable {
        val url = "$BASE_URL/users/me/suggestions/videos/tocompare/"
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Authorization", "Bearer $token")
            .build()

        val client = getHttpClient()
        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} $responseBody")
            }
            return@fromCallable parseSuggestedVideos(responseBody)
        }
    }.subscribeOn(Schedulers.io())

    fun fetchEntityToCompare(
        token: String,
        firstEntityUid: String
    ): Maybe<CompareComparisonVideo> = Maybe.fromCallable {
        val encoded = URLEncoder.encode(firstEntityUid, Charsets.UTF_8.name())
        val url = "$BASE_URL/users/me/entities_to_compare/videos/" +
            "?first_entity_uid=$encoded&limit=1"
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Authorization", "Bearer $token")
            .build()

        val client = getHttpClient()
        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} $responseBody")
            }
            val videos = parseSuggestedVideos(responseBody)
            return@fromCallable videos.firstOrNull()
        }
    }.subscribeOn(Schedulers.io())

    internal fun parseSuggestedVideos(responseBody: String): List<CompareComparisonVideo> {
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

    fun uidToServiceId(uid: String): Int {
        return when {
            uid.startsWith("yt:") -> ServiceList.YouTube.serviceId
            uid.startsWith("sc:") -> ServiceList.SoundCloud.serviceId
            uid.startsWith("peertube:") -> ServiceList.PeerTube.serviceId
            else -> ServiceList.YouTube.serviceId
        }
    }

    fun uidToVideoUrl(uid: String): String? {
        return buildFallbackVideoUrl(uid)
    }

    private const val BASE_URL = "https://api.tournesol.app"
    private const val COMPARE_URL = "$BASE_URL/users/me/comparisons/videos"
    private const val COMPARE_POLL = "videos"
    private const val COMPARE_CRITERIA = "largely_recommended"
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
}
