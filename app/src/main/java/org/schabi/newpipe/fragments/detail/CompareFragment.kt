package org.schabi.newpipe.fragments.detail

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.fragments.detail.compare.CompareCompactScreen
import org.schabi.newpipe.fragments.detail.compare.CompareScreen
import org.schabi.newpipe.fragments.detail.compare.CompareSuggestionPool
import org.schabi.newpipe.fragments.detail.compare.SuggestionFetch
import org.schabi.newpipe.fragments.detail.compare.SuggestionSlot
import org.schabi.newpipe.fragments.detail.compare.entitiesForLeftRight
import org.schabi.newpipe.ktx.serializable
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.util.KEY_INFO
import org.schabi.newpipe.util.MissingTokenException
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.TournesolAuthManager
import org.schabi.newpipe.util.orMissingToken

class CompareFragment : Fragment() {
    private var currentInfo: StreamInfo? = null
    private var useCompactUi = false
    private var embedInDetail = false
    private var externalScroll = false
    private val disposables = CompositeDisposable()
    private val authDisposables = CompositeDisposable()

    private var currentHistoryEntry: StreamHistoryEntry? = null
    private var historyEntries by mutableStateOf<List<StreamHistoryEntry>>(emptyList())
    private var historyMessageRes by mutableStateOf<Int?>(R.string.compare_loading_history)
    private var selectedIndex by mutableIntStateOf(0)
    private var selectedStreamId: Long? = null
    private var score by mutableIntStateOf(0)
    private var extraScores by mutableStateOf(defaultExtraScores())
    private var storedMainScore by mutableStateOf<Int?>(null)
    private var storedExtraScores by mutableStateOf<Map<String, Int>>(emptyMap())
    private var submitted by mutableStateOf(false)
    private var submittedConfirmed by mutableStateOf(false)
    private var extraSubmitted by mutableStateOf(false)
    private var submitInProgress by mutableStateOf(false)
    private var changeInProgress by mutableStateOf(false)
    private var submitMoreInProgress by mutableStateOf(false)
    private var compactPopupVisible by mutableStateOf(false)
    private var recommendations by mutableStateOf<List<CompareRecommendationItem>>(emptyList())
    private var recommendationsTotalCount by mutableStateOf<Int?>(null)
    private var recommendationsLoading by mutableStateOf(false)
    private var recommendationsError by mutableStateOf<String?>(null)
    private var showRecommendationsDialog by mutableStateOf(false)
    private var showLoginDialog by mutableStateOf(false)
    private var loginInProgress by mutableStateOf(false)
    private var loginError by mutableStateOf<String?>(null)
    private var loginDisposable: Disposable? = null
    private var recommendationsDisposable: Disposable? = null
    private var checkDisposable: Disposable? = null
    private lateinit var scoreStore: CompareScoreStore
    private var compactPairSelection: ComparePairSelection? = null
    private var suggestedLeft by mutableStateOf<CompareComparisonVideo?>(null)
    private var suggestedRight by mutableStateOf<CompareComparisonVideo?>(null)
    private var suggestionsLoading by mutableStateOf(false)
    private var suggestionsDisposable: Disposable? = null
    private lateinit var suggestionPool: CompareSuggestionPool
    private var weeklyComparisons by mutableStateOf<Int?>(null)
    private var weeklyStatsDisposable: Disposable? = null
    private var dailyComparisons by mutableStateOf<Int?>(null)
    private var username by mutableStateOf<String?>(null)
    private var comparisonCount by mutableStateOf<Int?>(null)
    private var profileDisposable: Disposable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentInfo = arguments?.serializable<StreamInfo>(KEY_INFO)
        useCompactUi = arguments?.getBoolean(KEY_COMPACT_UI) == true
        embedInDetail = arguments?.getBoolean(KEY_EMBED_IN_DETAIL) == true
        externalScroll = arguments?.getBoolean(KEY_EXTERNAL_SCROLL) == true
        currentHistoryEntry = currentInfo?.let { buildCurrentEntry(it) }
        scoreStore = CompareScoreStore.shared(requireContext())
        suggestionPool = CompareSuggestionPool(
            historyPairAllowed = useCompactUi && currentInfo == null,
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
        if (currentInfo == null && useCompactUi) {
            loadRandomPair()
        } else if (useCompactUi) {
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
                    val uiState = CompareUiState(
                        historyEntries = historyEntries,
                        currentEntry = currentHistoryEntry,
                        selectedIndex = selectedIndex,
                        historyMessageRes = historyMessageRes,
                        score = score,
                        extraScores = extraScores,
                        storedMainScore = storedMainScore,
                        storedExtraScores = storedExtraScores,
                        submitted = submitted,
                        submittedConfirmed = submittedConfirmed,
                        extraSubmitted = extraSubmitted,
                        submitInProgress = submitInProgress,
                        changeInProgress = changeInProgress,
                        submitMoreInProgress = submitMoreInProgress,
                        recommendations = recommendations,
                        recommendationsTotalCount = recommendationsTotalCount,
                        recommendationsLoading = recommendationsLoading,
                        recommendationsError = recommendationsError,
                        showRecommendationsDialog = showRecommendationsDialog,
                        showLoginDialog = showLoginDialog,
                        loginInProgress = loginInProgress,
                        loginError = loginError,
                        compactPopupVisible = compactPopupVisible,
                        suggestedLeft = suggestedLeft,
                        suggestedRight = suggestedRight,
                        suggestionsLoading = suggestionsLoading,
                        weeklyComparisons = weeklyComparisons,
                        dailyComparisons = dailyComparisons,
                        username = username,
                        comparisonCount = comparisonCount
                    )
                    val onExtraScoreChange = { criteria: String, value: Int ->
                        updateExtraScore(criteria, value)
                    }
                    if (useCompactUi) {
                        CompareCompactScreen(
                            state = uiState,
                            onScoreChange = { score = it },
                            onExtraScoreChange = onExtraScoreChange,
                            onSubmitCriterion = { criterionId, value, onSuccess ->
                                if (criterionId == COMPACT_MAIN_CRITERION_ID) {
                                    sendCompactMainComparison(value, onSuccess)
                                } else {
                                    sendCompactSingleCriterion(criterionId, value, onSuccess)
                                }
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
                            showGreeting = currentInfo == null,
                            reserveMiniPlayerSpace = !embedInDetail,
                            externalScroll = externalScroll,
                            onContentMeasured = if (embedInDetail) {
                                { heightPx ->
                                    (parentFragment as? VideoDetailFragment)
                                        ?.onCompareContentMeasured(heightPx)
                                }
                            } else {
                                null
                            }
                        )
                    } else {
                        CompareScreen(
                            state = uiState,
                            onSelectIndex = { selectIndex(it) },
                            onScoreChange = { score = it },
                            onSubmit = { sendComparison(score) },
                            onChangeMainScore = { sendMainScoreChange() },
                            onExtraScoreChange = onExtraScoreChange,
                            onSubmitMore = { sendAdditionalCriteria() },
                            onDismissLogin = { dismissLoginDialog() },
                            onRegister = { openRegisterPage() },
                            onLogin = { username, password -> performLogin(username, password) },
                            onNavigateToVideo = { serviceId, url, title ->
                                navigateToVideo(serviceId, url, title)
                            }
                        )
                    }
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
        // currentInfo, currentHistoryEntry and stored scores are small, set only in
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
        scoreStore.clear()
        resetSelectionState()
        refreshSubmittedState()
        refreshUserInfo()
    }

    private fun cancelAuthenticatedWork() {
        authDisposables.clear()
        submitInProgress = false
        changeInProgress = false
        submitMoreInProgress = false
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
        val current = currentInfo
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

        val existingIndex = selectedStreamId?.let { id ->
            filtered.indexOfFirst { it.streamId == id }
        } ?: -1

        val newIndex = when {
            filtered.isEmpty() -> 0
            existingIndex >= 0 -> existingIndex
            else -> 0
        }

        val newSelectedId = filtered.getOrNull(newIndex)?.streamId
        selectedIndex = newIndex
        if (newSelectedId != selectedStreamId) {
            resetSelectionState()
            selectedStreamId = newSelectedId
            refreshSubmittedState()
        }

        suggestionPool.onHistoryUpdated(filtered)
        publishSuggestions()
    }

    private fun selectIndex(newIndex: Int) {
        if (historyEntries.isEmpty()) {
            return
        }
        val clampedIndex = newIndex.coerceIn(0, historyEntries.lastIndex)
        if (clampedIndex == selectedIndex) {
            return
        }
        selectedIndex = clampedIndex
        selectedStreamId = historyEntries[clampedIndex].streamId
        resetSelectionState()
        refreshSubmittedState()
    }

    private fun updateCompactPairSelection(selection: ComparePairSelection?) {
        if (!useCompactUi || compactPairSelection == selection) {
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

    private fun buildCurrentEntry(info: StreamInfo): StreamHistoryEntry {
        val entity = StreamEntity(info)
        val hash = info.url.hashCode().toLong()
        val syntheticId = if (hash == 0L) -1L else -abs(hash)
        return StreamHistoryEntry(
            streamEntity = entity,
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
        recommendationsDisposable?.let { disposables.add(it) }
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
        loginDisposable = TournesolAuthManager.performPasswordLogin(username, password)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { tokenResponse ->
                    TournesolAuthManager.saveUsername(requireContext(), username)
                    TournesolAuthManager.saveAuthState(requireContext(), tokenResponse)
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
                    val message = error.message
                    loginError = if (message.isNullOrBlank()) {
                        getString(R.string.tournesol_login_failed)
                    } else {
                        getString(R.string.tournesol_login_failed_with_message, message)
                    }
                }
            )
        loginDisposable?.let { disposables.add(it) }
    }

    private fun sendComparison(score: Int) {
        if (submitInProgress) {
            return
        }
        val info = currentInfo
        if (info == null) {
            Toast.makeText(
                requireContext(),
                getString(R.string.compare_current_unavailable),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val selectedEntry = historyEntries.getOrNull(selectedIndex)
        if (selectedEntry == null) {
            val messageRes = historyMessageRes ?: R.string.compare_history_unavailable
            Toast.makeText(
                requireContext(),
                getString(messageRes),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val lastUid = CompareRepository.buildTournesolUid(
            selectedEntry.streamEntity.url,
            selectedEntry.streamEntity.serviceId
        )
        val currentUid = CompareRepository.buildTournesolUid(
            info.url,
            info.serviceId
        )

        if (lastUid == null || currentUid == null) {
            Toast.makeText(
                requireContext(),
                getString(R.string.compare_service_not_supported),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val entities = entitiesForLeftRight(currentUid, lastUid)

        runAuthenticated(
            inProgress = ::submitInProgress,
            request = { token ->
                CompareRepository.submitComparison(
                    token,
                    entities.entityA,
                    entities.entityB,
                    score
                )
            },
            onResult = { result ->
                markSubmitted(entities.entityA, entities.entityB)
                storeSubmittedScores(
                    CompareKey(entities.entityA, entities.entityB),
                    mainScore = score
                )
                if (result == SaveResult.CREATED) {
                    TournesolAuthManager.incrementComparisonCount(requireContext())
                    refreshUserInfo()
                }
                Toast.makeText(
                    requireContext(),
                    getString(saveResultMessageRes(result)),
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    private fun resolveCompactCompareKey(): CompareKey? {
        val pair = compactPairSelection
        if (pair == null) {
            val messageRes = historyMessageRes ?: R.string.compare_history_unavailable
            Toast.makeText(
                requireContext(),
                getString(messageRes),
                Toast.LENGTH_SHORT
            ).show()
            return null
        }

        val leftUid = CompareRepository.buildTournesolUid(pair.leftUrl, pair.leftServiceId)
        val rightUid = CompareRepository.buildTournesolUid(pair.rightUrl, pair.rightServiceId)
        if (leftUid == null || rightUid == null) {
            Toast.makeText(
                requireContext(),
                getString(R.string.compare_service_not_supported),
                Toast.LENGTH_SHORT
            ).show()
            return null
        }
        val entities = entitiesForLeftRight(leftUid, rightUid)
        return CompareKey(entities.entityA, entities.entityB)
    }

    private fun sendCompactMainComparison(score: Int, onSuccess: () -> Unit) {
        if (submitInProgress) {
            return
        }
        val key = resolveCompactCompareKey() ?: return
        val alreadySubmitted = scoreStore.isSubmitted(key)

        runAuthenticated(
            inProgress = ::submitInProgress,
            request = { token ->
                if (alreadySubmitted) {
                    CompareRepository.patchComparison(
                        token,
                        key.entityA,
                        key.entityB,
                        listOf(CriteriaScore(COMPACT_MAIN_CRITERION_ID, score))
                    ).map { SaveResult.UPDATED }
                } else {
                    CompareRepository.submitComparison(
                        token,
                        key.entityA,
                        key.entityB,
                        score
                    )
                }
            },
            onResult = { result ->
                markSubmitted(key.entityA, key.entityB)
                storeSubmittedScores(key, mainScore = score)
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

    private fun sendCompactSingleCriterion(
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
                if (alreadySubmitted) {
                    CompareRepository.patchComparison(
                        token,
                        key.entityA,
                        key.entityB,
                        criteriaScores
                    ).map { SaveResult.UPDATED }
                } else {
                    CompareRepository.submitOrUpdateComparison(
                        token,
                        key.entityA,
                        key.entityB,
                        criteriaScores
                    )
                }
            },
            onResult = { result ->
                markSubmitted(key.entityA, key.entityB)
                storeSubmittedScores(
                    key,
                    extraScores = (scoreStore.scoresFor(key)?.extraScores.orEmpty()) +
                        (criterionId to value)
                )
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
            onResult = { messageRes ->
                val merged = (scoreStore.scoresFor(key)?.extraScores.orEmpty()) +
                    criteriaScores.associate { it.criteria to it.score }
                storeSubmittedScores(key, extraScores = merged)
                Toast.makeText(
                    requireContext(),
                    getString(messageRes),
                    Toast.LENGTH_LONG
                ).show()
                onSuccess()
            }
        )
    }

    private fun sendMainScoreChange() {
        if (changeInProgress || !submittedConfirmed) {
            return
        }
        val key = compareKeyForSelection() ?: return
        val newScore = score
        val storedScore = scoreStore.scoresFor(key)?.mainScore
        if (storedScore != null && storedScore == newScore) {
            return
        }

        runAuthenticated(
            inProgress = ::changeInProgress,
            request = { token ->
                CompareRepository.patchComparison(
                    token,
                    key.entityA,
                    key.entityB,
                    listOf(CriteriaScore("largely_recommended", newScore))
                )
            },
            onResult = {
                storeSubmittedScores(key, mainScore = newScore)
                Toast.makeText(
                    requireContext(),
                    getString(R.string.compare_score_updated),
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    private fun sendAdditionalCriteria() {
        if (submitMoreInProgress || !submitted) {
            return
        }
        val info = currentInfo
        if (info == null) {
            Toast.makeText(
                requireContext(),
                getString(R.string.compare_current_unavailable),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val selectedEntry = historyEntries.getOrNull(selectedIndex)
        if (selectedEntry == null) {
            val messageRes = historyMessageRes ?: R.string.compare_history_unavailable
            Toast.makeText(
                requireContext(),
                getString(messageRes),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val lastUid = CompareRepository.buildTournesolUid(
            selectedEntry.streamEntity.url,
            selectedEntry.streamEntity.serviceId
        )
        val currentUid = CompareRepository.buildTournesolUid(
            info.url,
            info.serviceId
        )

        if (lastUid == null || currentUid == null) {
            Toast.makeText(
                requireContext(),
                getString(R.string.compare_service_not_supported),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val newExtraScores = extraScores
        val criteriaScores = EXTRA_CRITERIA.map { criterion ->
            CriteriaScore(criterion.id, newExtraScores[criterion.id] ?: 0)
        }

        val entities = entitiesForLeftRight(currentUid, lastUid)

        runAuthenticated(
            inProgress = ::submitMoreInProgress,
            request = { token ->
                CompareRepository.patchComparison(
                    token,
                    entities.entityA,
                    entities.entityB,
                    criteriaScores
                )
            },
            onResult = { messageRes ->
                storeSubmittedScores(
                    CompareKey(entities.entityA, entities.entityB),
                    extraScores = newExtraScores
                )
                Toast.makeText(
                    requireContext(),
                    getString(messageRes),
                    Toast.LENGTH_LONG
                ).show()
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
        changeInProgress = false
        submitMoreInProgress = false
        score = 0
        extraScores = defaultExtraScores()
        storedMainScore = null
        storedExtraScores = emptyMap()
        extraSubmitted = false
        submittedConfirmed = false
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
        submittedConfirmed = false
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
        checkDisposable?.let { disposables.add(it) }
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
            submittedConfirmed = false
        }
    }

    private fun compareKeyForSelection(): CompareKey? {
        if (useCompactUi) {
            val pair = compactPairSelection ?: return null
            val leftUid = CompareRepository.buildTournesolUid(
                pair.leftUrl,
                pair.leftServiceId
            ) ?: return null
            val rightUid = CompareRepository.buildTournesolUid(
                pair.rightUrl,
                pair.rightServiceId
            ) ?: return null
            val entities = entitiesForLeftRight(leftUid, rightUid)
            return CompareKey(entities.entityA, entities.entityB)
        }
        val info = currentInfo ?: return null
        val selectedEntry = historyEntries.getOrNull(selectedIndex) ?: return null
        val lastUid = CompareRepository.buildTournesolUid(
            selectedEntry.streamEntity.url,
            selectedEntry.streamEntity.serviceId
        ) ?: return null
        val currentUid = CompareRepository.buildTournesolUid(
            info.url,
            info.serviceId
        ) ?: return null
        val entities = entitiesForLeftRight(currentUid, lastUid)
        return CompareKey(entities.entityA, entities.entityB)
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
            submittedConfirmed = true
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

    fun setCompactPopupVisibleState(visible: Boolean) {
        compactPopupVisible = visible
    }

    private data class CompactCriteriaPayload(
        val criteriaScores: List<CriteriaScore>,
        val mainScore: Int?,
        val extraScores: Map<String, Int>
    )

    private fun buildCompactCriteriaPayload(
        selectedIds: Set<String>
    ): CompactCriteriaPayload? {
        if (selectedIds.isEmpty()) {
            return null
        }
        val criteriaScores = ArrayList<CriteriaScore>(selectedIds.size)
        val extras = LinkedHashMap<String, Int>()
        var mainScore: Int? = null
        selectedIds.forEach { criteria ->
            val value = if (criteria == "largely_recommended") {
                mainScore = score
                score
            } else {
                val extraValue = extraScores[criteria] ?: 0
                extras[criteria] = extraValue
                extraValue
            }
            criteriaScores.add(CriteriaScore(criteria, value))
        }
        if (criteriaScores.isEmpty()) {
            return null
        }
        return CompactCriteriaPayload(
            criteriaScores = criteriaScores,
            mainScore = mainScore,
            extraScores = extras
        )
    }

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

        private const val KEY_COMPACT_UI = "compare_compact_ui"
        private const val KEY_EMBED_IN_DETAIL = "compare_embed_in_detail"
        private const val KEY_EXTERNAL_SCROLL = "compare_external_scroll"
        private const val COMPARISONS_USERNAME = "me"
        private const val COMPARISONS_LIMIT = 20

        @JvmStatic
        @JvmOverloads
        fun getInstance(
            info: StreamInfo?,
            useCompactUi: Boolean = false,
            embedInDetail: Boolean = false,
            externalScroll: Boolean = false
        ): CompareFragment {
            return CompareFragment().apply {
                arguments = bundleOf(
                    KEY_INFO to info,
                    KEY_COMPACT_UI to useCompactUi,
                    KEY_EMBED_IN_DETAIL to embedInDetail,
                    KEY_EXTERNAL_SCROLL to externalScroll
                )
            }
        }
    }
}
