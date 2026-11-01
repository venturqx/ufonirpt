package org.schabi.newpipe.tournesol

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import io.reactivex.rxjava3.observers.TestObserver
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.ServiceList

class CompareRepositoryTest {

    private class FakeApi(private val code: Int, private val body: String) : Interceptor {
        @Volatile
        lateinit var request: Request

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

    private fun Request.bodyText(): String = Buffer().also { body!!.writeTo(it) }.readUtf8()

    private fun TestObserver<*>.assertHttpError(httpCode: Int, body: String) {
        awaitDone(5, TimeUnit.SECONDS)
        assertError {
            it is TournesolApiException &&
                it.httpCode == httpCode &&
                it.body == body &&
                it.message == "HTTP $httpCode"
        }
    }

    @After
    fun restoreHttpClient() {
        TournesolHttpClient.provider = { null }
    }

    @Test
    fun `checkComparison encodes uids, keeps the trailing slash and sends the token`() {
        val api = serve()

        assertTrue(CompareRepository.checkComparison("tok", "yt:a b", "sc:x/y").blockingGet())

        assertEquals("GET", api.request.method)
        assertEquals(
            "https://api.tournesol.app/users/me/comparisons/videos/yt:a%20b/sc:x%2Fy/",
            api.request.url.toString()
        )
        assertEquals("Bearer tok", api.request.header("Authorization"))
    }

    @Test
    fun `checkComparison maps 404 to false and anything else to an error`() {
        serve(code = 404)
        assertFalse(CompareRepository.checkComparison("tok", "yt:a", "yt:b").blockingGet())

        serve(code = 204, body = "empty")
        CompareRepository.checkComparison("tok", "yt:a", "yt:b").test()
            .assertHttpError(204, "empty")
    }

    @Test
    fun `submitOrUpdateComparison posts json to the collection url without a trailing slash`() {
        val api = serve(code = 201)

        val status = CompareRepository.submitOrUpdateComparison(
            "tok",
            "yt:a",
            "yt:b",
            listOf(CriteriaScore("largely_recommended", 30))
        ).blockingGet()

        assertEquals(SaveResult.CREATED, status)
        assertEquals("POST", api.request.method)
        assertEquals(
            "https://api.tournesol.app/users/me/comparisons/videos",
            api.request.url.toString()
        )
        assertEquals("Bearer tok", api.request.header("Authorization"))
        assertEquals(
            "application/json; charset=utf-8",
            api.request.body!!.contentType().toString()
        )
        val payload = JsonParser.`object`().from(api.request.bodyText())
        assertEquals("yt:a", payload.getObject("entity_a").getString("uid"))
        assertEquals("yt:b", payload.getObject("entity_b").getString("uid"))
        val score = payload.getArray("criteria_scores").getObject(0)
        assertEquals("largely_recommended", score.getString("criteria"))
        assertEquals(30, score.getInt("score"))
    }

    @Test
    fun `submitOrUpdateComparison patches the existing comparison when the server says already compared`() {
        val requests = mutableListOf<Request>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests.add(chain.request())
            val alreadyCompared = chain.request().method == "POST"
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(if (alreadyCompared) 400 else 200)
                .message("fake")
                .body((if (alreadyCompared) """{"detail":"already compared"}""" else "{}").toResponseBody())
                .build()
        }.build()
        TournesolHttpClient.provider = { client }

        val result = CompareRepository.submitOrUpdateComparison(
            "tok",
            "yt:a",
            "yt:b",
            listOf(CriteriaScore("largely_recommended", 30))
        ).blockingGet()

        assertEquals(SaveResult.UPDATED, result)
        assertEquals(listOf("POST", "PATCH"), requests.map { it.method })
        assertEquals(
            "https://api.tournesol.app/users/me/comparisons/videos/yt:a/yt:b/",
            requests[1].url.toString()
        )
        val patched = JsonParser.`object`().from(requests[1].bodyText())
            .getArray("criteria_scores").getObject(0)
        assertEquals("largely_recommended", patched.getString("criteria"))
        assertEquals(30, patched.getInt("score"))
    }

