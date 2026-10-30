package org.schabi.newpipe.util

import android.content.Context
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
}
