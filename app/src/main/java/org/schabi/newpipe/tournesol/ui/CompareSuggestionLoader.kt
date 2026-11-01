package org.schabi.newpipe.tournesol.ui

import io.reactivex.rxjava3.disposables.Disposable
import org.schabi.newpipe.database.history.model.StreamHistoryEntry
import org.schabi.newpipe.tournesol.CompareRepository
import org.schabi.newpipe.tournesol.MissingTokenException

internal class CompareSuggestionLoader(
    private val state: CompareUiState,
    private val pool: CompareSuggestionPool,
    private val host: CompareHost
) {
    private var fetch: Disposable? = null

    fun prefetch() = runStep(pool.prefetch())

    fun requestPair() = runStep(pool.requestPair())

    fun randomize(slot: SuggestionSlot) = runStep(pool.randomize(slot, state.historyEntries))

    fun onHistoryUpdated(entries: List<StreamHistoryEntry>) {
        pool.onHistoryUpdated(entries)
        publish()
    }

    fun cancel() {
        fetch?.dispose()
        fetch = null
        pool.onFetchAbandoned()
        state.suggestionsLoading = false
    }

    fun clear() {
        pool.clear()
        publish()
    }

    private fun runStep(step: SuggestionFetch) {
        publish()
        if (step == SuggestionFetch.START) {
            startFetch()
        }
    }

    private fun publish() {
        state.suggestedLeft = pool.left
        state.suggestedRight = pool.right
        state.suggestionsLoading = pool.fetchInFlight
    }

    private fun startFetch() {
        fetch = host
            .withToken { token -> CompareRepository.fetchSuggestedVideos(token) }
            .subscribe(
                { videos ->
                    pool.onFetched(videos)
                    publish()
                },
                { throwable ->
                    if (throwable is MissingTokenException) {
                        pool.onMissingToken(state.historyEntries)
                    } else {
                        pool.onFetchAbandoned()
                    }
                    publish()
                }
            )
    }
}
