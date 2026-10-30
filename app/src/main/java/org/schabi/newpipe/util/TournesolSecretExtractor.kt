package org.schabi.newpipe.util

import android.util.Log
import io.reactivex.rxjava3.core.Single
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.DownloaderImpl

internal const val SECRET_SCAN_COOLDOWN_MS = 5 * 60 * 1000L

private val FALLBACK_CREDENTIALS = Pair(
    "uUhctKD6C5LC4rPkklnshylcr3PHHQ8DTXBon8Mx",
    "yWeUwik8IrtZ8pwiIaFeCh6vGvp8vCKyxfn8N8cQMd79xM2gfKSMBosSgzCpIyXuTlSCwYCWoZSnEyjlqNnqo89MjeNNAbNT43GAKt7WvokMQDEQoWe0ef1ed8xYXPNn"
)

internal class TournesolSecretState(private val clock: () -> Long = System::currentTimeMillis) {
    @Volatile
    private var secrets: Pair<String, String>? = null

    @Volatile
    private var lastScanMillis: Long? = null

    fun current(): Pair<String, String> = secrets ?: FALLBACK_CREDENTIALS

    fun isScanDue(): Boolean {
        val lastScan = lastScanMillis ?: return true
        return clock() - lastScan >= SECRET_SCAN_COOLDOWN_MS
    }

    fun recordScan(found: Pair<String, String>?) {
        lastScanMillis = clock()
        found?.let { secrets = it }
    }
}

class TournesolSecretExtractor internal constructor(
    private val fetch: (String) -> String,
    private val state: TournesolSecretState,
    private val warn: (String, Exception) -> Unit
) {
    constructor() : this(httpFetch(), SHARED_STATE, { message, e -> Log.w(TAG, message, e) })

    fun extractSecrets(): Single<Pair<String, String>> {
        return Single.create { emitter ->
            if (!state.isScanDue()) {
                emitter.onSuccess(state.current())
                return@create
            }

            val secrets = try {
                TournesolSecretDiscovery(
                    BASE_URL,
                    fetch,
                    isCancelled = { emitter.isDisposed },
                    onSkipped = { url, e -> warn("Skipping $url", e) }
                ).discover()?.toPair()
            } catch (e: Exception) {
                warn("Dynamic secret extraction failed, using fallback credentials", e)
                null
            }

            if (!emitter.isDisposed) {
                state.recordScan(secrets)
                emitter.onSuccess(secrets ?: FALLBACK_CREDENTIALS)
            }
        }
    }

    companion object {
        private const val TAG = "TournesolSecrets"
        private const val BASE_URL = "https://tournesol.app/"
        private val SHARED_STATE = TournesolSecretState()

        internal fun getDefaultOrCachedSecrets(): Pair<String, String> = SHARED_STATE.current()

        private fun httpFetch(): (String) -> String {
            val client = (DownloaderImpl.getInstance()?.getClient() ?: OkHttpClient())
                .newBuilder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            return { url ->
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Failed to fetch $url: ${response.code}")
                    }
                    response.body.string()
                }
            }
        }
    }
}

internal class TournesolSecretDiscovery(
    private val baseUrl: String,
    private val fetch: (String) -> String,
    private val isCancelled: () -> Boolean = { false },
    private val onSkipped: (String, IOException) -> Unit = { _, _ -> }
) {
    fun discover(): TournesolClientCredentials? {
        val bundleUrls = TournesolBundleScanner.bundleUrls(fetch(baseUrl), baseUrl)

        val chunkUrls = LinkedHashSet<String>()
        return firstCredentials(bundleUrls) { js ->
            chunkUrls += TournesolBundleScanner.chunkUrls(js, baseUrl, bundleUrls)
        } ?: firstCredentials(chunkUrls)
    }

    private fun firstCredentials(
        urls: Collection<String>,
        onNotFound: (String) -> Unit = {}
    ): TournesolClientCredentials? {
        for (url in urls) {
            if (isCancelled()) {
                return null
            }

            val content = try {
                fetch(url)
            } catch (e: IOException) {
                onSkipped(url, e)
                continue
            }
            val credentials = TournesolBundleScanner.findCredentials(content)
            if (credentials != null) {
                return credentials
            }
            onNotFound(content)
        }
        return null
    }
}
