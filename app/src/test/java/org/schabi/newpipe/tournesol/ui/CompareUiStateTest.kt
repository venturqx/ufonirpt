package org.schabi.newpipe.tournesol.ui

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.tournesol.ComparePairSelection

class CompareUiStateTest {

    private val state = CompareUiState()

    private val readers = mapOf<String, () -> Any?>(
        "score" to { state.score },
        "extraScores" to { state.extraScores },
        "submitted" to { state.submitted },
        "username" to { state.username }
    )

    private fun readersInvalidatedBy(write: () -> Unit): List<String> {
        val invalidated = mutableListOf<String>()
        val observer = SnapshotStateObserver { it() }
        observer.start()
        try {
            readers.forEach { (name, read) ->
                observer.observeReads(name, { invalidated += it }) { read() }
            }
            write()
            Snapshot.sendApplyNotifications()
        } finally {
            observer.stop()
        }
        return invalidated
    }

    @Test
    fun `a score write reaches only score readers`() {
        assertEquals(listOf("score"), readersInvalidatedBy { state.score = 7 })
    }

    @Test
    fun `an extra score write reaches only extra score readers`() {
        val updated = state.extraScores + ("reliability" to 42)

        assertEquals(listOf("extraScores"), readersInvalidatedBy { state.extraScores = updated })
    }

    @Test
    fun `an equal extra scores map reaches no reader`() {
        val equalCopy = state.extraScores.toMap()

        assertEquals(emptyList<String>(), readersInvalidatedBy { state.extraScores = equalCopy })
    }

    private fun signedIn() {
        state.profileLoaded = true
        state.username = "name"
        state.history = CompareHistoryState.Empty
    }

    private fun status(
        selection: ComparePairSelection?,
        uidOf: (String, Int) -> String? = { url, _ -> url }
    ) = compareScreenStatus(state, selection, uidOf)

    @Test
    fun `an unread profile is loading even with a pair`() {
        assertEquals(CompareScreenStatus.Loading, status(PAIR_AB))
    }

    @Test
    fun `a signed out user needs to log in even with a pair`() {
        state.profileLoaded = true

        assertEquals(CompareScreenStatus.NeedsLogin, status(PAIR_AB))
    }

    @Test
    fun `a signed in user with a pair is ready even during a refill`() {
        signedIn()
        state.suggestionsLoading = true

        assertEquals(CompareScreenStatus.Ready, status(PAIR_AB))
    }

    @Test
    fun `a running fetch without a pair is loading`() {
        signedIn()
        state.suggestionsLoading = true

        assertEquals(CompareScreenStatus.Loading, status(null))
    }

    @Test
    fun `loading history without a pair is loading`() {
        signedIn()
        state.history = CompareHistoryState.Loading

        assertEquals(CompareScreenStatus.Loading, status(null))
    }

    @Test
    fun `nothing left to wait for is no pair`() {
        signedIn()

        assertEquals(CompareScreenStatus.NoPair(R.string.compare_no_pair), status(null))
    }

    @Test
    fun `a pair from an unsupported service cannot be rated`() {
        signedIn()

        assertEquals(
            CompareScreenStatus.NoPair(R.string.compare_service_not_supported),
            status(UNSUPPORTED_PAIR) { _, _ -> null }
        )
    }
}
