package org.schabi.newpipe.tournesol.ui

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import org.junit.Assert.assertEquals
import org.junit.Test

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
}
