package org.schabi.newpipe.tournesol.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.time.OffsetDateTime
import kotlin.math.abs
import kotlin.random.Random
import kotlin.reflect.KMutableProperty0
import org.schabi.newpipe.R
import org.schabi.newpipe.database.history.model.StreamHistoryEntry
import org.schabi.newpipe.fragments.detail.VideoDetailFragment
import org.schabi.newpipe.ktx.serializable
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.tournesol.CompareComparisonVideo
import org.schabi.newpipe.tournesol.CompareKey
import org.schabi.newpipe.tournesol.ComparePairSelection
import org.schabi.newpipe.tournesol.CompareRepository
import org.schabi.newpipe.tournesol.CompareScoreStore
import org.schabi.newpipe.tournesol.CompareSubmitFailure
import org.schabi.newpipe.tournesol.CriteriaScore
import org.schabi.newpipe.tournesol.MissingTokenException
import org.schabi.newpipe.tournesol.REGISTER_URL
import org.schabi.newpipe.tournesol.SaveResult
import org.schabi.newpipe.tournesol.TournesolAuthManager
import org.schabi.newpipe.tournesol.TournesolLoginFailure
import org.schabi.newpipe.tournesol.compareKeyFor
import org.schabi.newpipe.tournesol.orMissingToken
import org.schabi.newpipe.tournesol.scoresWithCriterion
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.util.NavigationHelper

class CompareFragment : Fragment() {
    private var currentVideo: TournesolVideoRef? = null
    private var embedInDetail = false
    private var externalScroll = false
    private val disposables = CompositeDisposable()
    private val authDisposables = CompositeDisposable()
    private val uiState = CompareUiState()

