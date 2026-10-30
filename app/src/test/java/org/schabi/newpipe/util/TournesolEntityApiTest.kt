package org.schabi.newpipe.util

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
import org.schabi.newpipe.DownloaderImpl

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
        DownloaderImpl.init(OkHttpClient.Builder().addInterceptor(it))
    }

    @After
    fun restoreDownloader() {
        DownloaderImpl.init(null)
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
        DownloaderImpl.init(
            OkHttpClient.Builder().addInterceptor(Interceptor { throw IOException("down") })
        )

        TournesolEntityApi.fetchRating(YOUTUBE, "abc").test()
            .awaitDone(5, TimeUnit.SECONDS)
            .assertError(IOException::class.java)
    }

    private companion object {
        const val YOUTUBE = 0
        const val SOUNDCLOUD = 1
    }
}
