package org.schabi.newpipe.tournesol.ui

import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import org.schabi.newpipe.tournesol.TournesolProfile
import org.schabi.newpipe.tournesol.orMissingToken

internal data class CompareToast(val message: CompareMessage, val length: Int)

internal interface CompareHost {
    val mainThread: Scheduler

    fun accessToken(): Maybe<String>

    fun login(username: String, password: String): Completable

    fun loadProfile(): Single<TournesolProfile>?

    fun incrementComparisonCount()

    fun saveComparisonCount(count: Int)

    fun showToast(toast: CompareToast)
}

internal fun <T : Any> CompareHost.withToken(request: (String) -> Single<T>): Single<T> = accessToken()
    .orMissingToken()
    .flatMap { token -> request(token) }
    .observeOn(mainThread)

internal fun <T : Any> CompareHost.ifLoggedIn(request: (String) -> Single<T>): Maybe<T> = accessToken()
    .flatMapSingle { token -> request(token) }
    .observeOn(mainThread)
