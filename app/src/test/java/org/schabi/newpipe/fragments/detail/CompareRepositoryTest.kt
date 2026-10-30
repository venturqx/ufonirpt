package org.schabi.newpipe.fragments.detail

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.ServiceList

class CompareRepositoryTest {

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
