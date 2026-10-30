package org.schabi.newpipe.util

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TournesolSecretExtractorTest {

    private val baseUrl = "https://tournesol.app/"
    private val clientId = "a".repeat(40)
    private val clientSecret = "b".repeat(128)
    private val credentialsJs = "client_id:\"$clientId\",client_secret:\"$clientSecret\""
    private val requested = mutableListOf<String>()

    private fun asset(name: String) = "${baseUrl}assets/$name.js"

    private fun html(vararg bundles: String) = bundles.joinToString("") { "<script src=\"/assets/$it.js\"></script>" }

    private fun fakeFetch(pages: Map<String, String>): (String) -> String = { url ->
        requested += url
        pages[url] ?: throw IOException("Failed to fetch $url")
    }

    @Test
    fun `discover finds credentials in an initial bundle and stops there`() {
        val pages = mapOf(
            baseUrl to html("first-a", "second-b"),
            asset("first-a") to credentialsJs,
            asset("second-b") to ""
        )

        assertEquals(
            TournesolClientCredentials(clientId, clientSecret),
            TournesolSecretDiscovery(baseUrl, fakeFetch(pages)).discover()
        )
        assertEquals(listOf(baseUrl, asset("first-a")), requested)
    }

    @Test
    fun `discover finds credentials in a chunk discovered from a bundle`() {
        val pages = mapOf(
            baseUrl to html("main-a"),
            asset("main-a") to "import(\"assets/chunk-1.js\")",
            asset("chunk-1") to credentialsJs
        )

        assertEquals(
            TournesolClientCredentials(clientId, clientSecret),
            TournesolSecretDiscovery(baseUrl, fakeFetch(pages)).discover()
        )
        assertEquals(listOf(baseUrl, asset("main-a"), asset("chunk-1")), requested)
    }

    @Test
    fun `discover skips only the bundle that fails to fetch`() {
        val pages = mapOf(
            baseUrl to html("missing-a", "main-b"),
            asset("main-b") to credentialsJs
        )
        val skipped = mutableListOf<String>()

        val credentials = TournesolSecretDiscovery(
            baseUrl,
            fakeFetch(pages),
            onSkipped = { url, _ -> skipped += url }
        ).discover()

        assertEquals(TournesolClientCredentials(clientId, clientSecret), credentials)
        assertEquals(listOf(asset("missing-a")), skipped)
    }

    @Test
    fun `discover returns null when nothing is found`() {
        val pages = mapOf(
            baseUrl to html("main-a"),
            asset("main-a") to "import(\"assets/chunk-1.js\")",
            asset("chunk-1") to ""
        )

        assertNull(TournesolSecretDiscovery(baseUrl, fakeFetch(pages)).discover())
        assertEquals(listOf(baseUrl, asset("main-a"), asset("chunk-1")), requested)
    }

    @Test
    fun `discover stops fetching once cancelled`() {
        val pages = mapOf(
            baseUrl to html("first-a", "second-b"),
            asset("first-a") to "import(\"assets/chunk-1.js\")",
            asset("second-b") to credentialsJs,
            asset("chunk-1") to credentialsJs
        )

        val credentials = TournesolSecretDiscovery(
            baseUrl,
            fakeFetch(pages),
            isCancelled = { asset("first-a") in requested }
        ).discover()

        assertNull(credentials)
        assertEquals(listOf(baseUrl, asset("first-a")), requested)
    }

    @Test
    fun `discover propagates a home page failure`() {
        assertThrows(IOException::class.java) {
            TournesolSecretDiscovery(baseUrl, fakeFetch(emptyMap())).discover()
        }
        assertEquals(listOf(baseUrl), requested)
    }

    @Test
    fun `extractSecrets rescans past cached secrets and updates the cache`() {
        val freshId = "c".repeat(40)
        val freshSecret = "d".repeat(128)
        val stalePages = mapOf(baseUrl to html("main-a"), asset("main-a") to credentialsJs)
        val freshPages = mapOf(
            baseUrl to html("main-a"),
            asset("main-a") to "client_id:\"$freshId\",client_secret:\"$freshSecret\""
        )

        TournesolSecretExtractor(fakeFetch(stalePages)).extractSecrets().blockingGet()
        assertEquals(Pair(clientId, clientSecret), TournesolSecretExtractor.getDefaultOrCachedSecrets())

        val refreshed = TournesolSecretExtractor(fakeFetch(freshPages)).extractSecrets().blockingGet()

        assertEquals(Pair(freshId, freshSecret), refreshed)
        assertEquals(refreshed, TournesolSecretExtractor.getDefaultOrCachedSecrets())
    }
}
