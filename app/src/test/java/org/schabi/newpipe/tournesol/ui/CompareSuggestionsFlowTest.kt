package org.schabi.newpipe.tournesol.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareSuggestionsFlowTest {
    private var rig = CompareRig()
    private val state get() = rig.state

    private val threeVideos = """[{"uid":"$UID_A"},{"uid":"$UID_B"},{"uid":"$UID_C"}]"""
    private val twoVideos = """[{"uid":"$UID_A"},{"uid":"$UID_B"}]"""

    @After
    fun tearDown() {
        rig.close()
    }

    private fun assertDistinctPair() {
        assertNotNull(state.suggestedLeft)
        assertNotNull(state.suggestedRight)
        assertNotEquals(state.suggestedLeft?.uid, state.suggestedRight?.uid)
    }

    @Test
    fun `requesting a pair fetches and fills both slots`() {
        rig.server.reply(SUGGESTIONS, 200, threeVideos)

        rig.requestPair()

        assertTrue(state.suggestionsLoading)
        assertEquals(listOf(SUGGESTIONS), rig.server.calls)

        rig.deliver()

        assertFalse(state.suggestionsLoading)
        assertDistinctPair()
    }

    @Test
    fun `a second request during a fetch shares it`() {
        rig.server.reply(SUGGESTIONS, 200, threeVideos)

        rig.requestPair()
        rig.requestPair()
        rig.deliver()

        assertEquals(listOf(SUGGESTIONS), rig.server.calls)
    }

    @Test
    fun `randomizing draws from the pool without fetching while it lasts`() {
        rig.server.reply(SUGGESTIONS, 200, threeVideos)
        rig.requestPair()
        rig.deliver()
        val right = state.suggestedRight

        rig.randomize(SuggestionSlot.LEFT)

        assertEquals(listOf(SUGGESTIONS), rig.server.calls)
        assertEquals(right, state.suggestedRight)
        assertDistinctPair()
    }

    @Test
    fun `randomizing an exhausted pool fetches again`() {
        rig.server.reply(SUGGESTIONS, 200, twoVideos)
        rig.requestPair()
        rig.deliver()

        rig.randomize(SuggestionSlot.LEFT)

        assertTrue(state.suggestionsLoading)
        assertEquals(listOf(SUGGESTIONS, SUGGESTIONS), rig.server.calls)
    }

    @Test
    fun `no token falls back to a pair from history`() {
        rig.token = null
        rig.historyChanged(listOf(historyEntry(URL_A, 1), historyEntry(URL_B, 2)))

        rig.requestPair()
        rig.deliver()

        assertFalse(state.suggestionsLoading)
        assertDistinctPair()
        assertEquals(emptyList<String>(), rig.server.calls)
    }

    @Test
    fun `history arriving after a missing token completes the pair`() {
        rig.token = null
        rig.requestPair()
        rig.deliver()
        assertNull(state.suggestedLeft)

        rig.historyChanged(listOf(historyEntry(URL_A, 1), historyEntry(URL_B, 2)))

        assertDistinctPair()
    }

    @Test
    fun `a failed fetch leaves the slots empty and stops loading`() {
        rig.server.reply(SUGGESTIONS, 500)

        rig.requestPair()
        rig.deliver()

        assertFalse(state.suggestionsLoading)
        assertNull(state.suggestedLeft)
        assertNull(state.suggestedRight)
    }

    @Test
    fun `a prefetch fills the pool but not the slots`() {
        rig.close()
        rig = CompareRig(videoRef(URL_A))
        rig.server.reply(SUGGESTIONS, 200, threeVideos)

        rig.prefetch()
        rig.deliver()

        assertNull(state.suggestedLeft)
        assertNull(state.suggestedRight)
        assertFalse(state.suggestionsLoading)

        rig.randomize(SuggestionSlot.RIGHT)

        assertNotNull(state.suggestedRight)
        assertEquals(listOf(SUGGESTIONS), rig.server.calls)
    }

    @Test
    fun `with a current video no token never builds a history pair`() {
        rig.close()
        rig = CompareRig(videoRef(URL_C))
        rig.token = null
        rig.historyChanged(listOf(historyEntry(URL_A, 1), historyEntry(URL_B, 2)))

        rig.randomize(SuggestionSlot.RIGHT)
        rig.deliver()

        assertNull(state.suggestedLeft)
        assertNotNull(state.suggestedRight)
    }

    @Test
    fun `signing out abandons the fetch`() {
        rig.server.reply(SUGGESTIONS, 200, threeVideos)
        rig.requestPair()

        rig.signedOut()
        assertFalse(state.suggestionsLoading)
        rig.deliver()

        assertNull(state.suggestedLeft)
    }

    @Test
    fun `destroying the view clears the slots and the fetch`() {
        rig.server.reply(SUGGESTIONS, 200, threeVideos)
        rig.requestPair()
        rig.deliver()
        rig.requestPair()

        rig.destroyView()
        rig.deliver()

        assertNull(state.suggestedLeft)
        assertNull(state.suggestedRight)
        assertFalse(state.suggestionsLoading)
    }
}
