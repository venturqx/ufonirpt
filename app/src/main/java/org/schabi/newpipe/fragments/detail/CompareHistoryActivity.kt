package org.schabi.newpipe.fragments.detail

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
import org.schabi.newpipe.fragments.detail.compare.CompareComparisonsFullScreen
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.util.TournesolAuthManager

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
                    onDismiss = { finish() }
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
            .switchIfEmpty(
                io.reactivex.rxjava3.core.Maybe.error(RuntimeException("login_required"))
            )
            .flatMapSingle { token ->
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
                    if (result.comparisons.isEmpty()) {
                        error = getString(R.string.compare_no_comparisons_available)
                    }
                },
                { throwable ->
                    loading = false
                    if (throwable.message == "login_required") {
                        error = getString(R.string.compare_login_required)
                        return@subscribe
                    }
                    val message = throwable.message
                    error = if (message.isNullOrBlank()) {
                        getString(R.string.compare_failed)
                    } else {
                        getString(R.string.compare_failed_with_message, message)
                    }
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
