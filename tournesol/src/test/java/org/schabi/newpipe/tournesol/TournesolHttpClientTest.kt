package org.schabi.newpipe.tournesol

import java.io.IOException
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TournesolHttpClientTest {
    private val requested = mutableListOf<String>()

    private fun failEveryRequest() {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requested += chain.request().url.toString()
            throw IOException("stopped by test")
        }.build()
        TournesolHttpClient.provider = { client }
    }

    @After
    fun restoreHttpClient() {
        TournesolHttpClient.provider = { null }
    }

    @Test
    fun `token requests go through the supplied client`() {
        failEveryRequest()

        TournesolAuthManager.performPasswordLogin("user", "password").test()
            .assertError(IOException::class.java)

        assertEquals(listOf("https://api.tournesol.app/o/token/"), requested)
    }

    @Test
    fun `secret scan fetches go through the supplied client`() {
        failEveryRequest()
        val fetch = TournesolSecretExtractor.httpFetch()

        assertThrows(IOException::class.java) { fetch("https://tournesol.app/") }

        assertEquals(listOf("https://tournesol.app/"), requested)
    }

    @Test
    fun `secret scan builds its own client when none is supplied`() {
        TournesolHttpClient.provider = { null }

        assertNotNull(TournesolSecretExtractor.httpFetch())
    }
}
