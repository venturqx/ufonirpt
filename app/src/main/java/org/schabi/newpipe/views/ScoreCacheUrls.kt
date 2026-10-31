package org.schabi.newpipe.views

internal object ScoreCacheUrls {
    @JvmStatic
    fun of(originalUrl: String, url: String): List<String> = listOf(originalUrl, url).distinct()
}
