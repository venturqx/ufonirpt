package org.schabi.newpipe.util

import android.content.Context
import io.reactivex.rxjava3.core.Maybe
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`

class TournesolAuthManagerTest {
    @Test
    fun `getValidAccessToken does no storage work until subscribed`() {
        val context = mock(Context::class.java)
        `when`(context.applicationContext).thenReturn(context)

        TournesolAuthManager.getValidAccessToken(context)

        verify(context).applicationContext
        verifyNoMoreInteractions(context)
    }

    @Test
    fun `orMissingToken passes through a token`() {
        Maybe.just("token").orMissingToken().test().assertResult("token")
    }

    @Test
    fun `orMissingToken maps empty to MissingTokenException`() {
        Maybe.empty<String>().orMissingToken().test()
            .assertNoValues()
            .assertError(MissingTokenException::class.java)
    }

    @Test
    fun `orMissingToken passes an upstream error through unchanged`() {
        val failure = IllegalStateException("boom")

        Maybe.error<String>(failure).orMissingToken().test()
            .assertNoValues()
            .assertError(failure)
    }
}
