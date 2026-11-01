package org.schabi.newpipe.tournesol.ui

import android.widget.Toast
import io.reactivex.rxjava3.disposables.Disposable
import org.schabi.newpipe.R
import org.schabi.newpipe.tournesol.TournesolLoginFailure

internal class CompareLoginFlow(
    private val state: CompareUiState,
    private val host: CompareHost,
    private val userInfo: CompareUserInfo
) {
    private var request: Disposable? = null

    fun show() {
        state.login = CompareLoginState.Open()
    }

    fun dismiss() {
        state.login = CompareLoginState.Hidden
        cancelRequest()
    }

    fun submit(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            state.login = CompareLoginState.Open(CompareMessage(R.string.tournesol_login_missing_fields))
            return
        }
        state.login = CompareLoginState.Submitting
        request?.dispose()
        request = host.login(username, password)
            .observeOn(host.mainThread)
            .subscribe(
                {
                    state.login = CompareLoginState.Hidden
                    userInfo.refresh()
                    host.showToast(
                        CompareToast(CompareMessage(R.string.tournesol_login_success), Toast.LENGTH_SHORT)
                    )
                },
                { error ->
                    state.login = CompareLoginState.Open(
                        CompareMessage(TournesolLoginFailure.from(error).messageRes)
                    )
                }
            )
    }

    fun cancel() {
        cancelRequest()
        if (state.login == CompareLoginState.Submitting) {
            state.login = CompareLoginState.Open()
        }
    }

    private fun cancelRequest() {
        request?.dispose()
        request = null
    }
}
