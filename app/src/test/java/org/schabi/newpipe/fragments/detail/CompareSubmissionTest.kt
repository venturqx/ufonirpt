package org.schabi.newpipe.fragments.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompareSubmissionTest {

    private val selection = ComparePairSelection(
        leftServiceId = 0,
        leftUrl = "left",
        rightServiceId = 1,
        rightUrl = "right"
    )

    @Test
    fun `compareKeyFor resolves each url with its own service`() {
        val key = compareKeyFor(selection) { url, serviceId -> "$serviceId:$url" }

        assertEquals(CompareKey(entityA = "0:left", entityB = "1:right"), key)
    }

    @Test
    fun `compareKeyFor is null when either side has no uid`() {
        assertNull(compareKeyFor(selection) { url, _ -> url.takeIf { it == "left" } })
        assertNull(compareKeyFor(selection) { url, _ -> url.takeIf { it == "right" } })
    }

    @Test
    fun `scoresWithCriterion sets the main score and keeps the extras`() {
        val stored = ComparisonScores(mainScore = 1, extraScores = mapOf("reliability" to 2))

        val updated = scoresWithCriterion(stored, COMPACT_MAIN_CRITERION_ID, 50)

        assertEquals(
            ComparisonScores(mainScore = 50, extraScores = mapOf("reliability" to 2)),
            updated
        )
    }

    @Test
    fun `scoresWithCriterion merges an extra and keeps the main score`() {
        val stored = ComparisonScores(mainScore = 1, extraScores = mapOf("reliability" to 2))

        val updated = scoresWithCriterion(stored, "pedagogy", 3)

        assertEquals(
            ComparisonScores(
                mainScore = 1,
                extraScores = mapOf("reliability" to 2, "pedagogy" to 3)
            ),
            updated
        )
    }

    @Test
    fun `scoresWithCriterion overwrites an extra that was already stored`() {
        val stored = ComparisonScores(extraScores = mapOf("reliability" to 2))

        val updated = scoresWithCriterion(stored, "reliability", 9)

        assertEquals(mapOf("reliability" to 9), updated.extraScores)
    }

    @Test
    fun `scoresWithCriterion starts empty when nothing is stored`() {
        assertEquals(
            ComparisonScores(mainScore = 7),
            scoresWithCriterion(null, COMPACT_MAIN_CRITERION_ID, 7)
        )
        assertEquals(
            ComparisonScores(extraScores = mapOf("pedagogy" to 7)),
            scoresWithCriterion(null, "pedagogy", 7)
        )
    }
}
