package org.schabi.newpipe.tournesol

import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.schedulers.Schedulers
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

object TournesolEntityApi {
    private const val ENTITIES_URL = "https://api.tournesol.app/polls/videos/entities"
    private const val DISTRIBUTIONS_SEGMENT = "criteria_scores_distributions"
    private const val YOUTUBE_SERVICE_ID = 0
    private const val CACHE_SIZE = 32
    private val CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(5)

    private class CacheEntry(val value: Any, val storedAtMs: Long)

    private val cache = object : LinkedHashMap<HttpUrl, CacheEntry>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<HttpUrl, CacheEntry>) =
            size > CACHE_SIZE
    }

    internal var clock: () -> Long = System::currentTimeMillis

    @JvmStatic
    fun fetchRating(serviceId: Int, videoId: String?): Maybe<TournesolRating> = fetch(
        entityUrl(serviceId, videoId),
        TournesolEntityParser::parseRating
    )

    @JvmStatic
    fun fetchDistributions(
        serviceId: Int,
        videoId: String?
    ): Maybe<Map<String, CriterionDistribution>> = fetch(
        distributionsUrl(serviceId, videoId),
        TournesolEntityParser::parseDistributions
    )

    @JvmStatic
    fun cachedRating(serviceId: Int, videoId: String?): TournesolRating? =
        cached(entityUrl(serviceId, videoId))

    @JvmStatic
    fun cachedDistributions(serviceId: Int, videoId: String?): Map<String, CriterionDistribution>? =
        cached(distributionsUrl(serviceId, videoId))

    internal fun clearCache() = synchronized(cache) { cache.clear() }

    fun entityUrl(serviceId: Int, videoId: String?): HttpUrl? {
        if (serviceId != YOUTUBE_SERVICE_ID || videoId.isNullOrEmpty()) {
            return null
        }
        return ENTITIES_URL.toHttpUrl()
            .newBuilder()
            .addEncodedPathSegment(URLEncoder.encode("yt:$videoId", "UTF-8"))
            .build()
    }

    fun distributionsUrl(serviceId: Int, videoId: String?): HttpUrl? = entityUrl(serviceId, videoId)
        ?.newBuilder()
        ?.addPathSegment(DISTRIBUTIONS_SEGMENT)
        ?.build()

    private fun <T : Any> fetch(url: HttpUrl?, parse: (String) -> T?): Maybe<T> {
        if (url == null) {
            return Maybe.empty()
        }
        return Maybe.fromCallable<T> { fetchBody(url)?.let(parse)?.also { store(url, it) } }
            .subscribeOn(Schedulers.io())
    }

    private fun store(url: HttpUrl, value: Any) = synchronized(cache) {
        cache[url] = CacheEntry(value, clock())
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> cached(url: HttpUrl?): T? = synchronized(cache) {
        val entry = url?.let { cache[it] } ?: return null
        if (clock() - entry.storedAtMs > CACHE_TTL_MS) {
            cache.remove(url)
            return null
        }
        entry.value as T
    }

    private fun fetchBody(url: HttpUrl): String? {
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Accept", "application/json")
            .build()
        return checkNotNull(TournesolHttpClient.provider()).newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body.string() else null
        }
    }
}
