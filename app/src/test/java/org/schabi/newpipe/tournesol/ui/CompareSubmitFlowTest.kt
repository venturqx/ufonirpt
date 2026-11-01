package org.schabi.newpipe.tournesol.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.tournesol.COMPACT_MAIN_CRITERION_ID
import org.schabi.newpipe.tournesol.ComparisonScores
import org.schabi.newpipe.tournesol.CriteriaScore
import org.schabi.newpipe.tournesol.SessionExpiredException

class CompareSubmitFlowTest {
    private val rig = CompareRig()
    private val state = rig.state
    private var successes = 0
    private val onSuccess: () -> Unit = { successes++ }
    private val sessionExpiredDialog = LoginView(
        visible = true,
        inProgress = false,
        error = message(R.string.compare_session_expired)
    )

    @After
    fun tearDown() {
        rig.close()
    }

    private fun selectSettledPair() {
        rig.selectPair(PAIR_AB)
        rig.deliver()
        rig.server.calls.clear()
    }

    private fun selectSubmittedPair() {
        rig.server.reply(CHECK_AB, 200)
        selectSettledPair()
    }

    private fun failNextSave() {
        rig.server.reply(SAVE, 401)
        selectSettledPair()
        rig.submitCriterion(COMPACT_MAIN_CRITERION_ID, 30, onSuccess)
        rig.deliver()
        rig.server.reply(SAVE, 201)
        rig.server.calls.clear()
    }

    @Test
    fun `a first rating is posted and recorded once the server accepts it`() {
        rig.comparisonCount = 4
        selectSettledPair()

        rig.submitCriterion(COMPACT_MAIN_CRITERION_ID, 30, onSuccess)

        assertTrue(state.submitInProgress)
        assertFalse(state.submitted)
        assertEquals(0, successes)
        assertEquals(listOf(SAVE), rig.server.calls)

        rig.deliver()

        assertFalse(state.submitInProgress)
        assertTrue(state.submitted)
        assertTrue(rig.store.isSubmitted(KEY_AB))
        assertEquals(ComparisonScores(mainScore = 30), rig.store.scoresFor(KEY_AB))
        assertEquals(5, rig.comparisonCount)
        assertEquals(5, state.comparisonCount)
        assertEquals(listOf(longToast(message(R.string.compare_submitted))), rig.toasts)
        assertEquals(1, successes)
    }

    @Test
    fun `a rating the server already has falls back to a patch and counts as an update`() {
        rig.comparisonCount = 4
        rig.server.reply(SAVE, 400, "already compared")
        selectSettledPair()

        rig.submitCriterion(COMPACT_MAIN_CRITERION_ID, 30, onSuccess)
        rig.deliver()

        assertEquals(listOf(SAVE, PATCH_AB), rig.server.calls)
        assertEquals(4, rig.comparisonCount)
        assertEquals(listOf(longToast(message(R.string.compare_score_updated))), rig.toasts)
        assertEquals(1, successes)
    }

    @Test
    fun `a pair already submitted is patched directly`() {
        selectSubmittedPair()

        rig.submitCriterion(COMPACT_MAIN_CRITERION_ID, 30, onSuccess)
        rig.deliver()

        assertEquals(listOf(PATCH_AB), rig.server.calls)
        assertEquals(listOf(longToast(message(R.string.compare_score_updated))), rig.toasts)
    }

    @Test
    fun `an extra criterion rated alone is stored as an extra`() {
        selectSettledPair()

        rig.submitCriterion("reliability", 8, onSuccess)
        rig.deliver()

        assertEquals(ComparisonScores(extraScores = mapOf("reliability" to 8)), rig.store.scoresFor(KEY_AB))
        assertTrue(state.submitted)
        assertTrue(state.extraSubmitted)
    }

    @Test
    fun `a second rating while one is running is dropped`() {
        selectSettledPair()
        rig.submitCriterion(COMPACT_MAIN_CRITERION_ID, 30, onSuccess)

        rig.submitCriterion(COMPACT_MAIN_CRITERION_ID, 50, onSuccess)
        rig.deliver()

        assertEquals(listOf(SAVE), rig.server.calls)
        assertEquals(1, successes)
        assertEquals(30, rig.store.scoresFor(KEY_AB)?.mainScore)
    }

    @Test
    fun `rating with no pair shows the history status`() {
        rig.submitCriterion(COMPACT_MAIN_CRITERION_ID, 30, onSuccess)

        assertEquals(listOf(shortToast(message(R.string.compare_loading_history))), rig.toasts)
        assertFalse(state.submitInProgress)
        assertEquals(emptyList<String>(), rig.server.calls)
    }

    @Test
    fun `rating an unsupported pair says so`() {
        rig.selectPair(UNSUPPORTED_PAIR)

        rig.submitCriterion(COMPACT_MAIN_CRITERION_ID, 30, onSuccess)

        assertEquals(listOf(shortToast(message(R.string.compare_service_not_supported))), rig.toasts)
        assertEquals(0, successes)
    }

