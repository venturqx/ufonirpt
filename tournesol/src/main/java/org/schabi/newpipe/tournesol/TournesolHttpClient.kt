package org.schabi.newpipe.tournesol

import okhttp3.OkHttpClient

object TournesolHttpClient {
    @Volatile
    var provider: () -> OkHttpClient? = { null }
}
