package org.schabi.newpipe.util

import androidx.annotation.StringRes
import java.io.IOException
import org.schabi.newpipe.R

internal enum class TournesolLoginFailure(@StringRes val messageRes: Int) {
    INVALID_CREDENTIALS(R.string.tournesol_login_invalid_credentials),
    NETWORK(R.string.network_error),
    UNKNOWN(R.string.tournesol_login_failed);

    companion object {
        fun from(throwable: Throwable): TournesolLoginFailure = when (throwable) {
            is TokenEndpointException -> if (throwable.isInvalidGrant) INVALID_CREDENTIALS else UNKNOWN
            is IOException -> NETWORK
            else -> UNKNOWN
        }
    }
}