    @Test
    fun `saveComparison posts when the pair is not submitted yet`() {
        val api = serve(code = 201)

        val result = CompareRepository.saveComparison(
            "tok",
            CompareKey("yt:a", "yt:b"),
            listOf(CriteriaScore("reliability", 5)),
            alreadySubmitted = false
        ).blockingGet()

        assertEquals(SaveResult.CREATED, result)
        assertEquals("POST", api.request.method)
        assertEquals(
            "https://api.tournesol.app/users/me/comparisons/videos",
            api.request.url.toString()
        )
    }

    @Test
    fun `saveComparison patches the pair url when the pair is already submitted`() {
        val api = serve()

        val result = CompareRepository.saveComparison(
            "tok",
            CompareKey("yt:a", "yt:b"),
            listOf(CriteriaScore("reliability", 5)),
            alreadySubmitted = true
        ).blockingGet()

        assertEquals(SaveResult.UPDATED, result)
        assertEquals("PATCH", api.request.method)
        assertEquals(
            "https://api.tournesol.app/users/me/comparisons/videos/yt:a/yt:b/",
            api.request.url.toString()
        )
    }

    @Test
    fun `patchComparison patches the pair url and reports an update`() {
        val api = serve()

        val status = CompareRepository.patchComparison(
            "tok",
            "yt:a",
            "yt:b",
            listOf(CriteriaScore("reliability", 5))
        ).blockingGet()

        assertEquals(SaveResult.UPDATED, status)
        assertEquals("PATCH", api.request.method)
        assertEquals(
            "https://api.tournesol.app/users/me/comparisons/videos/yt:a/yt:b/",
            api.request.url.toString()
        )
        assertEquals(
            "application/json; charset=utf-8",
            api.request.body!!.contentType().toString()
        )
        val payload = JsonParser.`object`().from(api.request.bodyText())
        assertEquals(
            "reliability",
            payload.getArray("criteria_scores").getObject(0).getString("criteria")
        )
    }

    @Test
    fun `fetchUserComparisons targets me by default with the requested limit`() {
        val api = serve(body = """{"count":3,"results":[]}""")

        val result = CompareRepository.fetchUserComparisons("tok").blockingGet()

        assertEquals(3, result.totalCount)
        assertEquals(
            "https://api.tournesol.app/users/me/comparisons/videos?limit=20",
            api.request.url.toString()
        )
        assertEquals("Bearer tok", api.request.header("Authorization"))
    }

    @Test
    fun `fetchUserComparisons encodes the username and clamps the limit`() {
        val api = serve(body = "{}")

        CompareRepository.fetchUserComparisons("tok", username = "a/b", limit = 0).blockingGet()

        assertEquals(
            "https://api.tournesol.app/users/a%2Fb/comparisons/videos?limit=1",
            api.request.url.toString()
        )
    }

    @Test
    fun `fetchWeeklyComparisons sends no auth header`() {
        val api = serve(body = """{"polls":[{"comparisons":{"added_current_week":7}}]}""")

        assertEquals(7, CompareRepository.fetchWeeklyComparisons().blockingGet())

        assertEquals("https://api.tournesol.app/stats/?poll=videos", api.request.url.toString())
        assertNull(api.request.header("Authorization"))
    }

    @Test
    fun `fetchSuggestedVideos keeps the trailing slash and sends the token`() {
        val api = serve(body = """[{"uid":"yt:abc"}]""")

        val videos = CompareRepository.fetchSuggestedVideos("tok").blockingGet()

        assertEquals("yt:abc", videos.single().uid)
        assertEquals(
            "https://api.tournesol.app/users/me/suggestions/videos/tocompare/",
            api.request.url.toString()
        )
        assertEquals("Bearer tok", api.request.header("Authorization"))
    }

    @Test
    fun `every endpoint maps a failed response to an api exception with status and body`() {
        serve(code = 503, body = "down")

        CompareRepository.checkComparison("tok", "yt:a", "yt:b").test().assertHttpError(503, "down")
        CompareRepository.submitOrUpdateComparison("tok", "yt:a", "yt:b", emptyList()).test()
            .assertHttpError(503, "down")
        CompareRepository.patchComparison("tok", "yt:a", "yt:b", emptyList()).test()
            .assertHttpError(503, "down")
        CompareRepository.fetchUserComparisons("tok").test().assertHttpError(503, "down")
        CompareRepository.fetchWeeklyComparisons().test().assertHttpError(503, "down")
        CompareRepository.fetchSuggestedVideos("tok").test().assertHttpError(503, "down")
    }

