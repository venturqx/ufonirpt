package org.schabi.newpipe.util

internal data class TournesolClientCredentials(val clientId: String, val clientSecret: String) {
    fun toPair(): Pair<String, String> = Pair(clientId, clientSecret)
}

internal object TournesolBundleScanner {
    private val SCRIPT_SRC_PATTERN = Regex("src=\\\"(/assets/.*?-([\\w-]+)\\.js)\\\"")
    private val HREF_PATTERN = Regex("href=\\\"(/assets/.*?-([\\w-]+)\\.js)\\\"")
    private val CHUNK_PATTERN = Regex("[\\\"'`](assets/[a-zA-Z0-9_-]+\\.js)[\\\"'`]")
    private val ID_PATTERN = Regex("[\\\"'`]([a-zA-Z0-9]{40})[\\\"'`:]")
    private val SECRET_PATTERN = Regex("[\\\"'`:]([a-zA-Z0-9]{128})[\\\"'`]")

    fun bundleUrls(html: String, baseUrl: String): List<String> = (firstGroups(HREF_PATTERN, html) + firstGroups(SCRIPT_SRC_PATTERN, html))
        .map { baseUrl + it.removePrefix("/") }
        .distinct()
        .sortedByDescending { it.contains("components") }
        .toList()

    fun chunkUrls(js: String, baseUrl: String, knownUrls: Collection<String>): List<String> = firstGroups(CHUNK_PATTERN, js)
        .map { baseUrl + it }
        .filterNot { it in knownUrls }
        .distinct()
        .toList()

    fun findCredentials(js: String): TournesolClientCredentials? {
        val clientId = firstGroups(ID_PATTERN, js).firstOrNull() ?: return null
        val clientSecret = firstGroups(SECRET_PATTERN, js).firstOrNull() ?: return null
        return TournesolClientCredentials(clientId, clientSecret)
    }

    private fun firstGroups(pattern: Regex, text: String): Sequence<String> = pattern.findAll(text).map { it.groupValues[1] }
}
