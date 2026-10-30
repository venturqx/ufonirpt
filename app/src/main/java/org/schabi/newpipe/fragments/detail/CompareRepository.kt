package org.schabi.newpipe.fragments.detail

import com.grack.nanojson.JsonStringWriter
import com.grack.nanojson.JsonWriter
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import java.io.IOException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.DownloaderImpl
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.linkhandler.LinkHandlerFactory

enum class SubmitOutcome { CREATED, ALREADY_EXISTS }

enum class SaveResult { CREATED, UPDATED }

object CompareRepository {
    fun buildTournesolUid(url: String, serviceId: Int): String? {
        if (!TournesolUid.supports(serviceId)) {
            return null
        }
        return try {
            val service = NewPipe.getService(serviceId)
            val factory: LinkHandlerFactory = service.getStreamLHFactory()
            val id = factory.getId(url)
            TournesolUid.of(serviceId, id)?.value
        } catch (_: ExtractionException) {
            null
        }
    }

    fun submitComparison(
        token: String,
        entityA: String,
        entityB: String,
        score: Int
    ): Single<SaveResult> = submitOrUpdateComparison(
        token = token,
        entityA = entityA,
        entityB = entityB,
        criteriaScores = listOf(CriteriaScore(CompareJsonParser.COMPARE_CRITERIA, score))
    )

    internal fun submitOrUpdateComparison(
        token: String,
        entityA: String,
        entityB: String,
        criteriaScores: List<CriteriaScore>
    ): Single<SaveResult> = submitComparisonWithCriteria(
        token,
        entityA,
        entityB,
        criteriaScores
    ).flatMap { outcome ->
        when (outcome) {
            SubmitOutcome.CREATED -> Single.just(SaveResult.CREATED)

            SubmitOutcome.ALREADY_EXISTS -> patchComparison(
                token,
                entityA,
                entityB,
                criteriaScores
            ).map { SaveResult.UPDATED }
        }
    }

    internal fun submitComparisonWithCriteria(
        token: String,
        entityA: String,
        entityB: String,
        criteriaScores: List<CriteriaScore>
    ): Single<SubmitOutcome> = Single.fromCallable {
        val payload = buildComparisonPayloadJson(entityA, entityB, criteriaScores)
        val response = send(comparisonsUrl(), token) { post(payload.toJsonBody()) }
        resolveSubmitOutcome(response.code, response.body)
    }.subscribeOn(Schedulers.io())

    internal fun resolveSubmitOutcome(httpCode: Int, responseBody: String): SubmitOutcome {
        if (httpCode in 200..299) {
            return SubmitOutcome.CREATED
        }
        if (httpCode == 400 && responseBody.contains("already compared")) {
            return SubmitOutcome.ALREADY_EXISTS
        }
        throw httpError(httpCode, responseBody)
    }

    fun checkComparison(
        token: String,
        uidA: String,
        uidB: String
    ): Single<Boolean> = Single.fromCallable {
        val response = send(comparisonsUrl(uidA, uidB, TRAILING_SLASH), token)
        when (response.code) {
            200 -> true
            404 -> false
            else -> throw httpError(response.code, response.body)
        }
    }.subscribeOn(Schedulers.io())

    internal fun patchComparison(
        token: String,
        entityA: String,
        entityB: String,
        criteriaScores: List<CriteriaScore>
    ): Single<Int> = Single.fromCallable {
        val payload = buildPatchPayloadJson(criteriaScores)
        val url = comparisonsUrl(entityA, entityB, TRAILING_SLASH)
        send(url, token) { patch(payload.toJsonBody()) }.bodyOrThrow()
        R.string.compare_more_submitted
    }.subscribeOn(Schedulers.io())

    fun fetchUserComparisons(
        token: String,
        username: String = "me",
        limit: Int = 20
    ): Single<CompareComparisonsResult> = Single.fromCallable {
        val url = apiUrl("users", username, "comparisons", COMPARE_POLL)
            .addQueryParameter("limit", limit.coerceAtLeast(1).toString())
            .build()
        CompareJsonParser.parseComparisons(send(url, token).bodyOrThrow())
    }.subscribeOn(Schedulers.io())

    internal fun buildComparisonPayloadJson(
        entityA: String,
        entityB: String,
        criteriaScores: List<CriteriaScore>
    ): String = JsonWriter.string()
        .`object`()
        .value("pollName", COMPARE_POLL)
        .`object`("entity_a").value("uid", entityA).end()
        .`object`("entity_b").value("uid", entityB).end()
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

    fun fetchWeeklyComparisons(): Single<Int> = Single.fromCallable {
        val url = apiUrl("stats", TRAILING_SLASH)
            .addQueryParameter("poll", COMPARE_POLL)
            .build()
        CompareJsonParser.parseWeeklyComparisons(send(url).bodyOrThrow())
    }.subscribeOn(Schedulers.io())

    fun fetchSuggestedVideos(
        token: String
    ): Single<List<CompareComparisonVideo>> = Single.fromCallable {
        val url = apiUrl("users", "me", "suggestions", COMPARE_POLL, "tocompare", TRAILING_SLASH)
            .build()
        CompareJsonParser.parseSuggestedVideos(send(url, token).bodyOrThrow())
    }.subscribeOn(Schedulers.io())

    fun fetchEntityToCompare(
        token: String,
        firstEntityUid: String
    ): Maybe<CompareComparisonVideo> = Maybe.fromCallable {
        val url = apiUrl("users", "me", "entities_to_compare", COMPARE_POLL, TRAILING_SLASH)
            .addQueryParameter("first_entity_uid", firstEntityUid)
            .addQueryParameter("limit", "1")
            .build()
        CompareJsonParser.parseSuggestedVideos(send(url, token).bodyOrThrow()).firstOrNull()
    }.subscribeOn(Schedulers.io())

    private class ApiResponse(val code: Int, val body: String) {
        fun bodyOrThrow(): String {
            if (code !in 200..299) {
                throw httpError(code, body)
            }
            return body
        }
    }

    private fun send(
        url: HttpUrl,
        token: String? = null,
        configure: Request.Builder.() -> Unit = {}
    ): ApiResponse {
        val request = Request.Builder()
            .url(url)
            .apply { token?.let { addHeader("Authorization", "Bearer $it") } }
            .apply(configure)
            .build()
        return getHttpClient().newCall(request).execute().use { response ->
            ApiResponse(response.code, response.body?.string().orEmpty())
        }
    }

    private fun httpError(code: Int, body: String) = IOException("HTTP $code $body")

    private fun apiUrl(vararg pathSegments: String): HttpUrl.Builder = BASE_URL.toHttpUrl()
        .newBuilder()
        .apply { pathSegments.forEach { addPathSegment(it) } }

    private fun comparisonsUrl(vararg pathSuffix: String): HttpUrl = apiUrl("users", "me", "comparisons", COMPARE_POLL, *pathSuffix).build()

    private fun String.toJsonBody(): RequestBody = toRequestBody(JSON_MEDIA_TYPE)

    private fun getHttpClient(): OkHttpClient = DownloaderImpl.getInstance()?.getClient() ?: fallbackClient

    private const val BASE_URL = "https://api.tournesol.app"
    private const val COMPARE_POLL = "videos"
    private const val TRAILING_SLASH = ""
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    private val fallbackClient by lazy { OkHttpClient.Builder().build() }
}