    @Test
    fun `api exception keeps the body in its property and out of its message`() {
        val error = TournesolApiException(400, "invalid")

        assertEquals(400, error.httpCode)
        assertEquals("invalid", error.body)
        assertEquals("HTTP 400", error.message)
    }

    @Test
    fun `html gateway page stays out of the exception message`() {
        val html = "<html><body><h1>502 Bad Gateway</h1></body></html>"
        serve(code = 502, body = html)

        CompareRepository.fetchSuggestedVideos("tok").test().assertHttpError(502, html)
    }

    @Test
    fun `resolveSubmitOutcome covers the 2xx bounds and reports status and body`() {
        assertEquals(SubmitOutcome.CREATED, CompareRepository.resolveSubmitOutcome(200, ""))
        assertEquals(SubmitOutcome.CREATED, CompareRepository.resolveSubmitOutcome(299, ""))

        val rejected = assertThrows(TournesolApiException::class.java) {
            CompareRepository.resolveSubmitOutcome(400, "invalid")
        }
        assertEquals(400, rejected.httpCode)
        assertEquals("invalid", rejected.body)
        assertEquals("HTTP 400", rejected.message)

        assertThrows(IOException::class.java) {
            CompareRepository.resolveSubmitOutcome(300, "")
        }
        assertThrows(IOException::class.java) {
            CompareRepository.resolveSubmitOutcome(409, "already compared")
        }
    }

    @Test
    fun `buildComparisonPayloadJson serializes entity pair and criteria scores`() {
        val rawJson = CompareRepository.buildComparisonPayloadJson(
            entityA = "yt:abc12345678",
            entityB = "yt:xyz98765432",
            criteriaScores = listOf(
                CriteriaScore("largely_recommended", 45),
                CriteriaScore("reliability", -20)
            )
        )

        val parsed = JsonParser.`object`().from(rawJson)
        assertEquals("videos", parsed.getString("pollName"))
        assertEquals("yt:abc12345678", parsed.getObject("entity_a").getString("uid"))
        assertEquals("yt:xyz98765432", parsed.getObject("entity_b").getString("uid"))

        val scores = parsed.getArray("criteria_scores")
        assertEquals(2, scores.size)
        assertEquals("largely_recommended", scores.getObject(0).getString("criteria"))
        assertEquals(45, scores.getObject(0).getInt("score"))
        assertEquals(SCORE_MAX, scores.getObject(0).getInt("score_max"))
        assertEquals("reliability", scores.getObject(1).getString("criteria"))
        assertEquals(-20, scores.getObject(1).getInt("score"))
    }

    @Test
    fun `collectObjectCandidates does not traverse metadata subtree twice`() {
        val json = JsonParser.`object`().from(
            """
            {
                "uid": "yt:vid1",
                "metadata": {
                    "title": "Spacecraft Guidance",
                    "nested": {
                        "uploader": "ESA"
                    }
                }
            }
            """.trimIndent()
        )
        val candidates = ArrayList<JsonObject>()
        CompareJsonParser.collectObjectCandidates(json, candidates)

        // Root + metadata + nested = 3 distinct objects (previously visited 5 times due to duplicate traversal)
        assertEquals(3, candidates.size)
    }

