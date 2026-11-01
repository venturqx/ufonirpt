package org.schabi.newpipe.tournesol.ui

import android.app.Application
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import org.schabi.newpipe.App
import org.schabi.newpipe.tournesol.CompareScoreStore
import org.schabi.newpipe.tournesol.TournesolAuthManager
import org.schabi.newpipe.tournesol.TournesolProfile

internal class CompareViewModel(
    application: Application,
    video: TournesolVideoRef?
) : AndroidViewModel(application) {
    val state = CompareUiState()
    private val host = object : CompareHost {
        override val mainThread: Scheduler
            get() = AndroidSchedulers.mainThread()

        override fun accessToken(): Maybe<String> = TournesolAuthManager.getValidAccessToken(application)

        override fun login(username: String, password: String): Completable = TournesolAuthManager.login(application, username, password)

        override fun loadProfile(): Single<TournesolProfile> = TournesolAuthManager.loadProfile(application)

        override fun incrementComparisonCount() = TournesolAuthManager.incrementComparisonCount(application)

        override fun saveComparisonCount(count: Int) = TournesolAuthManager.saveComparisonCount(application, count)

        override fun showToast(toast: CompareToast) {
            val ctx = ContextCompat.getContextForLanguage(application)
            Toast.makeText(ctx, toast.message.resolve(ctx), toast.length).show()
        }

        override fun logFailure(throwable: Throwable) = logCompareFailure(throwable)
    }
    val userInfo = CompareUserInfo(state, host)
    val controller = CompareController(
        state = state,
        video = video,
        store = CompareScoreStore.shared(application),
        userInfo = userInfo,
        host = host
    )

    override fun onCleared() {
        controller.onViewDestroyed()
    }

    companion object {
        fun factory(video: TournesolVideoRef?) = viewModelFactory {
            initializer {
                CompareViewModel(App.instance, video)
            }
        }
    }
}
