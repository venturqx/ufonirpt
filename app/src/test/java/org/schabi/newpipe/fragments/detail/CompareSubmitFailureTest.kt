package org.schabi.newpipe.fragments.detail

import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.util.MissingTokenException

class CompareSubmitFailureTest {

    @Test
    fun `missing token requires login`() {
        assertEquals(
            CompareSubmitFailure.LoginRequired,
            CompareSubmitFailure.from(MissingTokenException())
        )
    }

    @Test
    fun `wrapped missing token is a plain failure carrying the wrapper message`() {
        assertEquals(
            CompareSubmitFailure.Failed("wrapper"),
            CompareSubmitFailure.from(RuntimeException("wrapper", MissingTokenException()))
        )
    }

    @Test
    fun `api 401 requires login`() {
        assertEquals(
            CompareSubmitFailure.LoginRequired,
            CompareSubmitFailure.from(TournesolApiException(401, "revoked"))
        )
    }

    @Test
    fun `wrapped api 401 is a plain failure carrying the wrapper message`() {
        assertEquals(
            CompareSubmitFailure.Failed("wrapper"),
            CompareSubmitFailure.from(
                RuntimeException("wrapper", TournesolApiException(401, "revoked"))
            )
        )
    }

    @Test
    fun `other api errors fail with the short message`() {
        assertEquals(
            CompareSubmitFailure.Failed("HTTP 403"),
            CompareSubmitFailure.from(TournesolApiException(403, "forbidden"))
        )
        assertEquals(
            CompareSubmitFailure.Failed("HTTP 500"),
            CompareSubmitFailure.from(TournesolApiException(500, "boom"))
        )
    }

    @Test
    fun `html error page never reaches the failure message`() {
        assertEquals(
            CompareSubmitFailure.Failed("HTTP 502"),
            CompareSubmitFailure.from(
                TournesolApiException(502, "<html><body>Bad Gateway</body></html>")
            )
        )
    }

    @Test
    fun `null message has no message`() {
        assertEquals(
            CompareSubmitFailure.Failed(null),
            CompareSubmitFailure.from(RuntimeException())
        )
    }

    @Test
    fun `empty and blank messages have no message`() {
        listOf("", "   ", "\n\t").forEach { blank ->
            assertEquals(
                CompareSubmitFailure.Failed(null),
                CompareSubmitFailure.from(RuntimeException(blank))
            )
        }
    }

    @Test
    fun `real message is kept verbatim`() {
        assertEquals(
            CompareSubmitFailure.Failed(" HTTP 500 "),
            CompareSubmitFailure.from(RuntimeException(" HTTP 500 "))
        )
    }
}
