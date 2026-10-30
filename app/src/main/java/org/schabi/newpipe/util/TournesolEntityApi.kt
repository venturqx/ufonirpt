package org.schabi.newpipe.util

import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.schedulers.Schedulers
import java.net.URLEncoder
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.schabi.newpipe.DownloaderImpl

object TournesolEntityApi {
    private const val ENTITIES_URL = "https://api.tournesol.app/polls/videos/entities"
    private const val DISTRIBUTIONS_SEGMENT = "criteria_scores_distributions"
    private const val YOUTUBE_SERVICE_ID = 0

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
        return Maybe.fromCallable<T> { fetchBody(url)?.let(parse) }
            .subscribeOn(Schedulers.io())
    }

    private fun fetchBody(url: HttpUrl): String? {
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Accept", "application/json")
            .build()
        return DownloaderImpl.getInstance().getClient().newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body.string() else null
        }
    }
}
