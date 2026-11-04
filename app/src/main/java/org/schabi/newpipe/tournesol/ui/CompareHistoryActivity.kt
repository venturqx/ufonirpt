package org.schabi.newpipe.tournesol.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import org.schabi.newpipe.R
import org.schabi.newpipe.tournesol.CompareRecommendationItem
import org.schabi.newpipe.tournesol.CompareRepository
import org.schabi.newpipe.tournesol.CompareSubmitFailure
import org.schabi.newpipe.tournesol.MissingTokenException
import org.schabi.newpipe.tournesol.TournesolAuthManager
import org.schabi.newpipe.tournesol.orMissingToken
import org.schabi.newpipe.tournesol.suggestionServiceId
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.util.NavigationHelper

class CompareHistoryActivity : AppCompatActivity() {

    private var recommendations by mutableStateOf<List<CompareRecommendationItem>>(emptyList())
    private var totalCount by mutableStateOf<Int?>(null)
    private var loading by mutableStateOf(true)
    private var error by mutableStateOf<String?>(null)
    private var disposable: Disposable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadComparisons()
        setContent {
            AppTheme {
                CompareComparisonsFullScreen(
                    recommendations = recommendations,
                    totalCount = totalCount,
                    isLoading = loading,
                    errorMessage = error,
                    onRetry = { loadComparisons() },
                    onDismiss = { finish() },
                    onVideoClick = { video ->
                        val serviceId = suggestionServiceId(video)
                        val url = video.videoUrl
                        if (serviceId != null && url != null) {
                            NavigationHelper.openVideoDetail(this, serviceId, url, video.title, null, false)
                        }
                    }
                )
            }
        }
    }

    private fun loadComparisons() {
        loading = true
        error = null
        recommendations = emptyList()
        totalCount = null
        disposable?.dispose()
        disposable = TournesolAuthManager.getValidAccessToken(this)
            .orMissingToken()
            .flatMap { token ->
                CompareRepository.fetchUserComparisons(
                    token = token,
                    username = "me",
                    limit = 20
                )
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { result ->
                    loading = false
                    recommendations = result.comparisons
                    totalCount = result.totalCount
                    result.totalCount?.let {
                        TournesolAuthManager.saveComparisonCount(this, it)
                    }
                },
                { throwable ->
                    loading = false
                    if (throwable is MissingTokenException) {
                        error = getString(R.string.compare_login_required)
                        return@subscribe
                    }
                    logCompareFailure(throwable)
                    error = compareFailedMessage(CompareSubmitFailure.from(throwable)).resolve(this)
                }
            )
    }

    override fun onDestroy() {
        disposable?.dispose()
        super.onDestroy()
    }

    companion object {
        @JvmStatic
        fun start(context: Context) {
            context.startActivity(Intent(context, CompareHistoryActivity::class.java))
        }
    }
}
