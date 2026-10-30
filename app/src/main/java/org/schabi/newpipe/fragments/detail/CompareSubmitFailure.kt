package org.schabi.newpipe.fragments.detail

import org.schabi.newpipe.util.MissingTokenException

internal sealed interface CompareSubmitFailure {
    data object LoginRequired : CompareSubmitFailure

    data class Failed(val message: String?) : CompareSubmitFailure

    companion object {
        fun from(throwable: Throwable): CompareSubmitFailure = when {
            throwable is MissingTokenException -> LoginRequired
            throwable is TournesolApiException && throwable.httpCode == 401 -> LoginRequired
            else -> Failed(throwable.message?.takeUnless { it.isBlank() })
        }
    }
}
