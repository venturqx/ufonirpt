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
