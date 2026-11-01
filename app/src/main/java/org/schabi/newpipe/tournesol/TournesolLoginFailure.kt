package org.schabi.newpipe.tournesol

import java.io.IOException

internal enum class TournesolLoginFailure {
    INVALID_CREDENTIALS,
    NETWORK,
    UNKNOWN;

    companion object {
        fun from(throwable: Throwable): TournesolLoginFailure = when (throwable) {
            is TokenEndpointException -> if (throwable.isInvalidGrant) INVALID_CREDENTIALS else UNKNOWN
            is IOException -> NETWORK
            else -> UNKNOWN
        }
    }
}
