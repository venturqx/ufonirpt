package org.schabi.newpipe.tournesol

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TournesolEntityApiTest {

    private class FakeApi(private val code: Int, private val body: String) : Interceptor {
        @Volatile
        var request: Request? = null

        override fun intercept(chain: Interceptor.Chain): Response {
            request = chain.request()
            return Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("fake")
                .body(body.toResponseBody())
                .build()
        }
    }

    private fun serve(code: Int = 200, body: String = "{}"): FakeApi = FakeApi(code, body).also {
        val client = OkHttpClient.Builder().addInterceptor(it).build()
        TournesolHttpClient.provider = { client }
    }

    @After
    fun restoreHttpClient() {
        TournesolHttpClient.provider = { null }
        TournesolEntityApi.clearCache()
        TournesolEntityApi.clock = System::currentTimeMillis
    }

    @Test
    fun `urls encode the colon of the yt uid`() {
        assertEquals(
            "https://api.tournesol.app/polls/videos/entities/yt%3AdQw4w9WgXcQ",
            TournesolEntityApi.entityUrl(YOUTUBE, "dQw4w9WgXcQ").toString()
        )
        assertEquals(
            "https://api.tournesol.app/polls/videos/entities/yt%3AdQw4w9WgXcQ" +
                "/criteria_scores_distributions",
            TournesolEntityApi.distributionsUrl(YOUTUBE, "dQw4w9WgXcQ").toString()
        )
    }

    @Test
    fun `urls encode reserved characters of the video id`() {
        assertEquals(
            "https://api.tournesol.app/polls/videos/entities/yt%3Aa%2Fb%3Fc",
            TournesolEntityApi.entityUrl(YOUTUBE, "a/b?c").toString()
        )
    }

    @Test
    fun `urls need a youtube service and a video id`() {
        assertNull(TournesolEntityApi.entityUrl(SOUNDCLOUD, "abc"))
        assertNull(TournesolEntityApi.entityUrl(YOUTUBE, null))
        assertNull(TournesolEntityApi.entityUrl(YOUTUBE, ""))
        assertNull(TournesolEntityApi.distributionsUrl(SOUNDCLOUD, "abc"))
    }

    @Test
    fun `fetchRating asks for json and returns the parsed rating`() {
        val api = serve(body = """{"collective_rating":{"tournesol_score":12.5}}""")

        val rating = TournesolEntityApi.fetchRating(YOUTUBE, "abc").blockingGet()!!

        assertEquals(12.5, rating.score, 0.0)
        val request = api.request!!
        assertEquals("GET", request.method)
        assertEquals("application/json", request.header("Accept"))
        assertEquals(
            "https://api.tournesol.app/polls/videos/entities/yt%3Aabc",
            request.url.toString()
        )
    }

    @Test
    fun `fetchDistributions returns the parsed distributions`() {
        val api = serve(
            body = """
            {"criteria_scores_distributions":[
                {"criteria":"largely_recommended","bins":[0,1,2],"distribution":[3,4]}
            ]}
            """.trimIndent()
        )

        val distributions = TournesolEntityApi.fetchDistributions(YOUTUBE, "abc").blockingGet()!!

        assertArrayEquals(
            intArrayOf(3, 4),
            distributions.getValue(TournesolEntityParser.LARGELY_RECOMMENDED).distribution
        )
        assertEquals(
            "https://api.tournesol.app/polls/videos/entities/yt%3Aabc" +
                "/criteria_scores_distributions",
            api.request!!.url.toString()
        )
    }

    @Test
    fun `an unsupported stream completes empty without a request`() {
        val api = serve()

        assertNull(TournesolEntityApi.fetchRating(SOUNDCLOUD, "abc").blockingGet())
        assertNull(TournesolEntityApi.fetchDistributions(YOUTUBE, "").blockingGet())
        assertNull(api.request)
    }

    @Test
    fun `an http failure or an unusable body completes empty`() {
        serve(code = 404, body = """{"collective_rating":{"tournesol_score":12.5}}""")
        assertNull(TournesolEntityApi.fetchRating(YOUTUBE, "abc").blockingGet())

        serve(body = "<html>")
        assertNull(TournesolEntityApi.fetchRating(YOUTUBE, "abc").blockingGet())
        assertNull(TournesolEntityApi.fetchDistributions(YOUTUBE, "abc").blockingGet())
    }

    @Test
    fun `a network failure is reported as an error`() {
        val client = OkHttpClient.Builder().addInterceptor(Interceptor { throw IOException("down") }).build()
        TournesolHttpClient.provider = { client }

        TournesolEntityApi.fetchRating(YOUTUBE, "abc").test()
            .awaitDone(5, TimeUnit.SECONDS)
            .assertError(IOException::class.java)
    }

    @Test
    fun `a missing client is reported as an error`() {
        TournesolEntityApi.fetchRating(YOUTUBE, "abc").test()
            .awaitDone(5, TimeUnit.SECONDS)
            .assertError(IllegalStateException::class.java)
    }

    @Test
    fun `a fetched rating and distributions are served from the cache`() {
        serve(
            body = """
            {"collective_rating":{"tournesol_score":12.5},
             "criteria_scores_distributions":[
                {"criteria":"largely_recommended","bins":[0,1,2],"distribution":[3,4]}
            ]}
            """.trimIndent()
        )
        assertNull(TournesolEntityApi.cachedRating(YOUTUBE, "abc"))

        TournesolEntityApi.fetchRating(YOUTUBE, "abc").blockingGet()
        TournesolEntityApi.fetchDistributions(YOUTUBE, "abc").blockingGet()

        assertEquals(12.5, TournesolEntityApi.cachedRating(YOUTUBE, "abc")!!.score, 0.0)
        assertArrayEquals(
            intArrayOf(3, 4),
            TournesolEntityApi.cachedDistributions(YOUTUBE, "abc")!!
                .getValue(TournesolEntityParser.LARGELY_RECOMMENDED).distribution
        )
        assertNull(TournesolEntityApi.cachedRating(YOUTUBE, "other"))
    }

    @Test
    fun `a cached rating expires after five minutes`() {
        var now = 0L
        TournesolEntityApi.clock = { now }
        serve(body = """{"collective_rating":{"tournesol_score":12.5}}""")
        TournesolEntityApi.fetchRating(YOUTUBE, "abc").blockingGet()

        now = TimeUnit.MINUTES.toMillis(5)
        assertEquals(12.5, TournesolEntityApi.cachedRating(YOUTUBE, "abc")!!.score, 0.0)

        now += 1
        assertNull(TournesolEntityApi.cachedRating(YOUTUBE, "abc"))
    }

    @Test
    fun `an empty or failed fetch is not cached`() {
        serve(code = 404)
        TournesolEntityApi.fetchRating(YOUTUBE, "abc").blockingGet()
        assertNull(TournesolEntityApi.cachedRating(YOUTUBE, "abc"))

        TournesolHttpClient.provider = { null }
        TournesolEntityApi.fetchRating(YOUTUBE, "abc").test().awaitDone(5, TimeUnit.SECONDS)
        assertNull(TournesolEntityApi.cachedRating(YOUTUBE, "abc"))
    }

    private companion object {
        const val YOUTUBE = 0
        const val SOUNDCLOUD = 1
    }
}
