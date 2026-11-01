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
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import org.schabi.newpipe.R
import org.schabi.newpipe.fragments.detail.VideoDetailFragment
import org.schabi.newpipe.ktx.serializable
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.tournesol.CompareScoreStore
import org.schabi.newpipe.tournesol.REGISTER_URL
import org.schabi.newpipe.tournesol.TournesolAuthManager
import org.schabi.newpipe.tournesol.TournesolProfile
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.util.NavigationHelper

class CompareFragment : Fragment() {
    private var currentVideo: TournesolVideoRef? = null
    private var embedInDetail = false
    private var externalScroll = false
    private val uiState = CompareUiState()
    private val host = object : CompareHost {
        override val mainThread: Scheduler
            get() = AndroidSchedulers.mainThread()

        override fun accessToken(): Maybe<String> = TournesolAuthManager.getValidAccessToken(requireContext())

        override fun login(username: String, password: String): Completable = TournesolAuthManager.login(requireContext(), username, password)

        override fun loadProfile(): Single<TournesolProfile>? = context?.let { TournesolAuthManager.loadProfile(it) }

        override fun incrementComparisonCount() = TournesolAuthManager.incrementComparisonCount(requireContext())

        override fun saveComparisonCount(count: Int) = TournesolAuthManager.saveComparisonCount(requireContext(), count)

        override fun showToast(toast: CompareToast) {
            val ctx = requireContext()
            Toast.makeText(ctx, toast.message.resolve(ctx), toast.length).show()
        }
    }
    private val userInfo = CompareUserInfo(uiState, host)
    private lateinit var controller: CompareController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentVideo = arguments?.serializable<TournesolVideoRef>(KEY_VIDEO)
        embedInDetail = arguments?.getBoolean(KEY_EMBED_IN_DETAIL) == true
        externalScroll = arguments?.getBoolean(KEY_EXTERNAL_SCROLL) == true
        controller = CompareController(
            state = uiState,
            video = currentVideo,
            store = CompareScoreStore.shared(requireContext()),
            userInfo = userInfo,
            host = host
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val historyRecordManager = HistoryRecordManager(requireContext())
        controller.start(
            signedOut = TournesolAuthManager.signedOut(),
            history = if (isHistoryEnabled()) historyRecordManager.getStreamHistorySortedById() else null
        )
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
                        uiState.updateExtraScore(criteria, value)
                    }
                    CompareCompactScreen(
                        state = uiState,
                        onScoreChange = { uiState.score = it },
                        onExtraScoreChange = onExtraScoreChange,
                        onSubmitCriterion = { criterionId, value, onSuccess ->
                            controller.submitter.submitCriterion(criterionId, value, onSuccess)
                        },
                        onSubmitExtrasBatch = { criteriaScores, onSuccess ->
                            controller.submitter.submitExtras(criteriaScores, onSuccess)
                        },
                        onPairSelectionChange = { selection ->
                            controller.submitter.selectPair(selection)
                        },
                        onDismissRecommendations = { controller.recommendations.dismiss() },
                        onDismissLogin = { controller.login.dismiss() },
                        onShowLogin = { controller.login.show() },
                        onRegister = { openRegisterPage() },
                        onLogin = { username, password -> controller.login.submit(username, password) },
                        onNavigateToVideo = { serviceId, url, title ->
                            navigateToVideo(serviceId, url, title)
                        },
                        onRandomizeLeft = { controller.suggestions.randomize(SuggestionSlot.LEFT) },
                        onRandomizeRight = { controller.suggestions.randomize(SuggestionSlot.RIGHT) },
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
        controller.onViewDestroyed()
        super.onDestroyView()
    }

    private fun isHistoryEnabled(): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val key = getString(R.string.enable_watch_history_key)
        return prefs.getBoolean(key, false)
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
        controller.recommendations.open()
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

    fun refreshUserInfo() {
        userInfo.refresh()
    }

    companion object {
        @JvmStatic
        var pendingOpenComparisons = false

        private const val KEY_VIDEO = "compare_video"
        private const val KEY_EMBED_IN_DETAIL = "compare_embed_in_detail"
        private const val KEY_EXTERNAL_SCROLL = "compare_external_scroll"

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
