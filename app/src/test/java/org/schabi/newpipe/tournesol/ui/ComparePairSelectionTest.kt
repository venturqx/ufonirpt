package org.schabi.newpipe.tournesol.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.schabi.newpipe.tournesol.CompareComparisonVideo
import org.schabi.newpipe.tournesol.ComparePairSelection

class ComparePairSelectionTest {
    private val suggestedUrl = "https://example.com/suggested"
    private val otherSuggestedUrl = "https://example.com/other"
    private val historyUrl = "https://example.com/history"
    private val otherHistoryUrl = "https://example.com/history2"

    private fun video(url: String?, uid: String = "yt:$url") = CompareComparisonVideo(
        uid = uid,
        title = "title",
        uploader = "uploader",
        thumbnailUrl = null,
        videoUrl = url,
        serviceId = 0
    )

    private fun pair(left: String, right: String) = ComparePairSelection(0, left, 0, right)

    @Test
    fun `two suggestions win over history`() {
        assertEquals(
            pair(suggestedUrl, otherSuggestedUrl),
            comparePairSelection(
                video(suggestedUrl),
                video(otherSuggestedUrl),
                historyEntry(historyUrl),
                historyEntry(otherHistoryUrl)
            )
        )
    }

    @Test
    fun `no suggestion pairs the history entries`() {
        assertEquals(
            pair(historyUrl, otherHistoryUrl),
            comparePairSelection(null, null, historyEntry(historyUrl), historyEntry(otherHistoryUrl))
        )
    }

    @Test
    fun `a left suggestion is paired with the right history entry`() {
        assertEquals(
            pair(suggestedUrl, otherHistoryUrl),
            comparePairSelection(video(suggestedUrl), null, historyEntry(historyUrl), historyEntry(otherHistoryUrl))
        )
    }

    @Test
    fun `a right suggestion is paired with the left history entry`() {
        assertEquals(
            pair(historyUrl, suggestedUrl),
            comparePairSelection(null, video(suggestedUrl), historyEntry(historyUrl), historyEntry(otherHistoryUrl))
        )
    }

    @Test
    fun `a suggestion with no history on the other side gives no pair`() {
        assertNull(comparePairSelection(video(suggestedUrl), null, historyEntry(historyUrl), null))
    }

    @Test
    fun `a suggestion matching the other side gives no pair`() {
        assertNull(comparePairSelection(video(historyUrl), null, null, historyEntry(historyUrl)))
    }

    @Test
    fun `a suggestion without a url does not fall back to the hidden history entry`() {
        assertNull(comparePairSelection(video(null, uid = "yt:a"), null, historyEntry(historyUrl), historyEntry(otherHistoryUrl)))
    }

    @Test
    fun `an invalid suggested pair does not fall back to history`() {
        val sameUid = "yt:same"
        assertNull(
            comparePairSelection(
                video(suggestedUrl, uid = sameUid),
                video(otherSuggestedUrl, uid = sameUid),
                historyEntry(historyUrl),
                historyEntry(otherHistoryUrl)
            )
        )
    }
}
