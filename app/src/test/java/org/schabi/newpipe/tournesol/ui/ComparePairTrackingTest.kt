package org.schabi.newpipe.tournesol.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComparePairTrackingTest {
    private val rig = CompareRig()
    private val state = rig.state

    @After
    fun tearDown() {
        rig.close()
    }

    @Test
    fun `selecting a pair loads what the store knows about it`() {
        rig.store.markSubmitted(KEY_AB)
        rig.store.recordScores(KEY_AB, 12, mapOf("reliability" to 7))

        rig.selectPair(PAIR_AB)

        assertEquals(12, state.score)
        assertEquals(defaultExtraScores() + ("reliability" to 7), state.extraScores)
        assertTrue(state.submitted)
        assertTrue(state.extraSubmitted)
    }

    @Test
    fun `selecting a pair the store has never seen resets the editors`() {
        state.score = 40
        rig.changeExtraScore("reliability", 9)

        rig.selectPair(PAIR_AB)

        assertEquals(0, state.score)
        assertEquals(defaultExtraScores(), state.extraScores)
        assertFalse(state.submitted)
        assertFalse(state.extraSubmitted)
    }

    @Test
    fun `stored extras without a main score leave the main slider at zero`() {
        rig.store.recordScores(KEY_AB, extraScores = mapOf("pedagogy" to 3))

        rig.selectPair(PAIR_AB)

        assertEquals(0, state.score)
        assertEquals(3, state.extraScores["pedagogy"])
        assertTrue(state.extraSubmitted)
    }

    @Test
    fun `selecting the same pair again keeps edits and asks the server nothing`() {
        rig.selectPair(PAIR_AB)
        rig.deliver()
        state.score = 40
        rig.server.calls.clear()

        rig.selectPair(PAIR_AB)

        assertEquals(40, state.score)
        assertEquals(emptyList<String>(), rig.server.calls)
    }

    @Test
    fun `clearing the selection resets the editors without asking the server`() {
        rig.selectPair(PAIR_AB)
        rig.deliver()
        state.score = 40
        rig.server.calls.clear()

        rig.selectPair(null)

        assertEquals(0, state.score)
        assertFalse(state.submitted)
        assertEquals(emptyList<String>(), rig.server.calls)
    }

    @Test
    fun `a pair from an unsupported service behaves like no selection`() {
        rig.selectPair(UNSUPPORTED_PAIR)

        assertFalse(state.submitted)
        assertEquals(emptyList<String>(), rig.server.calls)
    }

    @Test
    fun `the remote check asks with the entities swapped`() {
        rig.selectPair(PAIR_AB)

        assertEquals(listOf(CHECK_AB), rig.server.calls)
    }

    @Test
    fun `a remote yes marks the pair submitted`() {
        rig.server.reply(CHECK_AB, 200)
        rig.selectPair(PAIR_AB)
        assertFalse(state.submitted)

        rig.deliver()

        assertTrue(state.submitted)
        assertTrue(rig.store.isSubmitted(KEY_AB))
    }

    @Test
    fun `a remote no forgets local data but keeps the sliders`() {
        rig.store.markSubmitted(KEY_AB)
        rig.store.recordScores(KEY_AB, 12, mapOf("reliability" to 7))
        rig.server.reply(CHECK_AB, 404)
        rig.selectPair(PAIR_AB)

        rig.deliver()

        assertFalse(state.submitted)
        assertFalse(state.extraSubmitted)
        assertFalse(rig.store.isSubmitted(KEY_AB))
        assertNull(rig.store.scoresFor(KEY_AB))
        assertEquals(12, state.score)
        assertEquals(7, state.extraScores["reliability"])
    }

    @Test
    fun `a remote answer for a pair no longer selected is ignored`() {
        rig.store.markSubmitted(KEY_AB)
        rig.server.reply(CHECK_AB, 404)
        rig.selectPair(PAIR_AB)

        rig.selectPair(null)
        rig.deliver()

        assertTrue(rig.store.isSubmitted(KEY_AB))
        assertFalse(state.submitted)
    }

    @Test
    fun `selecting another pair cancels the pending remote check`() {
        rig.store.markSubmitted(KEY_AB)
        rig.server.reply(CHECK_AB, 404)
        rig.server.reply(CHECK_CD, 200)
        rig.selectPair(PAIR_AB)

        rig.selectPair(PAIR_CD)
        rig.deliver()

        assertTrue(rig.store.isSubmitted(KEY_AB))
        assertTrue(rig.store.isSubmitted(KEY_CD))
        assertTrue(state.submitted)
    }

    @Test
    fun `without a token the local state stands`() {
        rig.token = null
        rig.store.markSubmitted(KEY_AB)

        rig.selectPair(PAIR_AB)
        rig.deliver()

        assertTrue(state.submitted)
        assertEquals(emptyList<String>(), rig.server.calls)
    }

    @Test
    fun `a failed remote check keeps the local state and stays silent`() {
        rig.store.markSubmitted(KEY_AB)
        rig.server.reply(CHECK_AB, 500)

        rig.selectPair(PAIR_AB)
        rig.deliver()

        assertTrue(state.submitted)
        assertEquals(emptyList<String>(), rig.toasts)
    }

    @Test
    fun `changing one extra score keeps the others`() {
        rig.changeExtraScore("reliability", 5)
        rig.changeExtraScore("pedagogy", -5)

        assertEquals(defaultExtraScores() + mapOf("reliability" to 5, "pedagogy" to -5), state.extraScores)
    }
}
