package org.schabi.newpipe.tournesol

import android.content.Context
import android.content.SharedPreferences
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import java.util.concurrent.TimeUnit
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.TokenRequest
import net.openid.appauth.TokenResponse
import okhttp3.OkHttpClient

data class TournesolProfile(
    val username: String?,
    val comparisonCount: Int?,
    val dailyComparisons: Int
)

open class MissingTokenException : RuntimeException()

class SessionExpiredException : MissingTokenException()

fun Maybe<String>.orMissingToken(): Single<String> = switchIfEmpty(Single.error(MissingTokenException()))

internal fun <T : Any> authenticateThenPersist(
    auth: Single<T>,
    scheduler: Scheduler,
    persist: (T) -> Boolean
): Completable = auth
    .doOnSuccess { check(persist(it)) { "Tournesol login could not be persisted" } }
    .ignoreElement()
    .subscribeOn(scheduler)

object TournesolAuthManager {
    private const val SHARED_PREF_AUTH_STATE = TournesolAuthStorage.AUTH_STATE_KEY
    private const val SHARED_PREF_USERNAME = "tournesol_username"
    private const val SHARED_PREF_COMPARISON_COUNT = "tournesol_comparison_count"
    private const val SHARED_PREF_DAILY_BASELINE_DATE = "tournesol_daily_baseline_date"
    private const val SHARED_PREF_DAILY_BASELINE_COUNT = "tournesol_daily_baseline_count"
    private const val AUTH_URL = "https://api.tournesol.app/o/authorize/"
    private const val TOKEN_URL = "https://api.tournesol.app/o/token/"
    private const val DEFAULT_MIN_TTL_MS = 2 * 60 * 1000L

    @Volatile
    private var storage: TournesolAuthStorage? = null

    @Volatile
    private var session: TournesolSession<TokenResponse>? = null

    private val signedOutEvents = PublishSubject.create<Unit>().toSerialized()

    private class AuthStateTokenStore(
        private val appContext: Context
    ) : TokenStore<TokenResponse> {
        override fun load(): StoredTokens? = getAuthState(appContext)?.toStoredTokens()

        override fun save(grant: TokenResponse): StoredTokens? {
            val prefs = getAuthStatePrefs(appContext) ?: return null
            val authState = getAuthState(appContext) ?: return null
            authState.update(grant, null)
            // commit(): a rotated refresh token that is lost cannot be recovered.
            prefs.edit().putString(SHARED_PREF_AUTH_STATE, authState.jsonSerializeString()).commit()
            return authState.toStoredTokens()
        }

        override fun clear() = clearAuthState(appContext)
    }

    private fun AuthState.toStoredTokens() = StoredTokens(accessToken, refreshToken, accessTokenExpirationTime)

    private fun session(appContext: Context): TournesolSession<TokenResponse> = session ?: synchronized(this) {
        session ?: TournesolSession(AuthStateTokenStore(appContext), ::refreshBlocking)
            .also { session = it }
    }

    private fun refreshBlocking(refreshToken: String): RefreshResult<TokenResponse> = performTokenRefresh(refreshToken)
        .map<RefreshResult<TokenResponse>> { RefreshResult.Success(it) }
        .onErrorReturn { classifyRefreshError(it) }
        .blockingGet()

    private fun storage(context: Context): TournesolAuthStorage = storage ?: synchronized(this) {
        storage ?: TournesolAuthStorage.create(context.applicationContext)
            .also { storage = it }
    }

    private fun getAuthStatePrefs(
        context: Context,
        resetIfUnreadable: Boolean = false
    ): SharedPreferences? = storage(context).prefs(resetIfUnreadable)

    fun saveUsername(context: Context, username: String) {
        val prefs = getAuthStatePrefs(context, resetIfUnreadable = true) ?: return
        prefs.edit().putString(SHARED_PREF_USERNAME, username).apply()
    }

    fun getUsername(context: Context): String? {
        val prefs = getAuthStatePrefs(context) ?: return null
        return prefs.getString(SHARED_PREF_USERNAME, null)?.takeIf { it.isNotBlank() }
    }

    fun saveComparisonCount(context: Context, count: Int) {
        val prefs = getAuthStatePrefs(context) ?: return
        prefs.edit().putInt(SHARED_PREF_COMPARISON_COUNT, count).apply()
    }

    fun getComparisonCount(context: Context): Int? {
        val prefs = getAuthStatePrefs(context) ?: return null
        return if (prefs.contains(SHARED_PREF_COMPARISON_COUNT)) {
            prefs.getInt(SHARED_PREF_COMPARISON_COUNT, 0)
        } else {
            null
        }
    }

    fun incrementComparisonCount(context: Context) {
        val current = getComparisonCount(context) ?: return
        saveComparisonCount(context, current + 1)
    }

    fun loadProfile(context: Context): Single<TournesolProfile> {
        val appContext = context.applicationContext
        return Single
            .fromCallable {
                TournesolProfile(
                    getUsername(appContext),
                    getComparisonCount(appContext),
                    getDailyComparisons(appContext)
                )
            }
            .subscribeOn(Schedulers.io())
    }

