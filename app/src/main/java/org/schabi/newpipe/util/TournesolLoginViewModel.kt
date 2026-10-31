package org.schabi.newpipe.util

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers

internal sealed interface TournesolLoginState {
    data object Idle : TournesolLoginState

    data object InProgress : TournesolLoginState

    data object Succeeded : TournesolLoginState

    data class Failed(val failure: TournesolLoginFailure) : TournesolLoginState
}

class TournesolLoginViewModel(application: Application) : AndroidViewModel(application) {

    internal var state by mutableStateOf<TournesolLoginState>(TournesolLoginState.Idle)
        private set
    private var loginDisposable: Disposable? = null

    fun login(username: String, password: String) {
        state = TournesolLoginState.InProgress
        loginDisposable?.dispose()
        loginDisposable = TournesolAuthManager.performPasswordLogin(username, password)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { tokenResponse ->
                    val context = getApplication<Application>()
                    TournesolAuthManager.saveUsername(context, username)
                    TournesolAuthManager.saveAuthState(context, tokenResponse)
                    state = TournesolLoginState.Succeeded
                },
                { error ->
                    state = TournesolLoginState.Failed(TournesolLoginFailure.from(error))
                }
            )
    }

    override fun onCleared() {
        loginDisposable?.dispose()
    }
}
