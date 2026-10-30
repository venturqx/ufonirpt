package org.schabi.newpipe.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import java.io.File
import java.util.concurrent.TimeUnit
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.TokenRequest
import net.openid.appauth.TokenResponse
import okhttp3.OkHttpClient
import org.schabi.newpipe.DownloaderImpl

data class TournesolProfile(val username: String?, val comparisonCount: Int?)

class MissingTokenException : RuntimeException()

fun Maybe<String>.orMissingToken(): Single<String> = switchIfEmpty(Single.error(MissingTokenException()))

object TournesolAuthManager {
    private const val TAG = "TournesolAuthManager"
    private const val SHARED_PREF_AUTH_STATE = "tournesol_auth_state"
    private const val SHARED_PREF_USERNAME = "tournesol_username"
    private const val SHARED_PREF_COMPARISON_COUNT = "tournesol_comparison_count"
    private const val SHARED_PREF_DAILY_BASELINE_DATE = "tournesol_daily_baseline_date"
    private const val SHARED_PREF_DAILY_BASELINE_COUNT = "tournesol_daily_baseline_count"
    private const val AUTH_STATE_PREFS_NAME = "tournesol_auth_state_prefs"
    private const val AUTH_URL = "https://api.tournesol.app/o/authorize/"
    private const val TOKEN_URL = "https://api.tournesol.app/o/token/"
    private const val DEFAULT_MIN_TTL_MS = 2 * 60 * 1000L

    @Volatile
    private var authStatePrefs: SharedPreferences? = null

    @Volatile
    private var session: TournesolSession<TokenResponse>? = null

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

    private fun getAuthStatePrefs(
        context: Context,
        resetIfUnreadable: Boolean = false
    ): SharedPreferences? {
        authStatePrefs?.let { return it }
        return synchronized(this) {
            authStatePrefs
                ?: openAuthStatePrefs(context.applicationContext, resetIfUnreadable)
                    ?.also { authStatePrefs = it }
        }
    }

    private fun openAuthStatePrefs(
        appContext: Context,
        resetIfUnreadable: Boolean
    ): SharedPreferences? {
        val encryptedPrefs = try {
            createEncryptedAuthPrefs(appContext)
        } catch (firstFailure: Exception) {
            if (!resetIfUnreadable) {
                Log.w(TAG, "Encrypted auth prefs are unavailable.", firstFailure)
                return null
            }
            Log.w(TAG, "Encrypted auth prefs are unreadable. Resetting auth storage.", firstFailure)
            clearAuthStateStorage(appContext)
            try {
                createEncryptedAuthPrefs(appContext)
            } catch (secondFailure: Exception) {
                Log.e(TAG, "Failed to recreate encrypted auth prefs after reset", secondFailure)
                null
            }
        } ?: return null

        migrateLegacyAuthState(appContext, encryptedPrefs)

        return encryptedPrefs
    }

    private fun createEncryptedAuthPrefs(appContext: Context): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            AUTH_STATE_PREFS_NAME,
            masterKeyAlias,
            appContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun clearAuthStateStorage(appContext: Context) {
        runCatching {
            appContext.getSharedPreferences(AUTH_STATE_PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
        }
        runCatching {
            appContext.deleteSharedPreferences(AUTH_STATE_PREFS_NAME)
        }
        runCatching {
            val prefsFile = File(
                appContext.applicationInfo.dataDir,
                "shared_prefs/$AUTH_STATE_PREFS_NAME.xml"
            )
            if (prefsFile.exists()) {
                prefsFile.delete()
            }
        }
    }

    private fun migrateLegacyAuthState(
        context: Context,
        encryptedPrefs: SharedPreferences
    ) {
        val legacyPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (!legacyPrefs.contains(SHARED_PREF_AUTH_STATE)) {
            return
        }

        val legacyValue = legacyPrefs.getString(SHARED_PREF_AUTH_STATE, null)
        if (!legacyValue.isNullOrBlank() && !encryptedPrefs.contains(SHARED_PREF_AUTH_STATE)) {
            encryptedPrefs.edit().putString(SHARED_PREF_AUTH_STATE, legacyValue).apply()
        }
        legacyPrefs.edit().remove(SHARED_PREF_AUTH_STATE).apply()
    }

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
                TournesolProfile(getUsername(appContext), getComparisonCount(appContext))
            }
            .subscribeOn(Schedulers.io())
    }

    fun getDailyComparisons(context: Context): Int {
        val prefs = getAuthStatePrefs(context) ?: return 0
        val currentCount = getComparisonCount(context) ?: return 0
        val today = java.time.LocalDate.now().toString()
        val storedDate = prefs.getString(SHARED_PREF_DAILY_BASELINE_DATE, null)
        if (storedDate == today) {
            val baseline = prefs.getInt(SHARED_PREF_DAILY_BASELINE_COUNT, currentCount)
            return (currentCount - baseline).coerceAtLeast(0)
        }
        // New day: reset baseline to current count
        prefs.edit()
            .putString(SHARED_PREF_DAILY_BASELINE_DATE, today)
            .putInt(SHARED_PREF_DAILY_BASELINE_COUNT, currentCount)
            .apply()
        return 0
    }

    fun clearAuthState(context: Context) {
        val prefs = getAuthStatePrefs(context) ?: return
        prefs.edit()
            .remove(SHARED_PREF_AUTH_STATE)
            .remove(SHARED_PREF_USERNAME)
            .remove(SHARED_PREF_COMPARISON_COUNT)
            .remove(SHARED_PREF_DAILY_BASELINE_DATE)
            .remove(SHARED_PREF_DAILY_BASELINE_COUNT)
            .apply()
    }

    fun saveAuthState(context: Context, authState: AuthState) {
        val prefs = getAuthStatePrefs(context, resetIfUnreadable = true) ?: return
        prefs.edit().putString(SHARED_PREF_AUTH_STATE, authState.jsonSerializeString()).apply()
    }

    fun saveAuthState(context: Context, tokenResponse: TokenResponse) {
        val config = AuthorizationServiceConfiguration(
            android.net.Uri.parse(AUTH_URL),
            android.net.Uri.parse(TOKEN_URL)
        )
        val authState = AuthState(config)
        authState.update(tokenResponse, null)
        saveAuthState(context, authState)
    }

    fun getAuthState(context: Context): AuthState? {
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
                is AccessTokenResult.Failed -> throw result.cause
            }
        }.subscribeOn(Schedulers.io())
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
        val baseClient = DownloaderImpl.getInstance()?.getClient() ?: OkHttpClient()
        val client = baseClient.newBuilder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        TournesolTokenEndpoint(client, TOKEN_URL)
            .request(clientId, clientSecret, command)
            .toTokenResponse(clientId, command)
    }

    private fun TokenGrant.toTokenResponse(clientId: String, command: TokenCommand): TokenResponse {
        val config = AuthorizationServiceConfiguration(
            android.net.Uri.parse(AUTH_URL),
            android.net.Uri.parse(TOKEN_URL)
        )
        val requestBuilder = TokenRequest.Builder(config, clientId)
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
