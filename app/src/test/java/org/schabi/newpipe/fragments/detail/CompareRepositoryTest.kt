package org.schabi.newpipe.fragments.detail

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
import org.schabi.newpipe.DownloaderImpl
import org.schabi.newpipe.R
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
        DownloaderImpl.init(OkHttpClient.Builder().addInterceptor(it))
    }

    private fun Request.bodyText(): String = Buffer().also { body!!.writeTo(it) }.readUtf8()

    private fun TestObserver<*>.assertHttpError(message: String) {
        awaitDone(5, TimeUnit.SECONDS)
        assertError { it is IOException && it.message == message }
    }

    @After
    fun restoreDownloader() {
        DownloaderImpl.init(null)
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
            .assertHttpError("HTTP 204 empty")
    }

    @Test
    fun `submitComparison posts json to the collection url without a trailing slash`() {
        val api = serve(code = 201)

        val status = CompareRepository.submitComparison("tok", "yt:a", "yt:b", 30).blockingGet()

        assertEquals(R.string.compare_submitted, status)
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
    fun `submitComparison treats an existing comparison as already submitted`() {
        serve(code = 400, body = """{"detail":"already compared"}""")

        val status = CompareRepository.submitComparison("tok", "yt:a", "yt:b", 30).blockingGet()

        assertEquals(R.string.compare_already_submitted, status)
    }

    @Test
    fun `patchComparison patches the pair url and reports more submitted`() {
        val api = serve()

        val status = CompareRepository.patchComparison(
            "tok",
            "yt:a",
            "yt:b",
            listOf(CriteriaScore("reliability", 5))
        ).blockingGet()

        assertEquals(R.string.compare_more_submitted, status)
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
    fun `fetchEntityToCompare encodes the uid and returns the first video`() {
        val api = serve(body = """[{"uid":"yt:first"},{"uid":"yt:second"}]""")

        val video = CompareRepository.fetchEntityToCompare("tok", "yt:abc").blockingGet()

        assertEquals("yt:first", video?.uid)
        assertEquals(
            "https://api.tournesol.app/users/me/entities_to_compare/videos/" +
                "?first_entity_uid=yt%3Aabc&limit=1",
            api.request.url.toString()
        )
    }

    @Test
    fun `fetchEntityToCompare is empty when there are no results`() {
        serve(body = "[]")

        CompareRepository.fetchEntityToCompare("tok", "yt:abc").test()
            .awaitDone(5, TimeUnit.SECONDS)
            .assertComplete()
            .assertNoValues()
    }

    @Test
    fun `every endpoint maps a failed response to an IOException with status and body`() {
        serve(code = 503, body = "down")
        val message = "HTTP 503 down"

        CompareRepository.checkComparison("tok", "yt:a", "yt:b").test().assertHttpError(message)
        CompareRepository.submitComparison("tok", "yt:a", "yt:b", 1).test().assertHttpError(message)
        CompareRepository.patchComparison("tok", "yt:a", "yt:b", emptyList()).test()
            .assertHttpError(message)
        CompareRepository.fetchUserComparisons("tok").test().assertHttpError(message)
        CompareRepository.fetchWeeklyComparisons().test().assertHttpError(message)
        CompareRepository.fetchSuggestedVideos("tok").test().assertHttpError(message)
        CompareRepository.fetchEntityToCompare("tok", "yt:a").test().assertHttpError(message)
    }

    @Test
    fun `resolveSubmitStatusRes covers the 2xx bounds and reports status and body`() {
        assertEquals(R.string.compare_submitted, CompareRepository.resolveSubmitStatusRes(200, ""))
        assertEquals(R.string.compare_submitted, CompareRepository.resolveSubmitStatusRes(299, ""))

        val rejected = assertThrows(IOException::class.java) {
            CompareRepository.resolveSubmitStatusRes(400, "invalid")
        }
        assertEquals("HTTP 400 invalid", rejected.message)

        assertThrows(IOException::class.java) {
            CompareRepository.resolveSubmitStatusRes(300, "")
        }
        assertThrows(IOException::class.java) {
            CompareRepository.resolveSubmitStatusRes(409, "already compared")
        }
    }

    @Test
    fun `buildComparisonPayloadJson serializes entity pair and criteria scores`() {
        val rawJson = CompareRepository.buildComparisonPayloadJson(
            lastUid = "yt:abc12345678",
            currentUid = "yt:xyz98765432",
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
        CompareRepository.collectObjectCandidates(json, candidates)

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

        val result = CompareRepository.parseComparisons(responseJson)
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
        val listFromArray = CompareRepository.parseSuggestedVideos(arrayJson)
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
        val listFromObj = CompareRepository.parseSuggestedVideos(objectJson)
        assertEquals(1, listFromObj.size)
        assertEquals("sc:track1", listFromObj[0].uid)
        assertEquals("Telemetry Signal", listFromObj[0].title)
        assertNull(listFromObj[0].thumbnailUrl)
    }

    @Test
    fun `parseWeeklyComparisons extracts added_current_week count safely`() {
        val statsJson = """{"polls":[{"comparisons":{"added_current_week":128}}]}"""
        assertEquals(128, CompareRepository.parseWeeklyComparisons(statsJson))
        assertEquals(0, CompareRepository.parseWeeklyComparisons("""{"polls":[]}"""))
    }

    @Test
    fun `resolveSubmitStatusRes maps HTTP statuses and throws on unexpected errors`() {
        assertEquals(
            R.string.compare_submitted,
            CompareRepository.resolveSubmitStatusRes(201, "{}")
        )
        assertEquals(
            R.string.compare_already_submitted,
            CompareRepository.resolveSubmitStatusRes(400, """{"detail":"already compared"}""")
        )
        assertThrows(IOException::class.java) {
            CompareRepository.resolveSubmitStatusRes(500, "Internal Server Error")
        }
    }

    @Test
    fun `uidToServiceId maps prefixes to NewPipe services`() {
        assertEquals(ServiceList.YouTube.serviceId, CompareRepository.uidToServiceId("yt:123"))
        assertEquals(ServiceList.SoundCloud.serviceId, CompareRepository.uidToServiceId("sc:123"))
        assertEquals(
            ServiceList.PeerTube.serviceId,
            CompareRepository.uidToServiceId("peertube:123")
        )
    }
}
