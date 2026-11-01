package org.schabi.newpipe.tournesol.ui

import io.reactivex.rxjava3.disposables.Disposable
import org.schabi.newpipe.R
import org.schabi.newpipe.tournesol.CompareComparisonsResult
import org.schabi.newpipe.tournesol.CompareRepository
import org.schabi.newpipe.tournesol.CompareSubmitFailure
import org.schabi.newpipe.tournesol.MissingTokenException

private const val COMPARISONS_USERNAME = "me"
private const val COMPARISONS_LIMIT = 20

internal class CompareRecommendationsLoader(
    private val state: CompareUiState,
    private val host: CompareHost,
    private val login: CompareLoginFlow
) {
    private var request: Disposable? = null

    fun open() {
        state.showRecommendationsDialog = true
        state.recommendations = CompareRecommendations.Loading
        request?.dispose()
        request = host
            .withToken { token ->
                CompareRepository.fetchUserComparisons(
                    token = token,
                    username = COMPARISONS_USERNAME,
                    limit = COMPARISONS_LIMIT
                )
            }
            .subscribe(
                { result -> onLoaded(result) },
                { throwable -> onFailed(throwable) }
            )
    }

    fun dismiss() {
        state.showRecommendationsDialog = false
    }

    fun cancel() {
        request?.dispose()
        request = null
        if (state.recommendations == CompareRecommendations.Loading) {
            state.recommendations = CompareRecommendations.Loaded()
        }
    }

    fun release() {
        val current = state.recommendations
        state.recommendations = if (current is CompareRecommendations.Failed) {
            current.copy(totalCount = null)
        } else {
            CompareRecommendations.Loaded()
        }
    }

    private fun onLoaded(result: CompareComparisonsResult) {
        state.recommendations = if (result.comparisons.isEmpty()) {
            CompareRecommendations.Failed(
                CompareMessage(R.string.compare_no_comparisons_available),
                result.totalCount
            )
        } else {
            CompareRecommendations.Loaded(result.comparisons, result.totalCount)
        }
        result.totalCount?.let(host::saveComparisonCount)
    }

    private fun onFailed(throwable: Throwable) {
        if (throwable is MissingTokenException) {
            state.recommendations = CompareRecommendations.Failed(CompareMessage(R.string.compare_login_required))
            login.show()
            return
        }
        host.logFailure(throwable)
        state.recommendations = CompareRecommendations.Failed(
            compareFailedMessage(CompareSubmitFailure.from(throwable))
        )
    }
}