    @Test
    fun `no token asks for login`() {
        selectSettledPair()
        rig.token = null

        rig.submitCriterion(COMPACT_MAIN_CRITERION_ID, 30, onSuccess)
        rig.deliver()

        assertFalse(state.submitInProgress)
        assertEquals(listOf(shortToast(message(R.string.compare_login_required))), rig.toasts)
        assertEquals(LoginView(visible = true, inProgress = false, error = null), rig.login)
        assertEquals(emptyList<String>(), rig.server.calls)
        assertEquals(0, successes)
    }

    @Test
    fun `an expired session says so in the login dialog`() {
        rig.server.reply(SAVE, 401)
        selectSettledPair()

        rig.submitCriterion(COMPACT_MAIN_CRITERION_ID, 30, onSuccess)
        rig.deliver()

        assertEquals(emptyList<String>(), rig.toasts)
        assertEquals(sessionExpiredDialog, rig.login)
    }

    @Test
    fun `a rejected refresh token is an expired session too`() {
        selectSettledPair()
        rig.tokenFailure = SessionExpiredException()

        rig.submitCriterion(COMPACT_MAIN_CRITERION_ID, 30, onSuccess)
        rig.deliver()

        assertEquals(sessionExpiredDialog, rig.login)
        assertEquals(emptyList<String>(), rig.server.calls)
    }

    @Test
    fun `an expired rating is resent after logging back in`() {
        failNextSave()

        rig.submitLogin("name", "secret")
        rig.deliver()

        assertEquals(listOf(SAVE), rig.server.calls)
        assertTrue(state.submitted)
        assertEquals(30, rig.store.scoresFor(KEY_AB)?.mainScore)
        assertEquals(
            listOf(shortToast(message(R.string.tournesol_login_success)), longToast(message(R.string.compare_submitted))),
            rig.toasts
        )
        assertEquals(1, successes)
    }

    @Test
    fun `an expired extras batch is resent after logging back in`() {
        rig.server.reply(PATCH_AB, 401)
        selectSubmittedPair()
        rig.submitExtras(listOf(CriteriaScore("pedagogy", 9)), onSuccess)
        rig.deliver()
        rig.server.reply(PATCH_AB, 200)
        rig.server.calls.clear()

        rig.submitLogin("name", "secret")
        rig.deliver()

        assertEquals(listOf(PATCH_AB), rig.server.calls)
        assertEquals(mapOf("pedagogy" to 9), rig.store.scoresFor(KEY_AB)?.extraScores)
        assertEquals(1, successes)
    }

    @Test
    fun `the sign-out from a rejected refresh token does not lose the rating`() {
        selectSettledPair()
        state.score = 30
        rig.tokenFailure = SessionExpiredException()
        rig.submitCriterion(COMPACT_MAIN_CRITERION_ID, 30, onSuccess)
        rig.deliver()

        rig.signedOut()
        rig.deliver()
        assertEquals(0, state.score)

        rig.tokenFailure = null
        rig.submitLogin("name", "secret")
        rig.deliver()

        assertEquals(30, state.score)
        assertEquals(30, rig.store.scoresFor(KEY_AB)?.mainScore)
        assertEquals(1, successes)
    }

    @Test
    fun `a failed login keeps the rating for the next attempt`() {
        failNextSave()
        rig.failLogins()

        rig.submitLogin("name", "wrong")
        rig.deliver()
        assertEquals(emptyList<String>(), rig.server.calls)

        rig.acceptLogins()
        rig.submitLogin("name", "secret")
        rig.deliver()

        assertEquals(listOf(SAVE), rig.server.calls)
        assertEquals(1, successes)
    }

    @Test
    fun `a rating rejected again reopens the dialog once`() {
        rig.server.reply(SAVE, 401)
        selectSettledPair()
        rig.submitCriterion(COMPACT_MAIN_CRITERION_ID, 30, onSuccess)
        rig.deliver()
        rig.server.calls.clear()

        rig.submitLogin("name", "secret")
        rig.deliver()

        assertEquals(listOf(SAVE), rig.server.calls)
        assertEquals(sessionExpiredDialog, rig.login)
        assertEquals(0, successes)
    }

    @Test
    fun `cancelling the login drops the rating`() {
        failNextSave()

        rig.dismissLogin()
        rig.showLogin()
        rig.submitLogin("name", "secret")
        rig.deliver()

        assertEquals(emptyList<String>(), rig.server.calls)
        assertEquals(0, successes)
    }

    @Test
    fun `a rating is not resent once another pair is selected`() {
        failNextSave()
        rig.selectPair(PAIR_CD)
        rig.deliver()
        rig.server.calls.clear()

        rig.submitLogin("name", "secret")
        rig.deliver()

        assertEquals(emptyList<String>(), rig.server.calls)
        assertFalse(rig.store.isSubmitted(KEY_AB))
        assertEquals(0, successes)
    }

