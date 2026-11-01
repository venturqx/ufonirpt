package org.schabi.newpipe.tournesol.ui

import java.time.OffsetDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.tournesol.COMPACT_MAIN_CRITERION_ID

class CompareHistoryFlowTest {
    private var rig = CompareRig()
    private val state get() = rig.state

    private val older = OffsetDateTime.parse("2024-01-01T00:00:00Z")
    private val newer = OffsetDateTime.parse("2025-01-01T00:00:00Z")

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
    fun `entries are shown newest first`() {
        rig.historyChanged(listOf(historyEntry(URL_A, 1, older), historyEntry(URL_B, 2, newer)))

        assertEquals(listOf(2L, 1L), state.historyEntries.map { it.streamId })
    }

    @Test
    fun `the current video is left out of the history`() {
        rig.close()
        rig = CompareRig(videoRef(URL_A))

        rig.historyChanged(listOf(historyEntry(URL_A, 1), historyEntry(URL_B, 2)))

        assertEquals(listOf(2L), state.historyEntries.map { it.streamId })
    }

    @Test
    fun `the same url on another service is not the current video`() {
        rig.close()
        rig = CompareRig(videoRef(URL_A))

        rig.historyChanged(listOf(historyEntry(URL_A, 1, serviceId = 1)))

        assertEquals(listOf(1L), state.historyEntries.map { it.streamId })
    }

    @Test
    fun `no pair before history arrives reports loading`() {
        assertNoPairToast(R.string.compare_loading_history)
    }

    @Test
    fun `no pair with an empty history reports it is empty`() {
        rig.historyChanged(emptyList())

        assertNoPairToast(R.string.compare_history_empty)
    }

    @Test
    fun `no pair with a filled history reports history unavailable`() {
        rig.historyChanged(listOf(historyEntry(URL_A, 1)))

        assertNoPairToast(R.string.compare_history_unavailable)
    }

    @Test
    fun `a history whose only entry is the current video counts as empty`() {
        rig.close()
        rig = CompareRig(videoRef(URL_A))

        rig.historyChanged(listOf(historyEntry(URL_A, 1)))

        assertNoPairToast(R.string.compare_history_empty)
    }

    @Test
    fun `a new newest stream reloads the selected pair`() {
        rig.historyChanged(listOf(historyEntry(URL_A, 1)))
        rig.selectPair(PAIR_AB)
        rig.deliver()
        state.score = 40
        rig.server.calls.clear()

        rig.historyChanged(listOf(historyEntry(URL_B, 2, newer), historyEntry(URL_A, 1, older)))

        assertEquals(40, state.score)
        rig.historyChanged(listOf(historyEntry(URL_B, 2, newer)))

        assertEquals(0, state.score)
        assertEquals(listOf(CHECK_AB), rig.server.calls)
    }

    @Test
    fun `the first history reloads the selected pair`() {
        rig.selectPair(PAIR_AB)
        rig.deliver()
        state.score = 40

        rig.historyChanged(listOf(historyEntry(URL_A, 1)))

        assertEquals(0, state.score)
    }

    @Test
    fun `a history keeping the tracked stream does not reload`() {
        rig.historyChanged(listOf(historyEntry(URL_A, 1), historyEntry(URL_B, 2)))
        rig.selectPair(PAIR_AB)
        rig.deliver()
        state.score = 40

        rig.historyChanged(listOf(historyEntry(URL_B, 2), historyEntry(URL_A, 1)))

        assertEquals(40, state.score)
    }

    @Test
    fun `an empty history after a filled one reloads the pair`() {
        rig.historyChanged(listOf(historyEntry(URL_A, 1)))
        rig.selectPair(PAIR_AB)
        rig.deliver()
        state.score = 40

        rig.historyChanged(emptyList())

        assertEquals(0, state.score)
    }

    @Test
    fun `a current entry has a stable negative id and no repeats`() {
        val first = rig.currentEntry(videoRef(URL_A))
        val second = rig.currentEntry(videoRef(URL_A))

        assertEquals(first.streamId, second.streamId)
        assertTrue(first.streamId < 0)
        assertEquals(0L, first.repeatCount)
        assertEquals(URL_A, first.streamEntity.url)
    }

    @Test
    fun `a url hashing to zero still gets a nonzero id`() {
        assertEquals(0, "".hashCode())

        assertEquals(-1L, rig.currentEntry(videoRef("")).streamId)
    }

    @Test
    fun `different urls get different current entry ids`() {
        assertNotEquals(
            rig.currentEntry(videoRef(URL_A)).streamId,
            rig.currentEntry(videoRef(URL_B)).streamId
        )
    }

    @Test
    fun `a history suggestion carries the tournesol uid`() {
        val video = rig.historySuggestion(historyEntry(URL_A, 7))

        assertEquals(UID_A, video.uid)
        assertEquals(URL_A, video.videoUrl)
        assertEquals(0, video.serviceId)
        assertEquals("title", video.title)
        assertEquals("uploader", video.uploader)
    }

    @Test
    fun `a history suggestion without a uid uses a synthetic one`() {
        val video = rig.historySuggestion(historyEntry("x", 7, serviceId = UNSUPPORTED_SERVICE))

        assertEquals("history:$UNSUPPORTED_SERVICE:7", video.uid)
    }

    @Test
    fun `destroying the view drops entries but they come back with history`() {
        rig.historyChanged(listOf(historyEntry(URL_A, 1)))

        rig.destroyView()
        assertTrue(state.historyEntries.isEmpty())
        assertNoPairToast(R.string.compare_history_unavailable)

        rig.historyChanged(listOf(historyEntry(URL_A, 1)))
        assertFalse(state.historyEntries.isEmpty())
    }

    @Test
    fun `destroying the view keeps the empty history message`() {
        rig.historyChanged(emptyList())

        rig.destroyView()

        assertNoPairToast(R.string.compare_history_empty)
    }

    @Test
    fun `destroying the view keeps the loading message`() {
        rig.destroyView()

        assertNoPairToast(R.string.compare_loading_history)
    }
}
