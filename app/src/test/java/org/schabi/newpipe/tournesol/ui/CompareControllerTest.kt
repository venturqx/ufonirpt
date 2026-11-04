package org.schabi.newpipe.tournesol.ui

import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.subjects.PublishSubject
import java.io.IOException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.database.history.model.StreamHistoryEntry
import org.schabi.newpipe.tournesol.COMPACT_MAIN_CRITERION_ID

class CompareControllerTest {
    private var rig = CompareRig()
    private val state get() = rig.state
    private val signedOut = PublishSubject.create<Unit>()
    private val oneEntry = Flowable.just(listOf(historyEntry(URL_A, 1)))
    private val noEntries: Flowable<List<StreamHistoryEntry>> = Flowable.empty()
    private val threeVideos = """[{"uid":"$UID_A"},{"uid":"$UID_B"},{"uid":"$UID_C"}]"""

    @After
    fun tearDown() {
        rig.close()
    }

    private fun assertNoPairToast(res: Int) {
        rig.toasts.clear()
        rig.submitCriterion(COMPACT_MAIN_CRITERION_ID, 1) {}
        assertEquals(listOf(shortToast(message(res))), rig.toasts)
    }

    @Test
    fun `starting on a tab asks for a pair, weekly stats and the profile`() {
        rig.username = "name"
        rig.server.reply(SUGGESTIONS, 200, threeVideos)
        rig.server.reply(STATS, 200, """{"polls":[{"comparisons":{"added_current_week":12}}]}""")

        rig.controller.start(signedOut, oneEntry)
        rig.deliver()

        assertEquals(listOf(SUGGESTIONS, STATS), rig.server.calls)
        assertEquals(listOf(1L), state.historyEntries.map { it.streamId })
        assertNotNull(state.suggestedLeft)
        assertNotNull(state.suggestedRight)
        assertEquals(12, state.weeklyComparisons)
        assertEquals("name", state.username)
    }

    @Test
    fun `starting on a video only prefetches`() {
        rig.close()
        rig = CompareRig(videoRef(URL_A))
        rig.server.reply(SUGGESTIONS, 200, threeVideos)

        rig.controller.start(signedOut, noEntries)
        rig.deliver()

        assertEquals(listOf(SUGGESTIONS, STATS), rig.server.calls)
        assertNull(state.suggestedLeft)
        assertNull(state.suggestedRight)
    }

    @Test
    fun `a current video gets its entry before the view exists`() {
        rig.close()
        rig = CompareRig(videoRef(URL_A))

        assertEquals(URL_A, state.currentEntry?.streamEntity?.url)
    }

    @Test
    fun `no current video has no entry`() {
        assertNull(state.currentEntry)
    }

    @Test
    fun `disabled history is unavailable`() {
        rig.controller.start(signedOut, null)

        assertNoPairToast(R.string.compare_history_unavailable)
    }

    @Test
    fun `a failing history source is unavailable`() {
        rig.controller.start(signedOut, Flowable.error(IOException()))
        rig.deliver()

        assertNoPairToast(R.string.compare_history_unavailable)
    }

    @Test
    fun `a sign out event reaches the controller on the main thread`() {
        rig.controller.start(signedOut, noEntries)
        rig.deliver()
        rig.selectPair(PAIR_AB)
        rig.deliver()
        state.score = 40

        signedOut.onNext(Unit)
        assertEquals(40, state.score)
        rig.deliver()

        assertEquals(0, state.score)
    }

    @Test
    fun `a destroyed view stops listening to sign outs and history`() {
        val history = PublishSubject.create<List<StreamHistoryEntry>>()
        rig.controller.start(signedOut, history.toFlowable(BackpressureStrategy.LATEST))
        rig.deliver()

        rig.destroyView()
        rig.selectPair(PAIR_AB)
        rig.deliver()
        state.score = 40
        signedOut.onNext(Unit)
        history.onNext(listOf(historyEntry(URL_A, 1)))
        rig.deliver()

        assertEquals(40, state.score)
        assertTrue(state.historyEntries.isEmpty())
    }

    @Test
    fun `a recreated view listens again`() {
        rig.controller.start(signedOut, noEntries)
        rig.destroyView()
        rig.controller.start(signedOut, oneEntry)
        rig.deliver()

        assertEquals(listOf(1L), state.historyEntries.map { it.streamId })
    }

    @Test
    fun `a view recreated over a running controller keeps the session`() {
        rig.server.reply(SUGGESTIONS, 200, threeVideos)
        rig.controller.start(signedOut, oneEntry)
        rig.deliver()
        val left = state.suggestedLeft
        val right = state.suggestedRight
        state.score = 34
        rig.server.calls.clear()
        val history = PublishSubject.create<List<StreamHistoryEntry>>()

        rig.controller.start(signedOut, history.toFlowable(BackpressureStrategy.LATEST))
        rig.deliver()

        assertEquals(emptyList<String>(), rig.server.calls)
        assertFalse(history.hasObservers())
        assertEquals(left, state.suggestedLeft)
        assertEquals(right, state.suggestedRight)
        assertEquals(34, state.score)
    }

    @Test
    fun `a successful login closes the dialog, refreshes the profile and says so`() {
        rig.username = "name"
        rig.showLogin()

        rig.submitLogin("name", "secret")
        assertTrue(rig.login.inProgress)
        rig.deliver()

        assertEquals(LoginView(visible = false, inProgress = false, error = null), rig.login)
        assertEquals("name", state.username)
        assertEquals(listOf(shortToast(message(R.string.tournesol_login_success))), rig.toasts)
    }

    @Test
    fun `extra submitted follows the stored scores`() {
        assertFalse(state.extraSubmitted)

        rig.store.recordScores(KEY_AB, extraScores = mapOf("pedagogy" to 1))
        rig.selectPair(PAIR_AB)

        assertTrue(state.extraSubmitted)
    }
}