    private fun getDailyComparisons(context: Context): Int {
        val prefs = getAuthStatePrefs(context) ?: return 0
        val progress = dailyProgress(
            currentCount = getComparisonCount(context),
            today = java.time.LocalDate.now().toString(),
            storedDate = prefs.getString(SHARED_PREF_DAILY_BASELINE_DATE, null),
            storedCount = if (prefs.contains(SHARED_PREF_DAILY_BASELINE_COUNT)) {
                prefs.getInt(SHARED_PREF_DAILY_BASELINE_COUNT, 0)
            } else {
                null
            }
        )
        progress.baselineToPersist?.let { baseline ->
            prefs.edit()
                .putString(SHARED_PREF_DAILY_BASELINE_DATE, baseline.date)
                .putInt(SHARED_PREF_DAILY_BASELINE_COUNT, baseline.count)
                .apply()
        }
        return progress.count
    }

    fun clearAuthState(context: Context) {
        getAuthStatePrefs(context)?.let { prefs ->
            prefs.edit()
                .remove(SHARED_PREF_AUTH_STATE)
                .remove(SHARED_PREF_USERNAME)
                .remove(SHARED_PREF_COMPARISON_COUNT)
                .remove(SHARED_PREF_DAILY_BASELINE_DATE)
                .remove(SHARED_PREF_DAILY_BASELINE_COUNT)
                .apply()
        }
        CompareScoreStore.shared(context).clear()
        signedOutEvents.onNext(Unit)
    }

    fun signedOut(): Observable<Unit> = signedOutEvents.observeOn(Schedulers.io())

    private fun saveAuthState(context: Context, authState: AuthState): Boolean {
        val prefs = getAuthStatePrefs(context, resetIfUnreadable = true) ?: return false
        return prefs.edit().putString(SHARED_PREF_AUTH_STATE, authState.jsonSerializeString()).commit()
    }

    fun saveAuthState(context: Context, tokenResponse: TokenResponse): Boolean {
        val authState = AuthState(serviceConfiguration())
        authState.update(tokenResponse, null)
        return saveAuthState(context, authState)
    }

    private fun getAuthState(context: Context): AuthState? {
        val prefs = getAuthStatePrefs(context) ?: return null
        val json = prefs.getString(SHARED_PREF_AUTH_STATE, null) ?: return null
        return try {
            AuthState.jsonDeserialize(json)
        } catch (_: Exception) {
            null
        }
    }

    fun getValidAccessToken(
        context: Context,
        minTtlMs: Long = DEFAULT_MIN_TTL_MS
    ): Maybe<String> {
        val appContext = context.applicationContext
        return Maybe.fromCallable {
            when (val result = session(appContext).validToken(minTtlMs)) {
                is AccessTokenResult.Token -> result.value
                AccessTokenResult.LoggedOut -> null
                AccessTokenResult.Expired -> throw SessionExpiredException()
                is AccessTokenResult.Failed -> throw result.cause
            }
        }.subscribeOn(Schedulers.io())
    }

    fun login(context: Context, username: String, password: String): Completable {
        val appContext = context.applicationContext
        val auth = performPasswordLogin(username, password)
        return authenticateThenPersist(auth, Schedulers.io()) { tokenResponse ->
            saveUsername(appContext, username)
            saveAuthState(appContext, tokenResponse)
        }
    }

    fun performPasswordLogin(username: String, password: String): Single<TokenResponse> = performTokenRequestWithFallback(TokenCommand.Password(username, password))

    fun performTokenRefresh(refreshToken: String): Single<TokenResponse> = performTokenRequestWithFallback(TokenCommand.Refresh(refreshToken))

    private fun performTokenRequestWithFallback(command: TokenCommand): Single<TokenResponse> {
        val initialSecrets = TournesolSecretExtractor.getDefaultOrCachedSecrets()
        return performTokenRequest(initialSecrets, command).onErrorResumeNext { error ->
            if (error is TokenEndpointException && error.isInvalidClient) {
                TournesolSecretExtractor().extractSecrets()
                    .flatMap { refreshedSecrets -> performTokenRequest(refreshedSecrets, command) }
            } else {
                Single.error(error)
            }
        }
    }

    private fun performTokenRequest(
        secrets: Pair<String, String>,
        command: TokenCommand
    ): Single<TokenResponse> = Single.fromCallable {
        val (clientId, clientSecret) = secrets
        val baseClient = TournesolHttpClient.provider() ?: OkHttpClient()
        val client = baseClient.newBuilder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        TournesolTokenEndpoint(client, TOKEN_URL)
            .request(clientId, clientSecret, command)
            .toTokenResponse(clientId, command)
    }

    private fun serviceConfiguration() = AuthorizationServiceConfiguration(
        android.net.Uri.parse(AUTH_URL),
        android.net.Uri.parse(TOKEN_URL)
    )

    private fun TokenGrant.toTokenResponse(clientId: String, command: TokenCommand): TokenResponse {
        val requestBuilder = TokenRequest.Builder(serviceConfiguration(), clientId)
            .setGrantType(command.grantType)
        if (command is TokenCommand.Refresh) {
            requestBuilder.setRefreshToken(command.refreshToken)
        }
        val builder = TokenResponse.Builder(requestBuilder.build())

        builder.setAccessToken(accessToken)
        builder.setTokenType(tokenType)
        expiresAtMillis?.let { builder.setAccessTokenExpirationTime(it) }
        refreshToken?.let { builder.setRefreshToken(it) }
        scope?.let { builder.setScope(it) }

        return builder.build()
    }
}
