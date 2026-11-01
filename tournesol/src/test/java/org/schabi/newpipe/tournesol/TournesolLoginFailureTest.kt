package org.schabi.newpipe.tournesol

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Test

class TournesolLoginFailureTest {
    private fun rejected(status: Int, oauthError: String?) = TokenEndpointException(status, oauthError, "Login failed: $status secret-body")

    @Test
    fun `rejected password is invalid credentials`() {
        assertEquals(
            TournesolLoginFailure.INVALID_CREDENTIALS,
            TournesolLoginFailure.from(rejected(400, "invalid_grant"))
        )
    }

    @Test
    fun `invalid_grant outside 4xx is not invalid credentials`() {
        assertEquals(
            TournesolLoginFailure.UNKNOWN,
            TournesolLoginFailure.from(rejected(500, "invalid_grant"))
        )
    }

    @Test
    fun `token endpoint rejection is unknown even though it is an IOException`() {
        assertEquals(
            TournesolLoginFailure.UNKNOWN,
            TournesolLoginFailure.from(rejected(401, "invalid_client"))
        )
        assertEquals(
            TournesolLoginFailure.UNKNOWN,
            TournesolLoginFailure.from(rejected(503, null))
        )
    }

    @Test
    fun `connection failures are network failures`() {
        assertEquals(
            TournesolLoginFailure.NETWORK,
            TournesolLoginFailure.from(UnknownHostException("api.tournesol.app"))
        )
        assertEquals(
            TournesolLoginFailure.NETWORK,
            TournesolLoginFailure.from(SocketTimeoutException())
        )
        assertEquals(TournesolLoginFailure.NETWORK, TournesolLoginFailure.from(IOException()))
    }

    @Test
    fun `any other throwable is unknown`() {
        assertEquals(
            TournesolLoginFailure.UNKNOWN,
            TournesolLoginFailure.from(IllegalStateException("boom"))
        )
    }
}
