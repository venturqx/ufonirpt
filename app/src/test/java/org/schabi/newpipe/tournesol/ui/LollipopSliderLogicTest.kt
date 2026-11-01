package org.schabi.newpipe.tournesol.ui

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.tournesol.COMPACT_MAIN_CRITERION_ID
import org.schabi.newpipe.tournesol.CriteriaScore

class LollipopSliderLogicTest {
    private val extraIds = EXTRA_CRITERIA.map { it.id }

    private fun qualifierText(criterionId: String, score: Int): String = buildQualifierLine(criterionId, score).let { it.qualifier + it.tail }

    @Test
    fun `arrows follow the score bands`() {
        val expected = mapOf(
            -100 to ("<<" to ""),
            -70 to ("<<" to ""),
            -69 to ("<" to ""),
            -16 to ("<" to ""),
            -15 to ("" to ""),
            0 to ("" to ""),
            15 to ("" to ""),
            16 to ("" to ">"),
            69 to ("" to ">"),
            70 to ("" to ">>"),
            100 to ("" to ">>")
        )
        for (id in extraIds + COMPACT_MAIN_CRITERION_ID) {
            expected.forEach { (score, arrows) ->
                val line = buildQualifierLine(id, score)
                assertEquals("$id at $score", arrows.first, line.leftArrow)
                assertEquals("$id at $score", arrows.second, line.rightArrow)
            }
        }
    }

    @Test
    fun `comparative qualifier follows the score bands`() {
        val expected = mapOf(
            -100 to "much more",
            -70 to "much more",
            -69 to "slightly more",
            -16 to "slightly more",
            -15 to "just as",
            0 to "just as",
            15 to "just as",
            16 to "slightly more",
            69 to "slightly more",
            70 to "much more",
            100 to "much more"
        )
        expected.forEach { (score, qualifier) ->
            assertEquals(qualifier, buildQualifierLine("reliability", score).qualifier)
        }
    }

    @Test
    fun `goodness qualifier follows the score bands`() {
        val expected = mapOf(
            -100 to "much better",
            -70 to "much better",
            -69 to "slightly better",
            -16 to "slightly better",
            -15 to "as good",
            0 to "as good",
            15 to "as good",
            16 to "slightly better",
            69 to "slightly better",
            70 to "much better",
            100 to "much better"
        )
        for (id in listOf("better_habits", "backfire_risk")) {
            expected.forEach { (score, qualifier) ->
                val line = buildQualifierLine(id, score)
                assertEquals(qualifier, line.qualifier)
                assertEquals("", line.tail)
            }
        }
    }

    @Test
    fun `each criterion has its own tail`() {
        val expected = mapOf(
            "largely_recommended" to "just as recommended",
            "reliability" to "just as reliable",
            "pedagogy" to "just as pedagogical",
            "importance" to "just as important",
            "layman_friendly" to "just as accessible",
            "entertaining_relaxing" to "just as entertaining",
            "engaging" to "just as engaging",
            "diversity_inclusion" to "just as inclusive",
            "better_habits" to "as good",
            "backfire_risk" to "as good"
        )
        expected.forEach { (id, text) -> assertEquals(text, qualifierText(id, 0)) }
    }

    @Test
    fun `unknown criterion reads as recommended`() {
        assertEquals("slightly more recommended", qualifierText("unknown", 20))
    }

    @Test
    fun `negative or main index selects the main criterion`() {
        assertEquals(COMPACT_MAIN_CRITERION_ID, criterionIdAt(-1))
        assertEquals(COMPACT_MAIN_CRITERION_ID, criterionIdAt(EXTRA_CRITERIA.size))
    }

    @Test
    fun `other indices select the matching extra criterion`() {
        EXTRA_CRITERIA.forEachIndexed { index, criterion ->
            assertEquals(criterion.id, criterionIdAt(index))
        }
    }

    @Test
    fun `no extra number while the main criterion is active`() {
        assertEquals(0, activeExtraNumber(-1))
        assertEquals(0, activeExtraNumber(EXTRA_CRITERIA.size))
    }

    @Test
    fun `extra numbers count from one`() {
        assertEquals(1, activeExtraNumber(0))
        assertEquals(EXTRA_CRITERIA.size, activeExtraNumber(EXTRA_CRITERIA.lastIndex))
    }

    @Test
    fun `main criterion reads the main score`() {
        assertEquals(42, criterionScore(COMPACT_MAIN_CRITERION_ID, 42, mapOf("reliability" to 7)))
    }

