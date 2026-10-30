package org.schabi.newpipe.util

import android.util.Log
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.core.SingleEmitter
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.DownloaderImpl

class TournesolSecretExtractor {
    private val client = (DownloaderImpl.getInstance()?.getClient() ?: OkHttpClient())
        .newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun extractSecrets(): Single<Pair<String, String>> {
        return Single.create { emitter ->
            cachedSecrets?.let {
                emitter.onSuccess(it)
                return@create
            }

            try {
                Log.d(TAG, "Starting dynamic secret extraction...")

                val bundleUrls = TournesolBundleScanner.bundleUrls(fetch(BASE_URL), BASE_URL)
                Log.d(TAG, "Found ${bundleUrls.size} initial JS bundles.")

                val chunkUrls = LinkedHashSet<String>()
                val secrets = firstCredentials(bundleUrls, emitter) { js ->
                    chunkUrls += TournesolBundleScanner.chunkUrls(js, BASE_URL, bundleUrls)
                } ?: firstCredentials(chunkUrls, emitter)

                if (secrets != null) {
                    cachedSecrets = secrets
                }
                emitter.onSuccess(secrets ?: FALLBACK_CREDENTIALS)
            } catch (e: Exception) {
                Log.w(TAG, "Dynamic secret extraction failed, using fallback credentials", e)
                if (!emitter.isDisposed) {
                    emitter.onSuccess(FALLBACK_CREDENTIALS)
                }
            }
        }
    }

    private fun firstCredentials(
        urls: Collection<String>,
        emitter: SingleEmitter<*>,
        onNotFound: (String) -> Unit = {}
    ): Pair<String, String>? {
        for (url in urls) {
            if (emitter.isDisposed) {
                return null
            }

            Log.d(TAG, "Checking $url")
            val content = fetch(url)
            val credentials = TournesolBundleScanner.findCredentials(content)
            if (credentials != null) {
                Log.d(TAG, "Found credentials!")
                return credentials.toPair()
            }
            onNotFound(content)
        }
        return null
    }

    private fun fetch(url: String): String {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Failed to fetch $url: ${response.code}")
            }
            return response.body.string()
        }
    }

    companion object {
        private const val TAG = "TournesolSecrets"
        private const val BASE_URL = "https://tournesol.app/"
        private val FALLBACK_CREDENTIALS = Pair(
            "uUhctKD6C5LC4rPkklnshylcr3PHHQ8DTXBon8Mx",
            "yWeUwik8IrtZ8pwiIaFeCh6vGvp8vCKyxfn8N8cQMd79xM2gfKSMBosSgzCpIyXuTlSCwYCWoZSnEyjlqNnqo89MjeNNAbNT43GAKt7WvokMQDEQoWe0ef1ed8xYXPNn"
        )

        @Volatile
        private var cachedSecrets: Pair<String, String>? = null

        internal fun getDefaultOrCachedSecrets(): Pair<String, String> = cachedSecrets ?: FALLBACK_CREDENTIALS
    }
}
