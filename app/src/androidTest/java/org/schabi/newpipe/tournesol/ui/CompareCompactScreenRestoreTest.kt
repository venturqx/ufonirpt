package org.schabi.newpipe.tournesol.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.database.history.model.StreamHistoryEntry
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.tournesol.ComparePairSelection

@RunWith(AndroidJUnit4::class)
class CompareCompactScreenRestoreTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val state = CompareUiState()
    private val history = CompareHistoryState.Ready(listOf(entry(1), entry(2), entry(3)))
    private var selection: ComparePairSelection? = null

    private fun entry(id: Long) = StreamHistoryEntry(
        streamEntity = StreamEntity(
            serviceId = 0,
            url = "https://www.youtube.com/watch?v=video$id",
            title = "title $id",
            streamType = StreamType.VIDEO_STREAM,
            duration = 0,
            uploader = "uploader"
        ),
        streamId = id,
        accessDate = OffsetDateTime.MIN,
        repeatCount = 0
    )

    @Composable
    private fun Screen() {
        CompareCompactScreen(
            state = state,
            onScoreChange = { state.score = it },
            onExtraScoreChange = { criteria, value -> state.updateExtraScore(criteria, value) },
            onSubmitCriterion = { _, _, _ -> },
            onSubmitExtrasBatch = { _, _ -> },
            onPairSelectionChange = { selection = it },
            onDismissRecommendations = {},
            onDismissLogin = {},
            onShowLogin = {},
            onRegister = {},
            onLogin = { _, _ -> }
        )
    }

    private fun showHistory(value: CompareHistoryState) {
        state.history = value
        composeRule.waitForIdle()
    }

    @Test
    fun historyReturningAfterAGapNeverPairsAVideoWithItself() {
        composeRule.setContent { Screen() }
        showHistory(history)
        assertNotEquals(selection?.leftUrl, selection?.rightUrl)

        showHistory(CompareHistoryState.Unavailable)
        assertNull(selection)
        showHistory(history)

        assertNotNull(selection)
        assertNotEquals(selection?.leftUrl, selection?.rightUrl)
    }

    @Test
    fun restoringTheScreenKeepsTheRatingSession() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { Screen() }
        showHistory(history)
        val pair = selection
        assertNotNull(pair)
        state.score = 34
        state.updateExtraScore("pedagogy", -38)
        state.picker.activeIndex.intValue = 2
        state.picker.touchedCriteria.value = setOf("pedagogy")
        state.picker.submittedCriteria.value = setOf("largely_recommended")
        state.picker.allExtrasDone.value = true

        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()

        assertEquals(pair, selection)
        assertEquals(34, state.score)
        assertEquals(-38, state.extraScores["pedagogy"])
        assertEquals(2, state.picker.activeIndex.intValue)
        assertEquals(setOf("pedagogy"), state.picker.touchedCriteria.value)
        assertEquals(setOf("largely_recommended"), state.picker.submittedCriteria.value)
        assertEquals(true, state.picker.allExtrasDone.value)
    }

    @Test
    fun aNewPairClearsPickerProgress() {
        composeRule.setContent { Screen() }
        showHistory(history)
        state.picker.activeIndex.intValue = 2
        state.picker.touchedCriteria.value = setOf("pedagogy")

        showHistory(CompareHistoryState.Ready(listOf(entry(4), entry(5), entry(6))))

        assertEquals(EXTRA_CRITERIA.size, state.picker.activeIndex.intValue)
        assertEquals(emptySet<String>(), state.picker.touchedCriteria.value)
    }
}
