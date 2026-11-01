package org.schabi.newpipe.tournesol.ui

import android.widget.Toast
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.schedulers.TestScheduler
import java.io.IOException
import java.time.OffsetDateTime
import kotlin.random.Random
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.schabi.newpipe.database.history.model.StreamHistoryEntry
import org.schabi.newpipe.tournesol.ComparePairSelection
import org.schabi.newpipe.tournesol.CompareRepository
import org.schabi.newpipe.tournesol.CompareScoreStore
import org.schabi.newpipe.tournesol.CriteriaScore
import org.schabi.newpipe.tournesol.TournesolHttpClient
import org.schabi.newpipe.tournesol.TournesolProfile

internal data class LoginView(val visible: Boolean, val inProgress: Boolean, val error: String?)

internal data class RecommendationsView(
    val visible: Boolean,
    val loading: Boolean,
    val error: String?,
    val itemCount: Int,
    val totalCount: Int?
)

internal class CompareRig(private val video: TournesolVideoRef? = null, seed: Int = 0) {
    private val main = TestScheduler()
    private val pluginErrors = mutableListOf<Throwable>()
    private var attached = true
    private var loginResult = Completable.complete()

    val server = FakeTournesolServer()
    val store = CompareScoreStore(InMemoryScoreBackend())
    val toasts = mutableListOf<String>()
    val loggedFailures = mutableListOf<Throwable>()
    var token: String? = "token"
    var tokenFailure: Throwable? = null
    var username: String? = null
    var comparisonCount: Int? = null
    val state = CompareUiState()

    private val host = object : CompareHost {
        override val mainThread: Scheduler
            get() = main

        override fun accessToken(): Maybe<String> = Maybe.fromCallable {
            tokenFailure?.let { throw it }
            token
        }

        override fun login(username: String, password: String) = loginResult

        override fun loadProfile(): Single<TournesolProfile>? = if (attached) {
            Single.fromCallable { TournesolProfile(username, comparisonCount, 0) }
        } else {
            null
        }

        override fun incrementComparisonCount() {
            comparisonCount = comparisonCount?.plus(1)
        }

        override fun saveComparisonCount(count: Int) {
            comparisonCount = count
        }

        override fun showToast(toast: CompareToast) {
            val length = if (toast.length == Toast.LENGTH_LONG) "long" else "short"
            toasts += "${text(toast.message)}|$length"
        }

        override fun logFailure(throwable: Throwable) {
            loggedFailures += throwable
        }
    }

    private val userInfo = CompareUserInfo(state, host)
    val controller = CompareController(state, video, store, userInfo, host, Random(seed))

    val login: LoginView
        get() = when (val current = state.login) {
            CompareLoginState.Hidden -> LoginView(visible = false, inProgress = false, error = null)
            CompareLoginState.Submitting -> LoginView(visible = true, inProgress = true, error = null)
            is CompareLoginState.Open -> LoginView(visible = true, inProgress = false, error = current.error?.let(::text))
        }

    val recommendations: RecommendationsView
        get() {
            val current = state.recommendations
            return RecommendationsView(
                visible = state.showRecommendationsDialog,
                loading = current == CompareRecommendations.Loading,
                error = (current as? CompareRecommendations.Failed)?.message?.let(::text),
                itemCount = (current as? CompareRecommendations.Loaded)?.items.orEmpty().size,
                totalCount = current.totalCount
            )
        }

    init {
        RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
        RxJavaPlugins.setErrorHandler { pluginErrors += it }
        val client = OkHttpClient.Builder().addInterceptor(server).build()
        TournesolHttpClient.provider = { client }
    }

    fun deliver() = main.triggerActions()

    fun close() {
        TournesolHttpClient.provider = { null }
        RxJavaPlugins.reset()
        assertEquals(emptyList<Throwable>(), pluginErrors)
    }

    fun detach() {
        attached = false
    }

    fun selectPair(selection: ComparePairSelection?) = controller.submitter.selectPair(selection)

    fun changeExtraScore(criteria: String, value: Int) = state.updateExtraScore(criteria, value)

    fun submitCriterion(criterionId: String, value: Int, onSuccess: () -> Unit) {
        controller.submitter.submitCriterion(criterionId, value, onSuccess)
    }

    fun submitExtras(scores: List<CriteriaScore>, onSuccess: () -> Unit) {
        controller.submitter.submitExtras(scores, onSuccess)
    }

    fun signedOut() = controller.onSignedOut()

    fun destroyView() = controller.onViewDestroyed()

    fun historyChanged(entries: List<StreamHistoryEntry>) {
        controller.onHistoryPrepared(prepareHistory(entries, video))
    }

    fun currentEntry(video: TournesolVideoRef) = buildCurrentEntry(video, OffsetDateTime.now())

    fun historySuggestion(entry: StreamHistoryEntry) = buildHistorySuggestion(entry, CompareRepository::buildTournesolUid)

    fun showLogin() = controller.login.show()

    fun dismissLogin() = controller.login.dismiss()

    fun submitLogin(username: String, password: String) = controller.login.submit(username, password)

    fun failLogins() {
        loginResult = Completable.error(IOException())
    }

    fun acceptLogins() {
        loginResult = Completable.complete()
    }

    fun openRecommendations() = controller.recommendations.open()

    fun dismissRecommendations() = controller.recommendations.dismiss()

    fun requestPair() = controller.suggestions.requestPair()

    fun prefetch() = controller.suggestions.prefetch()

    fun fillPair() = controller.fillPair()

    fun randomize(slot: SuggestionSlot) = controller.suggestions.randomize(slot)

    fun loadWeeklyStats() = userInfo.loadWeeklyStats()

    fun refreshUserInfo() = userInfo.refresh()

    private fun text(message: CompareMessage) = message(message.res)
}
