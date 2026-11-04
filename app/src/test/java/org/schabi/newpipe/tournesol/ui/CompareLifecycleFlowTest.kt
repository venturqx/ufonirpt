package org.schabi.newpipe.tournesol.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.tournesol.COMPACT_MAIN_CRITERION_ID

class CompareLifecycleFlowTest {
    private val rig = CompareRig()
    private val state = rig.state
    private var successes = 0
    private val onSuccess: () -> Unit = { successes++ }

    @After
    fun tearDown() {
        rig.close()
    }

    @Test
    fun `signing out cancels a running submission`() {
        rig.selectPair(PAIR_AB)
        rig.deliver()
        rig.submitCriterion(COMPACT_MAIN_CRITERION_ID, 30, onSuccess)

        rig.signedOut()
        assertFalse(state.submitInProgress)
        rig.deliver()

        assertEquals(0, successes)
        assertEquals(emptyList<String>(), rig.toasts)
        assertFalse(rig.store.isSubmitted(KEY_AB))
    }

    @Test
    fun `signing out cancels the running remote check`() {
        rig.store.markSubmitted(KEY_AB)
        rig.server.reply(CHECK_AB, 404)
        rig.selectPair(PAIR_AB)
        rig.token = null

        rig.signedOut()
        rig.deliver()

        assertTrue(rig.store.isSubmitted(KEY_AB))
    }

    @Test
    fun `signing out reloads the pair from the wiped store and asks the server again`() {
        rig.store.recordScores(KEY_AB, 12)
        rig.server.reply(CHECK_AB, 200)
        rig.selectPair(PAIR_AB)
        rig.deliver()
        assertTrue(state.submitted)
        rig.store.forget(KEY_AB)
        rig.server.calls.clear()

        rig.signedOut()

        assertEquals(0, state.score)
        assertFalse(state.submitted)
        assertEquals(listOf(CHECK_AB), rig.server.calls)
    }

    @Test
    fun `signing out with no token keeps the pair selected but silent`() {
        rig.selectPair(PAIR_AB)
        rig.deliver()
        rig.server.calls.clear()
        rig.token = null

        rig.signedOut()
        rig.deliver()

        assertEquals(emptyList<String>(), rig.server.calls)
    }

    @Test
    fun `signing out refreshes the user info`() {
        rig.username = "before"
        rig.refreshUserInfo()
        rig.deliver()
        assertEquals("before", state.username)
        rig.username = null

        rig.signedOut()
        rig.deliver()

        assertNull(state.username)
    }

    @Test
    fun `work can start again after signing out`() {
        rig.selectPair(PAIR_AB)
        rig.deliver()
        rig.signedOut()
        rig.deliver()

        rig.submitCriterion(COMPACT_MAIN_CRITERION_ID, 30, onSuccess)
        rig.deliver()

        assertEquals(1, successes)
        assertTrue(state.submitted)
    }

    @Test
    fun `destroying the view cancels a running submission`() {
        rig.selectPair(PAIR_AB)
        rig.deliver()
        rig.submitCriterion(COMPACT_MAIN_CRITERION_ID, 30, onSuccess)

        rig.destroyView()
        rig.deliver()

        assertFalse(state.submitInProgress)
        assertEquals(0, successes)
        assertEquals(emptyList<String>(), rig.toasts)
    }

    @Test
    fun `destroying the view forgets the pair but keeps the score state`() {
        rig.store.markSubmitted(KEY_AB)
        rig.store.recordScores(KEY_AB, 12, mapOf("reliability" to 7))
        rig.server.reply(CHECK_AB, 200)
        rig.selectPair(PAIR_AB)
        rig.deliver()

        rig.destroyView()

        assertEquals(12, state.score)
        assertEquals(7, state.extraScores["reliability"])
        assertTrue(state.submitted)
        assertTrue(state.extraSubmitted)

        rig.submitCriterion(COMPACT_MAIN_CRITERION_ID, 30, onSuccess)
        assertEquals(listOf(shortToast(message(R.string.compare_loading_history))), rig.toasts)
    }

    @Test
    fun `the same pair after a recreated view reloads`() {
        rig.selectPair(PAIR_AB)
        rig.deliver()
        rig.destroyView()
        state.score = 40
        rig.server.calls.clear()

        rig.selectPair(PAIR_AB)

        assertEquals(0, state.score)
        assertEquals(listOf(CHECK_AB), rig.server.calls)
    }

    @Test
    fun `destroying the view cancels stats and profile`() {
        rig.username = "name"
        rig.server.reply(STATS, 200, WEEKLY_STATS)
        rig.loadWeeklyStats()
        rig.refreshUserInfo()

        rig.destroyView()
        rig.deliver()

        assertNull(state.weeklyComparisons)
        assertNull(state.username)
    }

    @Test
    fun `signing out leaves weekly stats running`() {
        rig.server.reply(STATS, 200, WEEKLY_STATS)
        rig.loadWeeklyStats()

        rig.signedOut()
        rig.deliver()

        assertEquals(12, state.weeklyComparisons)
    }

    @Test
    fun `weekly stats errors are ignored`() {
        rig.server.reply(STATS, 500)

        rig.loadWeeklyStats()
        rig.deliver()

        assertNull(state.weeklyComparisons)
        assertEquals(emptyList<String>(), rig.toasts)
    }

    @Test
    fun `user info fills name and count`() {
        rig.username = "name"
        rig.comparisonCount = 3

        rig.refreshUserInfo()
        rig.deliver()

        assertEquals("name", state.username)
        assertEquals(3, state.comparisonCount)
    }

    @Test
    fun `a detached fragment does not load user info`() {
        rig.username = "name"
        rig.detach()

        rig.refreshUserInfo()
        rig.deliver()

        assertNull(state.username)
    }

    private companion object {
        const val WEEKLY_STATS = """{"polls":[{"comparisons":{"added_current_week":12}}]}"""
    }
}