    @Test
    fun `parseComparisons extracts video metadata, YouTube fallbacks, and scores`() {
        val responseJson = """
            {
                "count": 42,
                "results": [
                    {
                        "entity_a": {
                            "uid": "yt:dQw4w9WgXcQ",
                            "metadata": {
                                "name": "Video Alpha",
                                "channel_name": "Channel One",
                                "thumbnail": "//img.example.com/a.jpg"
                            }
                        },
                        "entity_b": {
                            "uid": "yt:9bZkp7q19f0"
                        },
                        "criteria_scores": [
                            {"criteria": "reliability", "score": 10, "score_max": 100},
                            {"criteria": "largely_recommended", "score": -65, "score_max": 100}
                        ]
                    }
                ]
            }
        """.trimIndent()

        val result = CompareJsonParser.parseComparisons(responseJson)
        assertEquals(42, result.totalCount)
        assertEquals(1, result.comparisons.size)

        val item = result.comparisons.first()
        assertEquals("yt:dQw4w9WgXcQ|yt:9bZkp7q19f0|0", item.uid)
        assertEquals(-65, item.largelyRecommendedScore)
        assertEquals(100, item.scoreMax)

        assertEquals("Video Alpha", item.videoA.title)
        assertEquals("Channel One", item.videoA.uploader)
        assertEquals("https://img.example.com/a.jpg", item.videoA.thumbnailUrl)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", item.videoA.videoUrl)

        // Fallback generation when metadata is absent
        assertEquals("yt:9bZkp7q19f0", item.videoB.title)
        assertEquals("", item.videoB.uploader)
        assertEquals("https://i.ytimg.com/vi/9bZkp7q19f0/hqdefault.jpg", item.videoB.thumbnailUrl)
        assertEquals("https://www.youtube.com/watch?v=9bZkp7q19f0", item.videoB.videoUrl)
    }

    @Test
    fun `parseSuggestedVideos supports both array and paginated object responses`() {
        val arrayJson = """
            [
                {
                    "entity": {
                        "uid": "yt:vidA",
                        "metadata": {"title": "Orbital Mechanics", "uploader": "NASA"}
                    }
                }
            ]
        """.trimIndent()
        val listFromArray = CompareJsonParser.parseSuggestedVideos(arrayJson)
        assertEquals(1, listFromArray.size)
        assertEquals("Orbital Mechanics", listFromArray[0].title)
        assertEquals("NASA", listFromArray[0].uploader)

        val objectJson = """
            {
                "results": [
                    {"uid": "sc:track1", "title": "Telemetry Signal"}
                ]
            }
        """.trimIndent()
        val listFromObj = CompareJsonParser.parseSuggestedVideos(objectJson)
        assertEquals(1, listFromObj.size)
        assertEquals("sc:track1", listFromObj[0].uid)
        assertEquals("Telemetry Signal", listFromObj[0].title)
        assertNull(listFromObj[0].thumbnailUrl)
    }

    @Test
    fun `parseWeeklyComparisons extracts added_current_week count safely`() {
        val statsJson = """{"polls":[{"comparisons":{"added_current_week":128}}]}"""
        assertEquals(128, CompareJsonParser.parseWeeklyComparisons(statsJson))
        assertEquals(0, CompareJsonParser.parseWeeklyComparisons("""{"polls":[]}"""))
    }

    @Test
    fun `resolveSubmitOutcome maps HTTP statuses and throws on unexpected errors`() {
        assertEquals(SubmitOutcome.CREATED, CompareRepository.resolveSubmitOutcome(201, "{}"))
        assertEquals(
            SubmitOutcome.ALREADY_EXISTS,
            CompareRepository.resolveSubmitOutcome(400, """{"detail":"already compared"}""")
        )
        assertThrows(IOException::class.java) {
            CompareRepository.resolveSubmitOutcome(500, "Internal Server Error")
        }
    }

    @Test
    fun `buildTournesolUid prefixes the stream id for urls that resolve offline`() {
        assertEquals(
            "yt:dQw4w9WgXcQ",
            CompareRepository.buildTournesolUid(
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                ServiceList.YouTube.serviceId
            )
        )
        assertEquals(
            "peertube:9c9de5e8-0a1e-484a-b099-e80766180a6d",
            CompareRepository.buildTournesolUid(
                "https://framatube.org/videos/watch/9c9de5e8-0a1e-484a-b099-e80766180a6d",
                ServiceList.PeerTube.serviceId
            )
        )
    }

    @Test
    fun `buildTournesolUid is null for unsupported services and invalid urls`() {
        assertNull(
            CompareRepository.buildTournesolUid(
                "https://example.bandcamp.com/track/a",
                ServiceList.Bandcamp.serviceId
            )
        )
        assertNull(
            CompareRepository.buildTournesolUid(
                "https://example.com/nope",
                ServiceList.YouTube.serviceId
            )
        )
        assertNull(
            CompareRepository.buildTournesolUid(
                "https://example.com/nope",
                ServiceList.SoundCloud.serviceId
            )
        )
    }
}
