package org.schabi.newpipe.tournesol.ui

import io.reactivex.rxjava3.disposables.Disposable
import org.schabi.newpipe.tournesol.CompareRepository

internal class CompareUserInfo(
    private val state: CompareUiState,
    private val host: CompareHost
) {
    private var weeklyStats: Disposable? = null
    private var profile: Disposable? = null

    fun loadWeeklyStats() {
        weeklyStats?.dispose()
        weeklyStats = CompareRepository.fetchWeeklyComparisons()
            .observeOn(host.mainThread)
            .subscribe(
                { count -> state.weeklyComparisons = count },
                { /* silently ignore stats errors */ }
            )
    }

    fun refresh() {
        val source = host.loadProfile() ?: return
        profile?.dispose()
        profile = source
            .observeOn(host.mainThread)
            .subscribe(
                { loaded ->
                    state.username = loaded.username
                    state.comparisonCount = loaded.comparisonCount
                    state.dailyComparisons = loaded.dailyComparisons
                    state.profileLoaded = true
                },
                { state.profileLoaded = true }
            )
    }

    fun cancel() {
        weeklyStats?.dispose()
        weeklyStats = null
        profile?.dispose()
        profile = null
    }
}
