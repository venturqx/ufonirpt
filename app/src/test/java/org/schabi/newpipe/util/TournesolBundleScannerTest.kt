package org.schabi.newpipe.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TournesolBundleScannerTest {

    private val baseUrl = "https://tournesol.app/"
    private val clientId = "a".repeat(40)
    private val clientSecret = "b".repeat(128)

    @Test
    fun `bundleUrls extracts href then src bundles`() {
        val html = """
            <script src="/assets/main-def.js"></script>
            <link rel="modulepreload" href="/assets/vendor-abc.js">
        """.trimIndent()

        assertEquals(
            listOf("${baseUrl}assets/vendor-abc.js", "${baseUrl}assets/main-def.js"),
            TournesolBundleScanner.bundleUrls(html, baseUrl)
        )
    }

    @Test
    fun `bundleUrls dedupes a bundle declared by both href and src`() {
        val html = """
            <link rel="modulepreload" href="/assets/main-def.js">
            <script src="/assets/main-def.js"></script>
        """.trimIndent()

        assertEquals(
            listOf("${baseUrl}assets/main-def.js"),
            TournesolBundleScanner.bundleUrls(html, baseUrl)
        )
    }

    @Test
    fun `bundleUrls ignores files outside assets and non js files`() {
        val html = """
            <script src="/static/main-def.js"></script>
            <link href="/assets/style-abc.css">
        """.trimIndent()

        assertTrue(TournesolBundleScanner.bundleUrls(html, baseUrl).isEmpty())
    }

    @Test
    fun `bundleUrls does not span from a css link to a script on one line`() {
        val html = """<link href="/assets/style-abc.css"><script src="/assets/main-def.js"></script>"""

        assertEquals(
            listOf("${baseUrl}assets/main-def.js"),
            TournesolBundleScanner.bundleUrls(html, baseUrl)
        )
    }

    @Test
    fun `bundleUrls does not span across two href attributes on one line`() {
        val html = """<link href="/assets/style-abc.css"><link href="/assets/vendor-def.js">"""

        assertEquals(
            listOf("${baseUrl}assets/vendor-def.js"),
            TournesolBundleScanner.bundleUrls(html, baseUrl)
        )
    }

    @Test
    fun `bundleUrls does not span across two src attributes on one line`() {
        val html = """<img src="/assets/logo-abc.png"><script src="/assets/main-def.js"></script>"""

        assertEquals(
            listOf("${baseUrl}assets/main-def.js"),
            TournesolBundleScanner.bundleUrls(html, baseUrl)
        )
    }

    @Test
    fun `bundleUrls moves components bundles first and keeps relative order`() {
        val html = """
            <script src="/assets/a-1.js"></script>
            <script src="/assets/components-x-2.js"></script>
            <script src="/assets/b-3.js"></script>
            <script src="/assets/components-y-4.js"></script>
        """.trimIndent()

        assertEquals(
            listOf(
                "${baseUrl}assets/components-x-2.js",
                "${baseUrl}assets/components-y-4.js",
                "${baseUrl}assets/a-1.js",
                "${baseUrl}assets/b-3.js"
            ),
            TournesolBundleScanner.bundleUrls(html, baseUrl)
        )
    }

    @Test
    fun `chunkUrls accepts every quote style`() {
        val js = "import(\"assets/one.js\");import('assets/two.js');import(`assets/three.js`)"

        assertEquals(
            listOf(
                "${baseUrl}assets/one.js",
                "${baseUrl}assets/two.js",
                "${baseUrl}assets/three.js"
            ),
            TournesolBundleScanner.chunkUrls(js, baseUrl, emptyList())
        )
    }

    @Test
    fun `chunkUrls excludes known urls and dedupes`() {
        val js = "\"assets/known.js\" \"assets/new.js\" \"assets/new.js\""

        assertEquals(
            listOf("${baseUrl}assets/new.js"),
            TournesolBundleScanner.chunkUrls(js, baseUrl, listOf("${baseUrl}assets/known.js"))
        )
    }

    @Test
    fun `findCredentials returns the id and secret pair`() {
        val js = "client_id:\"$clientId\",client_secret:\"$clientSecret\""

        assertEquals(
            TournesolClientCredentials(clientId, clientSecret),
            TournesolBundleScanner.findCredentials(js)
        )
    }

    @Test
    fun `findCredentials accepts colon separated id and secret`() {
        val js = "btoa(\"$clientId:$clientSecret\")"

        assertEquals(
            TournesolClientCredentials(clientId, clientSecret),
            TournesolBundleScanner.findCredentials(js)
        )
    }

    @Test
    fun `findCredentials selects the first id and the first secret`() {
        val otherId = "c".repeat(40)
        val otherSecret = "d".repeat(128)
        val js = "\"$clientId\" \"$otherId\" \"$clientSecret\" \"$otherSecret\""

        assertEquals(
            TournesolClientCredentials(clientId, clientSecret),
            TournesolBundleScanner.findCredentials(js)
        )
    }

    @Test
    fun `findCredentials returns null for an id without secret`() {
        assertNull(TournesolBundleScanner.findCredentials("\"$clientId\""))
    }

    @Test
    fun `findCredentials returns null for a secret without id`() {
        assertNull(TournesolBundleScanner.findCredentials("\"$clientSecret\""))
    }

    @Test
    fun `findCredentials rejects ids of the wrong length`() {
        assertNull(TournesolBundleScanner.findCredentials("\"${"a".repeat(39)}\" \"$clientSecret\""))
        assertNull(TournesolBundleScanner.findCredentials("\"${"a".repeat(41)}\" \"$clientSecret\""))
    }

    @Test
    fun `findCredentials rejects secrets of the wrong length`() {
        assertNull(TournesolBundleScanner.findCredentials("\"$clientId\" \"${"b".repeat(127)}\""))
        assertNull(TournesolBundleScanner.findCredentials("\"$clientId\" \"${"b".repeat(129)}\""))
    }

    @Test
    fun `scanner returns nothing for empty input`() {
        assertTrue(TournesolBundleScanner.bundleUrls("", baseUrl).isEmpty())
        assertTrue(TournesolBundleScanner.chunkUrls("", baseUrl, emptyList()).isEmpty())
        assertNull(TournesolBundleScanner.findCredentials(""))
    }
}
