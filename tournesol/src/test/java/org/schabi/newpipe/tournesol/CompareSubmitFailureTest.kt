package org.schabi.newpipe.tournesol

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Test

class CompareSubmitFailureTest {

    @Test
    fun `missing token requires login`() {
        assertEquals(
            CompareSubmitFailure.LoginRequired,
            CompareSubmitFailure.from(MissingTokenException())
        )
    }

    @Test
    fun `wrapped missing token is a plain failure`() {
        assertEquals(
            CompareSubmitFailure.Failed,
            CompareSubmitFailure.from(RuntimeException("wrapper", MissingTokenException()))
        )
    }

    @Test
    fun `api 401 is an expired session`() {
        assertEquals(
            CompareSubmitFailure.SessionExpired,
            CompareSubmitFailure.from(TournesolApiException(401, "revoked"))
        )
    }

    @Test
    fun `a rejected refresh token is an expired session`() {
        assertEquals(
            CompareSubmitFailure.SessionExpired,
            CompareSubmitFailure.from(SessionExpiredException())
        )
    }

    @Test
    fun `wrapped api 401 is a plain failure`() {
        assertEquals(
            CompareSubmitFailure.Failed,
            CompareSubmitFailure.from(
                RuntimeException("wrapper", TournesolApiException(401, "revoked"))
            )
        )
    }

    @Test
    fun `other api errors are plain failures, not network errors`() {
        assertEquals(
            CompareSubmitFailure.Failed,
            CompareSubmitFailure.from(TournesolApiException(403, "forbidden"))
        )
        assertEquals(
            CompareSubmitFailure.Failed,
            CompareSubmitFailure.from(TournesolApiException(500, "boom"))
        )
    }

    @Test
    fun `token endpoint rejections are plain failures, not network errors`() {
        assertEquals(
            CompareSubmitFailure.Failed,
            CompareSubmitFailure.from(TokenEndpointException(500, null, "Token refresh failed: 500"))
        )
    }

    @Test
    fun `connection errors are network failures`() {
        listOf(
            UnknownHostException("Unable to resolve host \"api.tournesol.app\""),
            SocketTimeoutException("timeout"),
            IOException()
        ).forEach { error ->
            assertEquals(CompareSubmitFailure.Network, CompareSubmitFailure.from(error))
        }
    }

    @Test
    fun `other exceptions are plain failures`() {
        assertEquals(
            CompareSubmitFailure.Failed,
            CompareSubmitFailure.from(RuntimeException("boom"))
        )
    }
}
