package org.schabi.newpipe.tournesol.ui

import android.widget.Toast
import androidx.annotation.StringRes
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import org.schabi.newpipe.R
import org.schabi.newpipe.database.history.model.StreamHistoryEntry
import org.schabi.newpipe.tournesol.CompareKey
import org.schabi.newpipe.tournesol.ComparePairSelection
import org.schabi.newpipe.tournesol.CompareRepository
import org.schabi.newpipe.tournesol.CompareSubmitFailure
import org.schabi.newpipe.tournesol.CriteriaScore
import org.schabi.newpipe.tournesol.SaveResult

internal class CompareSubmitter(
    private val state: CompareUiState,
    private val tracker: ComparePairTracker,
    private val host: CompareHost,
    private val login: CompareLoginFlow,
    private val userInfo: CompareUserInfo
) {
    private val submissions = CompositeDisposable()
    private var remoteCheck: Disposable? = null
    private var selectedStreamId: Long? = null

    fun selectPair(selection: ComparePairSelection?) {
        if (tracker.select(selection)) {
            reload()
        }
    }

    fun onHistoryUpdated(entries: List<StreamHistoryEntry>) {
        val newSelectedId = nextSelectedStream(selectedStreamId, entries)
        if (newSelectedId != selectedStreamId) {
            selectedStreamId = newSelectedId
            reload()
        }
    }

    fun reload() {
        state.submitInProgress = false
        tracker.reload()
        requestRemoteCheck()
    }

    fun submitCriterion(criterionId: String, value: Int, onSuccess: () -> Unit) {
        if (state.submitInProgress) {
            return
        }
        val key = keyOrToast() ?: return
        val criteriaScores = listOf(CriteriaScore(criterionId, value))
        val alreadySubmitted = tracker.isSubmitted(key)

        submit(
            key = key,
            criteriaScores = criteriaScores,
            resend = { submitCriterion(criterionId, value, onSuccess) },
            request = { token ->
                CompareRepository.saveComparison(token, key, criteriaScores, alreadySubmitted)
            },
            onResult = { result ->
                tracker.recordCriterion(key, criterionId, value)
                if (result == SaveResult.CREATED) {
                    host.incrementComparisonCount()
                    userInfo.refresh()
                }
                showLongToast(saveResultMessageRes(result))
                if (tracker.key == key) {
                    onSuccess()
                }
            }
        )
    }

    fun submitExtras(criteriaScores: List<CriteriaScore>, onSuccess: () -> Unit) {
        if (state.submitInProgress || criteriaScores.isEmpty()) {
            return
        }
        val key = keyOrToast() ?: return

        submit(
            key = key,
            criteriaScores = criteriaScores,
            resend = { submitExtras(criteriaScores, onSuccess) },
            request = { token ->
                CompareRepository.patchComparison(token, key.entityA, key.entityB, criteriaScores)
            },
            onResult = {
                tracker.recordExtras(key, criteriaScores)
                showLongToast(R.string.compare_more_submitted)
                if (tracker.key == key) {
                    onSuccess()
                }
            }
        )
    }

    fun cancel() {
        submissions.clear()
        state.submitInProgress = false
        remoteCheck?.dispose()
        remoteCheck = null
    }

    private fun keyOrToast(): CompareKey? {
        val key = tracker.key
        if (key == null) {
            val messageRes = if (tracker.hasSelection) {
                R.string.compare_service_not_supported
            } else {
                state.history.noPairMessageRes
            }
            host.showToast(CompareToast(CompareMessage(messageRes), Toast.LENGTH_SHORT))
        }
        return key
    }

    private fun <T : Any> submit(
        key: CompareKey,
        criteriaScores: List<CriteriaScore>,
        resend: () -> Unit,
        request: (String) -> Single<T>,
        onResult: (T) -> Unit
    ) {
        state.submitInProgress = true
        submissions.add(
            host.withToken(request).subscribe(
                { result ->
                    state.submitInProgress = false
                    onResult(result)
                },
                { throwable ->
                    state.submitInProgress = false
                    host.logFailure(throwable)
                    showFailure(CompareSubmitFailure.from(throwable)) {
                        if (tracker.key == key) {
                            tracker.restoreScores(criteriaScores)
                            resend()
                        }
                    }
                }
            )
        )
    }

    private fun showFailure(failure: CompareSubmitFailure, resend: () -> Unit) {
        when (failure) {
            CompareSubmitFailure.LoginRequired -> {
                host.showToast(CompareToast(CompareMessage(R.string.compare_login_required), Toast.LENGTH_SHORT))
                login.show()
            }

            CompareSubmitFailure.SessionExpired -> login.showExpired(resend)

            CompareSubmitFailure.Network, CompareSubmitFailure.Failed -> {
                host.showToast(CompareToast(compareFailedMessage(failure), Toast.LENGTH_LONG))
            }
        }
    }

    private fun showLongToast(@StringRes messageRes: Int) {
        host.showToast(CompareToast(CompareMessage(messageRes), Toast.LENGTH_LONG))
    }

    private fun requestRemoteCheck() {
        val key = tracker.key ?: return
        remoteCheck?.dispose()
        remoteCheck = host
            .ifLoggedIn { token -> CompareRepository.checkComparison(token, key.entityB, key.entityA) }
            .subscribe(
                { exists -> tracker.applyRemoteResult(key, exists) },
                { /* ignore auto-check errors */ },
                { /* no token, keep local state */ }
            )
    }

    private fun saveResultMessageRes(result: SaveResult): Int = when (result) {
        SaveResult.CREATED -> R.string.compare_submitted
        SaveResult.UPDATED -> R.string.compare_score_updated
    }
}