    @Test
    fun `extra criterion reads its own score and defaults to zero`() {
        assertEquals(7, criterionScore("reliability", 42, mapOf("reliability" to 7)))
        assertEquals(0, criterionScore("pedagogy", 42, mapOf("reliability" to 7)))
    }

    @Test
    fun `first untouched index skips touched criteria`() {
        assertEquals(0, firstUntouchedExtraIndex(emptySet()))
        assertEquals(1, firstUntouchedExtraIndex(setOf(extraIds[0])))
        assertEquals(0, firstUntouchedExtraIndex(setOf(extraIds[1])))
        assertEquals(0, firstUntouchedExtraIndex(setOf(COMPACT_MAIN_CRITERION_ID)))
    }

    @Test
    fun `no untouched index once every extra is touched`() {
        assertNull(firstUntouchedExtraIndex(extraIds.toSet()))
    }

    @Test
    fun `touched extras keep criteria order and ignore the main criterion`() {
        val touched = setOf(extraIds[2], COMPACT_MAIN_CRITERION_ID, extraIds[0])
        val scores = mapOf(extraIds[0] to 5, extraIds[1] to 9, extraIds[2] to -3)
        assertEquals(
            listOf(CriteriaScore(extraIds[0], 5), CriteriaScore(extraIds[2], -3)),
            touchedExtraScores(touched, scores)
        )
    }

    @Test
    fun `touched extra without a score defaults to zero`() {
        assertEquals(
            listOf(CriteriaScore(extraIds[0], 0)),
            touchedExtraScores(setOf(extraIds[0]), emptyMap())
        )
    }

    @Test
    fun `nothing touched gives an empty batch`() {
        assertEquals(emptyList<CriteriaScore>(), touchedExtraScores(emptySet(), emptyMap()))
    }

    @Test
    fun `zero score has no fill`() {
        assertNull(trackFill(score = 0, thumbX = 100f, centerX = 100f, thumbGap = 5f))
    }

    @Test
    fun `negative score fills from the thumb gap to the center`() {
        assertEquals(
            TrackFill(left = 45f, right = 100f),
            trackFill(score = -50, thumbX = 40f, centerX = 100f, thumbGap = 5f)
        )
    }

    @Test
    fun `positive score fills from the center to the thumb gap`() {
        assertEquals(
            TrackFill(left = 100f, right = 155f),
            trackFill(score = 50, thumbX = 160f, centerX = 100f, thumbGap = 5f)
        )
    }

    @Test
    fun `thumb inside the gap has no fill`() {
        assertNull(trackFill(score = 1, thumbX = 103f, centerX = 100f, thumbGap = 5f))
        assertNull(trackFill(score = -1, thumbX = 97f, centerX = 100f, thumbGap = 5f))
    }

    @Test
    fun `thumb exactly at the gap has no fill`() {
        assertNull(trackFill(score = 1, thumbX = 105f, centerX = 100f, thumbGap = 5f))
        assertNull(trackFill(score = -1, thumbX = 95f, centerX = 100f, thumbGap = 5f))
    }

    private inner class DragHarness(criterionId: String, startScore: Int = 3) {
        val dispatched = mutableListOf<Int>()
        val submitted = mutableListOf<Pair<String, Int>>()
        val touched = mutableStateOf(emptySet<String>())
        val activeIndex = mutableIntStateOf(-1)
        val allDone = mutableStateOf(false)
        val isDragging = mutableStateOf(false)
        val isInCancelZone = mutableStateOf(false)
        val dragScore = mutableStateOf<Int?>(null)
        val controller = SliderDragController(
            criterionId = criterionId,
            latestScore = { startScore },
            dispatchChange = { dispatched += it },
            onSubmit1 = { id, value -> submitted += id to value },
            touchedCriteria = touched,
            activeIndex = activeIndex,
            allExtrasDone = allDone,
            isDragging = isDragging,
            isInCancelZone = isInCancelZone,
            dragScore = dragScore
        )
    }

    @Test
    fun `drag start shows the drag and marks the criterion touched`() {
        val harness = DragHarness(extraIds[0]).apply { isInCancelZone.value = true }

        val newTouched = harness.controller.start(40)

        assertTrue(harness.isDragging.value)
        assertFalse(harness.isInCancelZone.value)
        assertEquals(40, harness.dragScore.value)
        assertEquals(listOf(40), harness.dispatched)
        assertEquals(setOf(extraIds[0]), newTouched)
        assertEquals(setOf(extraIds[0]), harness.touched.value)
    }

