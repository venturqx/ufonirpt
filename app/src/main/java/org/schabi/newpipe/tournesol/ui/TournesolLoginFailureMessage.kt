package org.schabi.newpipe.tournesol.ui

import androidx.annotation.StringRes
import org.schabi.newpipe.R
import org.schabi.newpipe.tournesol.TournesolLoginFailure

@get:StringRes
internal val TournesolLoginFailure.messageRes: Int
    get() = when (this) {
        TournesolLoginFailure.INVALID_CREDENTIALS -> R.string.tournesol_login_invalid_credentials
        TournesolLoginFailure.NETWORK -> R.string.network_error
        TournesolLoginFailure.UNKNOWN -> R.string.tournesol_login_failed
    }
