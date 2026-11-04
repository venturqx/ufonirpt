package org.schabi.newpipe.tournesol.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.tournesol.TournesolLoginFailure

class TournesolLoginFailureMessageTest {
    @Test
    fun `each failure maps to its own string resource`() {
        assertEquals(
            R.string.tournesol_login_invalid_credentials,
            TournesolLoginFailure.INVALID_CREDENTIALS.messageRes
        )
        assertEquals(R.string.network_error, TournesolLoginFailure.NETWORK.messageRes)
        assertEquals(R.string.tournesol_login_failed, TournesolLoginFailure.UNKNOWN.messageRes)
    }
}
