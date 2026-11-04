package org.schabi.newpipe.tournesol

import java.io.IOException

sealed interface CompareSubmitFailure {
    data object LoginRequired : CompareSubmitFailure

    data object SessionExpired : CompareSubmitFailure

    data object Network : CompareSubmitFailure

    data object Failed : CompareSubmitFailure

    companion object {
        fun from(throwable: Throwable): CompareSubmitFailure = when (throwable) {
            is SessionExpiredException -> SessionExpired
            is MissingTokenException -> LoginRequired
            is TournesolApiException -> if (throwable.httpCode == 401) SessionExpired else Failed
            is TokenEndpointException -> Failed
            is IOException -> Network
            else -> Failed
        }
    }
}
