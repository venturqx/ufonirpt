package org.schabi.newpipe.tournesol.ui

import java.io.IOException
import java.time.OffsetDateTime
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.schabi.newpipe.database.history.model.StreamHistoryEntry
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.tournesol.CompareKey
import org.schabi.newpipe.tournesol.ComparePairSelection
import org.schabi.newpipe.tournesol.CompareScoreBackend

internal const val URL_A = "https://www.youtube.com/watch?v=aaaaaaaaaaa"
internal const val URL_B = "https://www.youtube.com/watch?v=bbbbbbbbbbb"
internal const val URL_C = "https://www.youtube.com/watch?v=ccccccccccc"
internal const val URL_D = "https://www.youtube.com/watch?v=ddddddddddd"
internal const val UID_A = "yt:aaaaaaaaaaa"
internal const val UID_B = "yt:bbbbbbbbbbb"
internal const val UID_C = "yt:ccccccccccc"
internal const val UID_D = "yt:ddddddddddd"
internal const val UNSUPPORTED_SERVICE = 99

internal const val SAVE = "POST /users/me/comparisons/videos"
internal const val PATCH_AB = "PATCH /users/me/comparisons/videos/$UID_A/$UID_B/"
internal const val CHECK_AB = "GET /users/me/comparisons/videos/$UID_B/$UID_A/"
internal const val CHECK_CD = "GET /users/me/comparisons/videos/$UID_D/$UID_C/"
internal const val COMPARISONS = "GET /users/me/comparisons/videos"
internal const val SUGGESTIONS = "GET /users/me/suggestions/videos/tocompare/"
internal const val STATS = "GET /stats/"
internal const val TOKEN_ENDPOINT = "POST /o/token/"

internal val PAIR_AB = ComparePairSelection(0, URL_A, 0, URL_B)
internal val PAIR_CD = ComparePairSelection(0, URL_C, 0, URL_D)
internal val UNSUPPORTED_PAIR = ComparePairSelection(UNSUPPORTED_SERVICE, "left", UNSUPPORTED_SERVICE, "right")
internal val KEY_AB = CompareKey(UID_A, UID_B)
internal val KEY_CD = CompareKey(UID_C, UID_D)

internal fun message(res: Int, arg: String? = null) = if (arg == null) "$res" else "$res:$arg"

internal fun shortToast(text: String) = "$text|short"

internal fun longToast(text: String) = "$text|long"

internal fun streamEntity(url: String, serviceId: Int = 0) = StreamEntity(
    serviceId = serviceId,
    url = url,
    title = "title",
    streamType = StreamType.VIDEO_STREAM,
    duration = 0,
    uploader = "uploader"
)

internal fun historyEntry(
    url: String,
    streamId: Long = 0,
    accessDate: OffsetDateTime = OffsetDateTime.MIN,
    serviceId: Int = 0
) = StreamHistoryEntry(
    streamEntity = streamEntity(url, serviceId),
    streamId = streamId,
    accessDate = accessDate,
    repeatCount = 0
)

internal fun videoRef(url: String) = TournesolVideoRef(
    videoId = url.substringAfterLast('='),
    originalUrl = url,
    stream = streamEntity(url)
)

internal class InMemoryScoreBackend : CompareScoreBackend {
    private var submitted = emptySet<String>()
    private var scores: String? = null

    override fun readSubmitted() = submitted

    override fun writeSubmitted(pairs: Set<String>) {
        submitted = pairs
    }

    override fun readScores() = scores

    override fun writeScores(json: String) {
        scores = json
    }
}

internal class FakeTournesolServer : Interceptor {
    private class Reply(val code: Int, val body: String, val failure: IOException? = null)

    private val replies = mutableMapOf<String, Reply>()
    private var lastUrl: HttpUrl? = null
    val calls = mutableListOf<String>()

    fun lastQuery(name: String) = lastUrl?.queryParameter(name)

    fun reply(route: String, code: Int, body: String = "{}") {
        replies[route] = Reply(code, body)
    }

    fun failNetwork(route: String, message: String = "") {
        replies[route] = Reply(code = 0, body = "", failure = IOException(message))
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val route = "${request.method} ${request.url.encodedPath}"
        calls += route
        lastUrl = request.url
        val reply = replies[route] ?: defaultReply(request.method)
        reply.failure?.let { throw it }
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(reply.code)
            .message("fake")
            .body(reply.body.toResponseBody())
            .build()
    }

    private fun defaultReply(method: String) = when (method) {
        "POST" -> Reply(201, "{}")
        "PATCH" -> Reply(200, "{}")
        else -> Reply(404, "{}")
    }
}