    private var currentHistoryEntry by uiState::currentEntry
    private var historyEntries by uiState::historyEntries
    private var historyMessageRes by uiState::historyMessageRes
    private var selectedStreamId: Long? = null
    private var score by uiState::score
    private var extraScores by uiState::extraScores
    private var storedMainScore by uiState::storedMainScore
    private var storedExtraScores by uiState::storedExtraScores
    private var submitted by uiState::submitted
    private var extraSubmitted by uiState::extraSubmitted
    private var submitInProgress by uiState::submitInProgress
    private var recommendations by uiState::recommendations
    private var recommendationsTotalCount by uiState::recommendationsTotalCount
    private var recommendationsLoading by uiState::recommendationsLoading
    private var recommendationsError by uiState::recommendationsError
    private var showRecommendationsDialog by uiState::showRecommendationsDialog
    private var showLoginDialog by uiState::showLoginDialog
    private var loginInProgress by uiState::loginInProgress
    private var loginError by uiState::loginError
    private var loginDisposable: Disposable? = null
    private var recommendationsDisposable: Disposable? = null
    private var checkDisposable: Disposable? = null
    private lateinit var scoreStore: CompareScoreStore
    private var compactPairSelection: ComparePairSelection? = null
    private var suggestedLeft by uiState::suggestedLeft
    private var suggestedRight by uiState::suggestedRight
    private var suggestionsLoading by uiState::suggestionsLoading
    private var suggestionsDisposable: Disposable? = null
    private lateinit var suggestionPool: CompareSuggestionPool
    private var weeklyComparisons by uiState::weeklyComparisons
    private var weeklyStatsDisposable: Disposable? = null
    private var dailyComparisons by uiState::dailyComparisons
    private var username by uiState::username
    private var comparisonCount by uiState::comparisonCount
    private var profileDisposable: Disposable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentVideo = arguments?.serializable<TournesolVideoRef>(KEY_VIDEO)
        embedInDetail = arguments?.getBoolean(KEY_EMBED_IN_DETAIL) == true
        externalScroll = arguments?.getBoolean(KEY_EXTERNAL_SCROLL) == true
        currentHistoryEntry = currentVideo?.let { buildCurrentEntry(it) }
        scoreStore = CompareScoreStore.shared(requireContext())
        suggestionPool = CompareSuggestionPool(
            historyPairAllowed = currentVideo == null,
            random = Random.Default,
            toVideo = ::buildHistorySuggestion
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        disposables.add(
            TournesolAuthManager.signedOut()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { onSignedOut() }
        )
        val historyRecordManager = HistoryRecordManager(requireContext())
        if (isHistoryEnabled()) {
            val historySource = historyRecordManager.getStreamHistorySortedById()
            disposables.add(
                historySource
                    .subscribeOn(Schedulers.io())
                    .map { entries -> prepareHistory(entries) }
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        { prepared -> updateHistory(prepared) },
                        {
                            historyEntries = emptyList()
                            historyMessageRes = R.string.compare_history_unavailable
                        }
                    )
            )
        } else {
            historyEntries = emptyList()
            historyMessageRes = R.string.compare_history_unavailable
        }
        if (currentVideo == null) {
            loadRandomPair()
        } else {
            prefetchPool()
        }
        loadWeeklyStats()
        refreshUserInfo()
        return ComposeView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                if (externalScroll) ViewGroup.LayoutParams.WRAP_CONTENT else ViewGroup.LayoutParams.MATCH_PARENT
            )
            ViewCompat.setNestedScrollingEnabled(this, embedInDetail || externalScroll)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AppTheme {
                    val onExtraScoreChange = { criteria: String, value: Int ->
                        updateExtraScore(criteria, value)
                    }
                    CompareCompactScreen(
                        state = uiState,
                        onScoreChange = { score = it },
                        onExtraScoreChange = onExtraScoreChange,
                        onSubmitCriterion = { criterionId, value, onSuccess ->
                            sendCompactCriterion(criterionId, value, onSuccess)
                        },
                        onSubmitExtrasBatch = { criteriaScores, onSuccess ->
                            sendCompactExtrasBatch(criteriaScores, onSuccess)
                        },
                        onPairSelectionChange = { selection ->
                            updateCompactPairSelection(selection)
                        },
                        onDismissRecommendations = { dismissRecommendationsDialog() },
                        onDismissLogin = { dismissLoginDialog() },
                        onShowLogin = { showLoginDialog() },
                        onRegister = { openRegisterPage() },
                        onLogin = { username, password -> performLogin(username, password) },
                        onNavigateToVideo = { serviceId, url, title ->
                            navigateToVideo(serviceId, url, title)
                        },
                        onRandomizeLeft = { randomize(SuggestionSlot.LEFT) },
                        onRandomizeRight = { randomize(SuggestionSlot.RIGHT) },
                        showGreeting = currentVideo == null,
                        reserveMiniPlayerSpace = !embedInDetail,
                        externalScroll = externalScroll,
                        onContentMeasured = if (embedInDetail) {
                            { heightPx ->
                                (parentFragment?.parentFragment as? VideoDetailFragment)
                                    ?.onCompareContentMeasured(heightPx)
                            }
                        } else {
                            null
                        }
                    )
                }
            }
        }
    }

    override fun onDestroyView() {
        disposables.clear()
        cancelAuthenticatedWork()
        loginDisposable?.dispose()
        loginDisposable = null
        loginInProgress = false
        weeklyStatsDisposable?.dispose()
        weeklyStatsDisposable = null
        profileDisposable?.dispose()
        profileDisposable = null
        // Drop the large lists so a kept Fragment instance does not pin them in heap.
        // currentVideo, currentHistoryEntry and stored scores are small, set only in
        // onCreate/applyStored..., and must survive for a recreated view.
        historyEntries = emptyList()
        recommendations = emptyList()
        recommendationsTotalCount = null
        suggestionPool.clear()
        publishSuggestions()
        compactPairSelection = null
        super.onDestroyView()
    }

    private fun onSignedOut() {
        cancelAuthenticatedWork()
        resetSelectionState()
        refreshSubmittedState()
        refreshUserInfo()
    }

    private fun cancelAuthenticatedWork() {
        authDisposables.clear()
        submitInProgress = false
        recommendationsDisposable?.dispose()
        recommendationsDisposable = null
        recommendationsLoading = false
        checkDisposable?.dispose()
        checkDisposable = null
        suggestionsDisposable?.dispose()
        suggestionsDisposable = null
        suggestionPool.onFetchAbandoned()
        suggestionsLoading = false
    }

    private fun prepareHistory(entries: List<StreamHistoryEntry>): PreparedHistory {
        val sorted = entries.sortedByDescending { it.accessDate }
        val current = currentVideo
        val filtered = if (current == null) {
            sorted
        } else {
            sorted.filterNot { entry ->
                entry.streamEntity.serviceId == current.serviceId &&
                    entry.streamEntity.url == current.url
            }
        }
        val messageRes = if (filtered.isEmpty()) {
            R.string.compare_history_empty
        } else {
            null
        }
        return PreparedHistory(
            entries = filtered,
            messageRes = messageRes
        )
    }

    private fun updateHistory(prepared: PreparedHistory) {
        val filtered = prepared.entries
        historyEntries = filtered
        historyMessageRes = prepared.messageRes

        val existingId = selectedStreamId?.takeIf { id -> filtered.any { it.streamId == id } }
        val newSelectedId = existingId ?: filtered.firstOrNull()?.streamId
        if (newSelectedId != selectedStreamId) {
            resetSelectionState()
            selectedStreamId = newSelectedId
            refreshSubmittedState()
        }

        suggestionPool.onHistoryUpdated(filtered)
        publishSuggestions()
    }

    private fun updateCompactPairSelection(selection: ComparePairSelection?) {
        if (compactPairSelection == selection) {
            return
        }
        compactPairSelection = selection
        resetSelectionState()
        refreshSubmittedState()
    }

    private fun isHistoryEnabled(): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val key = getString(R.string.enable_watch_history_key)
        return prefs.getBoolean(key, false)
    }

    private fun buildCurrentEntry(video: TournesolVideoRef): StreamHistoryEntry {
        val hash = video.url.hashCode().toLong()
        val syntheticId = if (hash == 0L) -1L else -abs(hash)
        return StreamHistoryEntry(
            streamEntity = video.stream,
            streamId = syntheticId,
            accessDate = OffsetDateTime.now(),
            repeatCount = 0
        )
    }

    private fun showLoginDialog() {
        loginError = null
        loginInProgress = false
        showLoginDialog = true
    }

    private fun dismissLoginDialog() {
        showLoginDialog = false
        loginInProgress = false
        loginError = null
        loginDisposable?.dispose()
        loginDisposable = null
    }

    private fun navigateToVideo(serviceId: Int, url: String, title: String) {
        val ctx = context ?: return
        NavigationHelper.openVideoDetail(ctx, serviceId, url, title, null, false)
    }

    private fun openRegisterPage() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(REGISTER_URL))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        requireContext().startActivity(intent)
    }

    fun openRecommendationsDialog() {
        showRecommendationsDialog = true
        recommendationsLoading = true
        recommendationsError = null
        recommendations = emptyList()
        recommendationsTotalCount = null
        recommendationsDisposable?.dispose()
        recommendationsDisposable = TournesolAuthManager.getValidAccessToken(requireContext())
            .orMissingToken()
            .flatMap { token ->
                CompareRepository.fetchUserComparisons(
                    token = token,
                    username = COMPARISONS_USERNAME,
                    limit = COMPARISONS_LIMIT
                )
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { result ->
                    recommendationsLoading = false
                    recommendations = result.comparisons
                    recommendationsTotalCount = result.totalCount
                    result.totalCount?.let {
                        TournesolAuthManager.saveComparisonCount(requireContext(), it)
                    }
                    if (result.comparisons.isEmpty()) {
                        recommendationsError =
                            getString(R.string.compare_no_comparisons_available)
                    }
                },
                { throwable ->
                    recommendationsLoading = false
                    if (throwable is MissingTokenException) {
                        recommendationsError = getString(R.string.compare_login_required)
                        showLoginDialog()
                        return@subscribe
                    }
                    val message = throwable.message
                    recommendationsError = if (message.isNullOrBlank()) {
                        getString(R.string.compare_failed)
                    } else {
                        getString(R.string.compare_failed_with_message, message)
                    }
                }
            )
    }

    private fun dismissRecommendationsDialog() {
        showRecommendationsDialog = false
    }

    private fun performLogin(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            loginError = getString(R.string.tournesol_login_missing_fields)
            return
        }
        loginError = null
        loginInProgress = true
        loginDisposable?.dispose()
        loginDisposable = TournesolAuthManager.login(requireContext(), username, password)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                {
                    loginInProgress = false
                    showLoginDialog = false
                    refreshUserInfo()
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.tournesol_login_success),
                        Toast.LENGTH_SHORT
                    ).show()
                },
                { error ->
                    loginInProgress = false
                    loginError = getString(TournesolLoginFailure.from(error).messageRes)
                }
            )
    }

    private fun resolveCompactCompareKey(): CompareKey? {
        val key = compareKeyForSelection()
        if (key == null) {
            val messageRes = if (compactPairSelection == null) {
                historyMessageRes ?: R.string.compare_history_unavailable
            } else {
                R.string.compare_service_not_supported
            }
            Toast.makeText(
                requireContext(),
                getString(messageRes),
                Toast.LENGTH_SHORT
            ).show()
        }
        return key
    }

    private fun sendCompactCriterion(
        criterionId: String,
        value: Int,
        onSuccess: () -> Unit
    ) {
        if (submitInProgress) {
            return
        }
        val key = resolveCompactCompareKey() ?: return
        val criteriaScores = listOf(CriteriaScore(criterionId, value))
        val alreadySubmitted = scoreStore.isSubmitted(key)

        runAuthenticated(
            inProgress = ::submitInProgress,
            request = { token ->
                CompareRepository.saveComparison(token, key, criteriaScores, alreadySubmitted)
            },
            onResult = { result ->
                markSubmitted(key.entityA, key.entityB)
                val updated = scoresWithCriterion(scoreStore.scoresFor(key), criterionId, value)
                storeSubmittedScores(key, updated.mainScore, updated.extraScores)
                if (result == SaveResult.CREATED) {
                    TournesolAuthManager.incrementComparisonCount(requireContext())
                    refreshUserInfo()
                }
                Toast.makeText(
                    requireContext(),
                    getString(saveResultMessageRes(result)),
                    Toast.LENGTH_LONG
                ).show()
                onSuccess()
            }
        )
    }

    private fun sendCompactExtrasBatch(
        criteriaScores: List<CriteriaScore>,
        onSuccess: () -> Unit
    ) {
        if (submitInProgress || criteriaScores.isEmpty()) {
            return
        }
        val key = resolveCompactCompareKey() ?: return

        runAuthenticated(
            inProgress = ::submitInProgress,
            request = { token ->
                CompareRepository.patchComparison(
                    token,
                    key.entityA,
                    key.entityB,
                    criteriaScores
                )
            },
            onResult = {
                val merged = (scoreStore.scoresFor(key)?.extraScores.orEmpty()) +
                    criteriaScores.associate { it.criteria to it.score }
                storeSubmittedScores(key, extraScores = merged)
                Toast.makeText(
                    requireContext(),
                    getString(R.string.compare_more_submitted),
                    Toast.LENGTH_LONG
                ).show()
                onSuccess()
            }
        )
    }

    private fun <T : Any> runAuthenticated(
        inProgress: KMutableProperty0<Boolean>,
        request: (String) -> Single<T>,
        onResult: (T) -> Unit
    ) {
        inProgress.set(true)
        authDisposables.add(
            TournesolAuthManager.getValidAccessToken(requireContext())
                .orMissingToken()
                .flatMap { token -> request(token) }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { result ->
                        inProgress.set(false)
                        onResult(result)
                    },
                    { throwable ->
                        inProgress.set(false)
                        showSubmitFailure(CompareSubmitFailure.from(throwable))
                    }
                )
        )
    }

    private fun showSubmitFailure(failure: CompareSubmitFailure) {
        when (failure) {
            CompareSubmitFailure.LoginRequired -> {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.compare_login_required),
                    Toast.LENGTH_SHORT
                ).show()
                showLoginDialog()
            }

            is CompareSubmitFailure.Failed -> {
                val errorText = if (failure.message == null) {
                    getString(R.string.compare_failed)
                } else {
                    getString(R.string.compare_failed_with_message, failure.message)
                }
                Toast.makeText(requireContext(), errorText, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateExtraScore(criteria: String, value: Int) {
        val updated = extraScores.toMutableMap()
        updated[criteria] = value
        extraScores = updated
    }

    private fun resetSelectionState() {
        submitInProgress = false
        score = 0
        extraScores = defaultExtraScores()
        storedMainScore = null
        storedExtraScores = emptyMap()
        extraSubmitted = false
        applyStoredScoresForSelection()
    }

    private fun applyStoredScoresForSelection() {
        val key = compareKeyForSelection() ?: return
        val stored = scoreStore.scoresFor(key) ?: return
        storedMainScore = stored.mainScore
        storedExtraScores = stored.extraScores
        extraSubmitted = stored.extraScores.isNotEmpty()
        stored.mainScore?.let { score = it }
        if (stored.extraScores.isNotEmpty()) {
            val updated = defaultExtraScores().toMutableMap()
            stored.extraScores.forEach { (criteria, value) ->
                updated[criteria] = value
            }
            extraScores = updated
        }
    }

    private fun updateSubmittedState() {
        submitted = compareKeyForSelection()?.let(scoreStore::isSubmitted) == true
    }

    private fun refreshSubmittedState() {
        updateSubmittedState()
        requestRemoteSubmittedCheck()
    }

    private fun requestRemoteSubmittedCheck() {
        val key = compareKeyForSelection() ?: return
        checkDisposable?.dispose()
        checkDisposable = TournesolAuthManager.getValidAccessToken(requireContext())
            .flatMapSingle { token ->
                CompareRepository.checkComparison(token, key.entityB, key.entityA)
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { exists -> applyRemoteSubmittedResult(key, exists) },
                { /* ignore auto-check errors */ },
                { /* no token, keep local state */ }
            )
    }

    private fun applyRemoteSubmittedResult(key: CompareKey, exists: Boolean) {
        if (compareKeyForSelection() != key) {
            return
        }
        if (exists) {
            markSubmitted(key.entityA, key.entityB)
        } else {
            scoreStore.forget(key)
            if (compareKeyForSelection() == key) {
                storedMainScore = null
                storedExtraScores = emptyMap()
                extraSubmitted = false
            }
            submitted = false
        }
    }

    private fun compareKeyForSelection(): CompareKey? {
        val pair = compactPairSelection ?: return null
        return compareKeyFor(pair, CompareRepository::buildTournesolUid)
    }

    private fun saveResultMessageRes(result: SaveResult): Int = when (result) {
        SaveResult.CREATED -> R.string.compare_submitted
        SaveResult.UPDATED -> R.string.compare_score_updated
    }

    private fun markSubmitted(entityA: String, entityB: String) {
        val key = CompareKey(entityA, entityB)
        scoreStore.markSubmitted(key)
        if (compareKeyForSelection() == key) {
            submitted = true
        }
    }

    private fun storeSubmittedScores(
        key: CompareKey,
        mainScore: Int? = null,
        extraScores: Map<String, Int>? = null
    ) {
        val merged = scoreStore.recordScores(key, mainScore, extraScores) ?: return
        if (compareKeyForSelection() == key) {
            storedMainScore = merged.mainScore
            storedExtraScores = merged.extraScores
            extraSubmitted = merged.extraScores.isNotEmpty()
        }
    }

    private data class PreparedHistory(
        val entries: List<StreamHistoryEntry>,
        val messageRes: Int?
    )

    private fun prefetchPool() {
        runSuggestionStep(suggestionPool.prefetch())
    }

    private fun loadRandomPair() {
        runSuggestionStep(suggestionPool.requestPair())
    }

    private fun randomize(slot: SuggestionSlot) {
        runSuggestionStep(suggestionPool.randomize(slot, historyEntries))
    }

    private fun runSuggestionStep(fetch: SuggestionFetch) {
        publishSuggestions()
        if (fetch == SuggestionFetch.START) {
            fetchSuggestions()
        }
    }

    private fun publishSuggestions() {
        suggestedLeft = suggestionPool.left
        suggestedRight = suggestionPool.right
        suggestionsLoading = suggestionPool.fetchInFlight
    }

    private fun buildHistorySuggestion(entry: StreamHistoryEntry): CompareComparisonVideo {
        val entity = entry.streamEntity
        val uid = CompareRepository.buildTournesolUid(entity.url, entity.serviceId)
            ?: "history:${entity.serviceId}:${entry.streamId}"
        return CompareComparisonVideo(
            uid = uid,
            title = entity.title,
            uploader = entity.uploader,
            thumbnailUrl = entity.thumbnailUrl,
            videoUrl = entity.url,
            serviceId = entity.serviceId
        )
    }

    private fun fetchSuggestions() {
        suggestionsDisposable = TournesolAuthManager.getValidAccessToken(requireContext())
            .orMissingToken()
            .flatMap { token ->
                CompareRepository.fetchSuggestedVideos(token)
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { videos ->
                    suggestionPool.onFetched(videos)
                    publishSuggestions()
                },
                { throwable ->
                    if (throwable is MissingTokenException) {
                        suggestionPool.onMissingToken(historyEntries)
                    } else {
                        suggestionPool.onFetchAbandoned()
                    }
                    publishSuggestions()
                }
            )
    }

    override fun onResume() {
        super.onResume()
        refreshUserInfo()
        consumePendingOpenComparisons()
    }

    fun consumePendingOpenComparisons() {
        if (pendingOpenComparisons) {
            pendingOpenComparisons = false
            openRecommendationsDialog()
        }
    }

    private fun loadWeeklyStats() {
        weeklyStatsDisposable?.dispose()
        weeklyStatsDisposable = CompareRepository.fetchWeeklyComparisons()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { count -> weeklyComparisons = count },
                { /* silently ignore stats errors */ }
            )
    }

    fun refreshUserInfo() {
        val ctx = context ?: return
        profileDisposable?.dispose()
        profileDisposable = TournesolAuthManager.loadProfile(ctx)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { profile ->
                    username = profile.username
                    comparisonCount = profile.comparisonCount
                    dailyComparisons = profile.dailyComparisons
                },
                { /* keep the last known profile on read errors */ }
            )
    }

    companion object {
        @JvmStatic
        var pendingOpenComparisons = false

        private const val KEY_VIDEO = "compare_video"
        private const val KEY_EMBED_IN_DETAIL = "compare_embed_in_detail"
        private const val KEY_EXTERNAL_SCROLL = "compare_external_scroll"
        private const val COMPARISONS_USERNAME = "me"
        private const val COMPARISONS_LIMIT = 20

        @JvmStatic
        @JvmOverloads
        fun getInstance(
            video: TournesolVideoRef?,
            embedInDetail: Boolean = false,
            externalScroll: Boolean = false
        ): CompareFragment {
            return CompareFragment().apply {
                arguments = bundleOf(
                    KEY_VIDEO to video,
                    KEY_EMBED_IN_DETAIL to embedInDetail,
                    KEY_EXTERNAL_SCROLL to externalScroll
                )
            }
        }
    }
}
