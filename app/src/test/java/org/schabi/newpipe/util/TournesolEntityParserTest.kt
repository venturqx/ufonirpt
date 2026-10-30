package org.schabi.newpipe.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TournesolEntityParserTest {

    private val malformedBodies = listOf(
        "",
        "not json",
        "<html>Bad Gateway</html>",
        "{",
        """{"collective_rating":{"tournesol_score":""",
        "[1,2]",
        "\"text\"",
        "null",
        """{"collective_rating":{"tournesol_score":NaN}}"""
    )

    private fun ratingOf(fields: String) = TournesolEntityParser
        .parseRating("""{"collective_rating":{$fields}}""")

    private fun unsafeReasonsOf(unsafe: String) = ratingOf(""""tournesol_score":1,"unsafe":$unsafe""")!!
        .unsafeReasons

    private fun distributionsOf(vararg entries: String) = TournesolEntityParser
        .parseDistributions(
            """{"criteria_scores_distributions":[${entries.joinToString(",")}]}"""
        )!!

    @Test
    fun `parseRating reads a full response and leaves largely_recommended out of the criteria`() {
        val rating = TournesolEntityParser.parseRating(
            """
            {
              "entity": {"uid": "yt:dQw4w9WgXcQ", "type": "video"},
              "collective_rating": {
                "n_comparisons": 120,
                "n_contributors": 35,
                "tournesol_score": 42.57,
                "unsafe": {"status": true, "reasons": ["insufficient_trust"]},
                "criteria_scores": [
                  {"criteria": "largely_recommended", "score": 42.57},
                  {"criteria": "pedagogy", "score": -12},
                  {"criteria": "reliability", "score": 38.1}
                ]
              }
            }
            """.trimIndent()
        )

        assertEquals(
            TournesolRating(
                score = 42.57,
                unsafeReasons = listOf("insufficient_trust"),
                comparisonCount = 120,
                contributorCount = 35,
                criterionScores = listOf(
                    CriterionScore("pedagogy", -12.0),
                    CriterionScore("reliability", 38.1)
                )
            ),
            rating
        )
    }

    @Test
    fun `parseRating needs a collective rating object`() {
        listOf(
            "{}",
            """{"collective_rating":null}""",
            """{"collective_rating":"none"}""",
            """{"collective_rating":[]}"""
        ).forEach { assertNull(it, TournesolEntityParser.parseRating(it)) }
    }

    @Test
    fun `parseRating needs a finite numeric score`() {
        listOf(
            "",
            """"tournesol_score":null""",
            """"tournesol_score":"NaN"""",
            """"tournesol_score":"high"""",
            """"tournesol_score":1e999"""
        ).forEach { assertNull(it, ratingOf(it)) }
    }

    @Test
    fun `parseRating accepts integer, fractional and zero scores`() {
        assertEquals(42.0, ratingOf(""""tournesol_score":42""")!!.score, 0.0)
        assertEquals(-3.25, ratingOf(""""tournesol_score":-3.25""")!!.score, 0.0)
        assertEquals(0.0, ratingOf(""""tournesol_score":0""")!!.score, 0.0)
    }

    @Test
    fun `parseRating defaults optional fields`() {
        val bare = ratingOf(""""tournesol_score":1""")!!
        assertEquals(0, bare.comparisonCount)
        assertEquals(0, bare.contributorCount)
        assertTrue(bare.unsafeReasons.isEmpty())
        assertTrue(bare.criterionScores.isEmpty())

        val nulls = ratingOf(
            """
            "tournesol_score":1,"n_comparisons":null,"n_contributors":null,
            "unsafe":null,"criteria_scores":null
            """.trimIndent()
        )!!
        assertEquals(bare, nulls)
    }

    @Test
    fun `parseRating skips non object criteria and defaults a missing criterion score`() {
        val rating = ratingOf(
            """"tournesol_score":1,"criteria_scores":["x",null,{"criteria":"engaging"}]"""
        )!!

        assertEquals(listOf(CriterionScore("engaging", 0.0)), rating.criterionScores)
    }

    @Test
    fun `unsafe reasons are only read when the status is true`() {
        val reasons = """["insufficient_trust"]"""

        assertEquals(
            listOf("insufficient_trust"),
            unsafeReasonsOf("""{"status":true,"reasons":$reasons}""")
        )
        assertTrue(unsafeReasonsOf("""{"status":false,"reasons":$reasons}""").isEmpty())
        assertTrue(unsafeReasonsOf("""{"reasons":$reasons}""").isEmpty())
        assertTrue(unsafeReasonsOf("""{"status":true}""").isEmpty())
    }

    @Test
    fun `blank and non text unsafe reasons are dropped`() {
        assertEquals(
            listOf("moderation_by_contributors"),
            unsafeReasonsOf(
                """{"status":true,"reasons":["",null,"   ",7,"moderation_by_contributors"]}"""
            )
        )
    }

    @Test
    fun `parsers return null on malformed or non object bodies`() {
        malformedBodies.forEach {
            assertNull(it, TournesolEntityParser.parseRating(it))
            assertNull(it, TournesolEntityParser.parseDistributions(it))
        }
    }

    @Test
    fun `parsers survive the truncated literal that crashes the nanojson tokener`() {
        assertNull(TournesolEntityParser.parseRating("""{"collective_rating":{t"""))
        assertNull(TournesolEntityParser.parseDistributions("""{"collective_rating":{n"""))
    }

    @Test
    fun `parseDistributions keeps every valid criterion and truncates fractional bins`() {
        val distributions = distributionsOf(
            """{"criteria":"largely_recommended","bins":[-100,0,100],"distribution":[1,2]}""",
            """{"criteria":"pedagogy","bins":[-99.5,0.0,99.5],"distribution":[3,4]}"""
        )

        assertEquals(listOf("largely_recommended", "pedagogy"), distributions.keys.toList())
        val pedagogy = distributions.getValue("pedagogy")
        assertArrayEquals(intArrayOf(-99, 0, 99), pedagogy.bins)
        assertArrayEquals(intArrayOf(3, 4), pedagogy.distribution)
    }

    @Test
    fun `parseDistributions treats a missing list as no data and an empty one as no criteria`() {
        assertNull(TournesolEntityParser.parseDistributions("{}"))
        assertNull(
            TournesolEntityParser.parseDistributions("""{"criteria_scores_distributions":null}""")
        )
        assertTrue(distributionsOf().isEmpty())
    }

    @Test
    fun `parseDistributions skips invalid entries and keeps the valid ones`() {
        val distributions = distributionsOf(
            "\"text\"",
            "null",
            """{"bins":[0,1,2],"distribution":[1,2]}""",
            """{"criteria":"","bins":[0,1,2],"distribution":[1,2]}""",
            """{"criteria":null,"bins":[0,1,2],"distribution":[1,2]}""",
            """{"criteria":"no_bins","distribution":[1,2]}""",
            """{"criteria":"no_counts","bins":[0,1,2]}""",
            """{"criteria":"valid","bins":[0,1,2],"distribution":[1,2]}"""
        )

        assertEquals(setOf("valid"), distributions.keys)
    }

    @Test
    fun `parseDistributions rejects arrays the chart cannot draw`() {
        val unusable = listOf(
            """"bins":[0,1],"distribution":[1,2]""",
            """"bins":[0,1,2,3],"distribution":[1,2]""",
            """"bins":[],"distribution":[1,2]""",
            """"bins":[0,1],"distribution":[1]""",
            """"bins":[0],"distribution":[]"""
        )

        unusable.forEach {
            assertTrue(it, distributionsOf("""{"criteria":"pedagogy",$it}""").isEmpty())
        }
    }

    @Test
    fun `an all zero distribution still parses so the chart can stay empty`() {
        assertNotNull(CriterionDistribution.of(intArrayOf(0, 1, 2), intArrayOf(0, 0)))
    }
}
