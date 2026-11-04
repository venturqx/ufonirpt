package org.schabi.newpipe.tournesol.ui

import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.time.OffsetDateTime
import kotlin.random.Random
import org.schabi.newpipe.database.history.model.StreamHistoryEntry
import org.schabi.newpipe.tournesol.CompareRepository
import org.schabi.newpipe.tournesol.CompareScoreStore

internal class CompareController(
    private val state: CompareUiState,
    private val video: TournesolVideoRef?,
    store: CompareScoreStore,
    private val userInfo: CompareUserInfo,
    private val host: CompareHost,
    random: Random = Random.Default
) {
    private val disposables = CompositeDisposable()
    private val tracker = ComparePairTracker(state, store, CompareRepository::buildTournesolUid)
    private var started = false

    val login = CompareLoginFlow(state, host, userInfo) { fillPairIfMissing() }
    val recommendations = CompareRecommendationsLoader(state, host, login)
    val submitter = CompareSubmitter(state, tracker, host, login, userInfo)
    val suggestions = CompareSuggestionLoader(
        state,
        CompareSuggestionPool(
            historyPairAllowed = video == null,
            random = random,
            toVideo = { entry -> buildHistorySuggestion(entry, CompareRepository::buildTournesolUid) }
        ),
        host
    )

    init {
        state.currentEntry = video?.let { buildCurrentEntry(it, OffsetDateTime.now()) }
    }

    fun start(signedOut: Observable<Unit>, history: Flowable<List<StreamHistoryEntry>>?) {
        if (started) {
            return
        }
        started = true
        disposables.add(
            signedOut
                .observeOn(host.mainThread)
                .subscribe { onSignedOut() }
        )
        if (history == null) {
            state.history = CompareHistoryState.Unavailable
        } else {
            disposables.add(
                history
                    .subscribeOn(Schedulers.io())
                    .map { entries -> prepareHistory(entries, video) }
                    .observeOn(host.mainThread)
                    .subscribe(
                        { prepared -> onHistoryPrepared(prepared) },
                        { state.history = CompareHistoryState.Unavailable }
                    )
            )
        }
        if (video == null) {
            suggestions.requestPair()
        } else {
            suggestions.prefetch()
        }
        userInfo.loadWeeklyStats()
        userInfo.refresh()
    }

    fun fillPair() {
        if (video == null) {
            suggestions.requestPair()
        } else {
            suggestions.randomize(SuggestionSlot.RIGHT)
        }
    }

    fun onHistoryPrepared(prepared: CompareHistoryState) {
        state.history = prepared
        submitter.onHistoryUpdated(prepared.entries)
        suggestions.onHistoryUpdated(prepared.entries)
    }

    fun onSignedOut() {
        cancelAuthenticatedWork()
        submitter.reload()
        userInfo.refresh()
    }

    fun onViewDestroyed() {
        started = false
        disposables.clear()
        cancelAuthenticatedWork()
        login.cancel()
        userInfo.cancel()
        // Drop the large lists so a kept Fragment instance does not pin them in heap.
        // The current video, its history entry and the stored scores are small and
        // must survive for a recreated view.
        state.history = state.history.released()
        recommendations.release()
        suggestions.clear()
        tracker.clearSelection()
    }

    private fun fillPairIfMissing() {
        val historyVideosNeeded = if (video == null) 2 else 1
        if (state.suggestedRight == null && state.historyEntries.size < historyVideosNeeded) {
            fillPair()
        }
    }

    private fun cancelAuthenticatedWork() {
        submitter.cancel()
        recommendations.cancel()
        suggestions.cancel()
    }
}