    @Test
    fun `destroying the view drops the rating`() {
        failNextSave()
        rig.destroyView()
        rig.selectPair(PAIR_AB)
        rig.deliver()
        rig.server.calls.clear()

        rig.submitLogin("name", "secret")
        rig.deliver()

        assertEquals(emptyList<String>(), rig.server.calls)
        assertEquals(0, successes)
    }

    @Test
    fun `a server error shows the generic text and records nothing`() {
        rig.server.reply(SAVE, 500)
        selectSettledPair()

        rig.submitCriterion(COMPACT_MAIN_CRITERION_ID, 30, onSuccess)
        rig.deliver()

        assertFalse(state.submitInProgress)
        assertFalse(state.submitted)
        assertNull(rig.store.scoresFor(KEY_AB))
        assertEquals(listOf(longToast(message(R.string.compare_failed))), rig.toasts)
        assertFalse(rig.login.visible)
        assertEquals(0, successes)
    }

    @Test
    fun `a network failure shows a fixed message and logs the detail`() {
        rig.server.failNetwork(SAVE, "Unable to resolve host")
        selectSettledPair()

        rig.submitCriterion(COMPACT_MAIN_CRITERION_ID, 30, onSuccess)
        rig.deliver()

        assertEquals(listOf(longToast(message(R.string.network_error))), rig.toasts)
        assertEquals(listOf("Unable to resolve host"), rig.loggedFailures.map { it.message })
    }

    @Test
    fun `a result for a pair no longer selected is stored but not shown as submitted`() {
        selectSettledPair()
        rig.submitCriterion(COMPACT_MAIN_CRITERION_ID, 30, onSuccess)

        rig.selectPair(PAIR_CD)
        assertFalse(state.submitInProgress)
        rig.deliver()

        assertTrue(rig.store.isSubmitted(KEY_AB))
        assertEquals(30, rig.store.scoresFor(KEY_AB)?.mainScore)
        assertFalse(state.submitted)
        assertFalse(state.extraSubmitted)
        assertEquals(0, successes)
    }

    @Test
    fun `an extras result for a pair no longer selected does not report success`() {
        selectSubmittedPair()
        rig.submitExtras(listOf(CriteriaScore("pedagogy", 9)), onSuccess)

        rig.selectPair(PAIR_CD)
        rig.deliver()

        assertEquals(mapOf("pedagogy" to 9), rig.store.scoresFor(KEY_AB)?.extraScores)
        assertEquals(0, successes)
    }

    @Test
    fun `an extras batch patches and merges over stored extras`() {
        rig.store.recordScores(KEY_AB, 12, mapOf("reliability" to 1, "pedagogy" to 2))
        selectSubmittedPair()

        rig.submitExtras(listOf(CriteriaScore("pedagogy", 9), CriteriaScore("importance", 4)), onSuccess)
        assertTrue(state.submitInProgress)
        rig.deliver()

        assertEquals(listOf(PATCH_AB), rig.server.calls)
        assertEquals(
            ComparisonScores(12, mapOf("reliability" to 1, "pedagogy" to 9, "importance" to 4)),
            rig.store.scoresFor(KEY_AB)
        )
        assertEquals(listOf(longToast(message(R.string.compare_more_submitted))), rig.toasts)
        assertEquals(1, successes)
    }

    @Test
    fun `an extras batch does not mark the pair submitted`() {
        selectSettledPair()

        rig.submitExtras(listOf(CriteriaScore("pedagogy", 9)), onSuccess)
        rig.deliver()

        assertFalse(rig.store.isSubmitted(KEY_AB))
        assertFalse(state.submitted)
        assertTrue(state.extraSubmitted)
    }

    @Test
    fun `an empty extras batch does nothing`() {
        selectSettledPair()

        rig.submitExtras(emptyList(), onSuccess)
        rig.deliver()

        assertFalse(state.submitInProgress)
        assertEquals(emptyList<String>(), rig.server.calls)
        assertEquals(emptyList<String>(), rig.toasts)
        assertEquals(0, successes)
    }

    @Test
    fun `an extras batch while a rating is running is dropped`() {
        selectSettledPair()
        rig.submitCriterion(COMPACT_MAIN_CRITERION_ID, 30, onSuccess)

        rig.submitExtras(listOf(CriteriaScore("pedagogy", 9)), onSuccess)
        rig.deliver()

        assertEquals(listOf(SAVE), rig.server.calls)
    }

    @Test
    fun `extras failures use the same messages as ratings`() {
        rig.server.reply(PATCH_AB, 401)
        selectSubmittedPair()

        rig.submitExtras(listOf(CriteriaScore("pedagogy", 9)), onSuccess)
        rig.deliver()

        assertEquals(sessionExpiredDialog, rig.login)
        assertEquals(0, successes)
    }
}
