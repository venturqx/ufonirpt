package org.schabi.newpipe.fragments.detail

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareJsonParserTest {

    private fun thumbnailOf(metadataJson: String): String? = CompareJsonParser
        .parseSuggestedVideos("""[{"uid":"sc:track","metadata":$metadataJson}]""")
        .single()
        .thumbnailUrl

    @Test
    fun `parseComparisons tolerates missing entities, scores and count`() {
        val result = CompareJsonParser.parseComparisons(
            """{"results":[{}, "not-an-object", {"entity_a":{"uid":"sc:track"}}]}"""
        )

        assertNull(result.totalCount)
        assertEquals(2, result.comparisons.size)

        val empty = result.comparisons[0]
        assertEquals("entity_a_0|entity_b_0|0", empty.uid)
        assertEquals("entity_a_0", empty.videoA.title)
        assertEquals("", empty.videoA.uploader)
        assertNull(empty.videoA.thumbnailUrl)
        assertNull(empty.videoA.videoUrl)
        assertNull(empty.largelyRecommendedScore)
        assertNull(empty.scoreMax)

        val partial = result.comparisons[1]
        assertEquals("sc:track|entity_b_2|2", partial.uid)
        assertEquals("sc:track", partial.videoA.uid)
        assertNull(partial.videoA.thumbnailUrl)
    }

    @Test
    fun `parseComparisons handles an empty document and a negative count`() {
        val empty = CompareJsonParser.parseComparisons("{}")
        assertNull(empty.totalCount)
        assertTrue(empty.comparisons.isEmpty())

        assertNull(CompareJsonParser.parseComparisons("""{"count":-1}""").totalCount)
    }

    @Test
    fun `parseComparisons ignores other criteria and non numeric scores`() {
        val otherCriteria = CompareJsonParser.parseComparisons(
            """{"results":[{"criteria_scores":[{"criteria":"reliability","score":10}]}]}"""
        ).comparisons.single()
        assertNull(otherCriteria.largelyRecommendedScore)

        val textScore = CompareJsonParser.parseComparisons(
            """
            {"results":[{"criteria_scores":[
                {"criteria":"largely_recommended","score":"high","score_max":100}
            ]}]}
            """.trimIndent()
        ).comparisons.single()
        assertNull(textScore.largelyRecommendedScore)
        assertEquals(100, textScore.scoreMax)
    }

    @Test
    fun `parseComparisons prefers entity metadata and falls back to contexts`() {
        val item = CompareJsonParser.parseComparisons(
            """
            {"results":[{
                "entity_a": {"uid": "yt:aaa", "metadata": {"title": "Entity title"}},
                "entity_a_contexts": [{"title": "Context title", "uploader": "Context uploader"}],
                "entity_b": {"uid": "yt:bbb"},
                "entity_b_contexts": [{"metadata": {"title": "Nested context title"}}]
            }]}
            """.trimIndent()
        ).comparisons.single()

        assertEquals("Entity title", item.videoA.title)
        assertEquals("Context uploader", item.videoA.uploader)
        assertEquals("Nested context title", item.videoB.title)
    }

    @Test
    fun `parseSuggestedVideos skips entries without a usable uid`() {
        val videos = CompareJsonParser.parseSuggestedVideos(
            """
              [
                {"entity": {"title": "No uid"}},
                7,
                {"entity": {"uid": "   "}},
                {"uid": "yt:kept"}
              ]
            """
        )

        val kept = videos.single()
        assertEquals("yt:kept", kept.uid)
        assertEquals("yt:kept", kept.title)
        assertEquals("https://i.ytimg.com/vi/kept/hqdefault.jpg", kept.thumbnailUrl)
        assertEquals("https://www.youtube.com/watch?v=kept", kept.videoUrl)
    }

    @Test
    fun `parseSuggestedVideos returns nothing for empty responses of either shape`() {
        assertTrue(CompareJsonParser.parseSuggestedVideos("[]").isEmpty())
        assertTrue(CompareJsonParser.parseSuggestedVideos("{}").isEmpty())
        assertTrue(CompareJsonParser.parseSuggestedVideos("""{"results":[]}""").isEmpty())
    }

    @Test
    fun `parseSuggestedVideos reads metadata from entity_contexts`() {
        val video = CompareJsonParser.parseSuggestedVideos(
            """
            {"results":[{
                "entity": {"uid": "yt:abc"},
                "entity_contexts": [{"uploader": "Context uploader"}]
            }]}
            """.trimIndent()
        ).single()

        assertEquals("Context uploader", video.uploader)
    }

    @Test
    fun `parseWeeklyComparisons returns zero when the count is absent`() {
        assertEquals(0, CompareJsonParser.parseWeeklyComparisons("{}"))
        assertEquals(0, CompareJsonParser.parseWeeklyComparisons("""{"polls":[]}"""))
        assertEquals(0, CompareJsonParser.parseWeeklyComparisons("""{"polls":[{}]}"""))
        assertEquals(0, CompareJsonParser.parseWeeklyComparisons("""{"polls":["x"]}"""))
    }

    @Test
    fun `thumbnail is read from a string and normalized`() {
        assertEquals(
            "https://cdn.example/a.jpg",
            thumbnailOf("""{"thumbnail":"//cdn.example/a.jpg"}""")
        )
        assertEquals(
            "https://cdn.example/a.jpg",
            thumbnailOf("""{"thumbnail":"  https://cdn.example/a.jpg  "}""")
        )
    }

    @Test
    fun `thumbnail is read from an object through the url keys`() {
        assertEquals(
            "https://cdn.example/b.jpg",
            thumbnailOf("""{"thumbnail":{"url":"//cdn.example/b.jpg"}}""")
        )
        assertEquals(
            "https://cdn.example/b.jpg",
            thumbnailOf("""{"thumbnail":{"link":"https://cdn.example/b.jpg"}}""")
        )
    }

    @Test
    fun `thumbnail is read from the first usable array entry`() {
        assertEquals(
            "https://cdn.example/c.jpg",
            thumbnailOf("""{"thumbnails":["","null","//cdn.example/c.jpg"]}""")
        )
        assertEquals(
            "https://cdn.example/d.jpg",
            thumbnailOf("""{"images":[{"video_url":"https://cdn.example/d.jpg"}]}""")
        )
    }

    @Test
    fun `thumbnail keys are tried in order and the null literal is skipped`() {
        assertEquals(
            "https://cdn.example/first.jpg",
            thumbnailOf(
                """
                {
                    "thumbnail": "https://cdn.example/second.jpg",
                    "thumbnail_url": "https://cdn.example/first.jpg"
                }
                """.trimIndent()
            )
        )
        assertEquals(
            "https://cdn.example/e.jpg",
            thumbnailOf("""{"thumbnail":"null","image":"https://cdn.example/e.jpg"}""")
        )
    }

    @Test
    fun `thumbnail is null without metadata unless the uid is a YouTube one`() {
        assertNull(thumbnailOf("""{"title":"No thumbnail"}"""))

        val video = CompareJsonParser.parseSuggestedVideos(
            """[{"uid":"yt:vid","metadata":{"thumbnails":[]}}]"""
        ).single()
        assertEquals("https://i.ytimg.com/vi/vid/hqdefault.jpg", video.thumbnailUrl)
    }

    @Test
    fun `title skips the null literal`() {
        val video = CompareJsonParser.parseSuggestedVideos(
            """[{"uid":"sc:track","metadata":{"title":"null","name":"Real name"}}]"""
        ).single()

        assertEquals("Real name", video.title)
    }

    @Test
    fun `collectObjectCandidates visits metadata before its owner and children after`() {
        val json = JsonParser.`object`().from(
            """{"title":"own","metadata":{"title":"meta"},"child":{"title":"child"}}"""
        )
        val candidates = ArrayList<JsonObject>()

        CompareJsonParser.collectObjectCandidates(json, candidates)

        assertEquals(listOf("meta", "own", "child"), candidates.map { it.getString("title") })
    }

    @Test
    fun `collectObjectCandidates stops below depth five`() {
        var nested = "{}"
        repeat(7) { nested = """{"child":$nested}""" }
        val candidates = ArrayList<JsonObject>()

        CompareJsonParser.collectObjectCandidates(JsonParser.`object`().from(nested), candidates)

        assertEquals(6, candidates.size)
    }
}