    @Test
    fun `drag move publishes the score and the cancel zone`() {
        val harness = DragHarness(extraIds[0])

        harness.controller.move(55, inCancelZone = true)

        assertTrue(harness.isInCancelZone.value)
        assertEquals(55, harness.dragScore.value)
        assertEquals(listOf(55), harness.dispatched)
    }

    @Test
    fun `release in the cancel zone restores the score without advancing`() {
        val harness = DragHarness(COMPACT_MAIN_CRITERION_ID)
        val newTouched = harness.controller.start(40)
        harness.controller.move(55, inCancelZone = true)

        harness.controller.release(scoreBeforeDrag = 3, finalScore = 55, newTouched = newTouched)

        assertFalse(harness.isDragging.value)
        assertFalse(harness.isInCancelZone.value)
        assertNull(harness.dragScore.value)
        assertEquals(listOf(40, 55, 3), harness.dispatched)
        assertEquals(emptySet<String>(), harness.touched.value)
        assertEquals(emptyList<Pair<String, Int>>(), harness.submitted)
        assertEquals(-1, harness.activeIndex.intValue)
        assertFalse(harness.allDone.value)
    }

    @Test
    fun `release of the main criterion submits it and moves to the first extra`() {
        val harness = DragHarness(COMPACT_MAIN_CRITERION_ID)
        val newTouched = harness.controller.start(40)

        harness.controller.release(scoreBeforeDrag = 3, finalScore = 40, newTouched = newTouched)

        assertFalse(harness.isDragging.value)
        assertNull(harness.dragScore.value)
        assertEquals(listOf(COMPACT_MAIN_CRITERION_ID to 40), harness.submitted)
        assertEquals(0, harness.activeIndex.intValue)
        assertFalse(harness.allDone.value)
    }

    @Test
    fun `release of an extra criterion moves on without submitting`() {
        val harness = DragHarness(extraIds[0])
        val newTouched = harness.controller.start(40)

        harness.controller.release(scoreBeforeDrag = 3, finalScore = 40, newTouched = newTouched)

        assertEquals(emptyList<Pair<String, Int>>(), harness.submitted)
        assertEquals(1, harness.activeIndex.intValue)
        assertFalse(harness.allDone.value)
    }

    @Test
    fun `release of the last untouched extra finishes`() {
        val harness = DragHarness(extraIds.last())
        harness.touched.value = extraIds.dropLast(1).toSet()
        val newTouched = harness.controller.start(40)

        harness.controller.release(scoreBeforeDrag = 3, finalScore = 40, newTouched = newTouched)

        assertTrue(harness.allDone.value)
        assertEquals(-1, harness.activeIndex.intValue)
    }

    @Test
    fun `release of the main criterion finishes when every extra is touched`() {
        val harness = DragHarness(COMPACT_MAIN_CRITERION_ID)
        harness.touched.value = extraIds.toSet()
        val newTouched = harness.controller.start(40)

        harness.controller.release(scoreBeforeDrag = 3, finalScore = 40, newTouched = newTouched)

        assertEquals(listOf(COMPACT_MAIN_CRITERION_ID to 40), harness.submitted)
        assertTrue(harness.allDone.value)
    }

    @Test
    fun `release advances from the touched set captured at drag start`() {
        val harness = DragHarness(extraIds[0])
        val newTouched = harness.controller.start(40)
        harness.touched.value = emptySet()

        harness.controller.release(scoreBeforeDrag = 3, finalScore = 40, newTouched = newTouched)

        assertEquals(1, harness.activeIndex.intValue)
    }

    @Test
    fun `cancelling a previously touched criterion drops it from the touched set`() {
        val harness = DragHarness(extraIds[0])
        harness.touched.value = setOf(extraIds[0], extraIds[1])
        val newTouched = harness.controller.start(40)
        harness.controller.move(40, inCancelZone = true)

        harness.controller.release(scoreBeforeDrag = 3, finalScore = 40, newTouched = newTouched)

        assertEquals(setOf(extraIds[1]), harness.touched.value)
    }

    @Test
    fun `controller works without a hoisted drag score`() {
        val harness = DragHarness(extraIds[0])
        val controller = harness.controller.copy(dragScore = null)

        val newTouched = controller.start(40)
        controller.move(50, inCancelZone = false)
        controller.release(scoreBeforeDrag = 3, finalScore = 50, newTouched = newTouched)

        assertEquals(listOf(40, 50), harness.dispatched)
        assertEquals(1, harness.activeIndex.intValue)
    }
}
