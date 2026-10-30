package org.schabi.newpipe.util

data class StoredTokens(
    val accessToken: String?,
    val refreshToken: String?,
    val expiresAtMillis: Long?
)

sealed interface RefreshResult<out G> {
    data class Success<G>(val grant: G) : RefreshResult<G>
    data object InvalidGrant : RefreshResult<Nothing>
    data class Failure(val cause: Throwable) : RefreshResult<Nothing>
}

sealed interface AccessTokenResult {
    data class Token(val value: String) : AccessTokenResult
    data object LoggedOut : AccessTokenResult
    data class Failed(val cause: Throwable) : AccessTokenResult
}

interface TokenStore<G> {
    fun load(): StoredTokens?

    /** Returns null if the session was cleared while the refresh was in flight. */
    fun save(grant: G): StoredTokens?

    fun clear()
}

fun interface TokenRefresher<G> {
    fun refresh(refreshToken: String): RefreshResult<G>
}

class TournesolSession<G>(
    private val store: TokenStore<G>,
    private val refresher: TokenRefresher<G>,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val lock = Any()

    /** Blocks; the lock is held across the refresh so parallel callers share one refresh. */
    fun validToken(minTtlMs: Long): AccessTokenResult {
        synchronized(lock) {
            val tokens = store.load() ?: return AccessTokenResult.LoggedOut
            val accessToken = tokens.accessToken
            if (!accessToken.isNullOrBlank() && !isExpiring(tokens, minTtlMs)) {
                return AccessTokenResult.Token(accessToken)
            }

            val refreshToken = tokens.refreshToken
            if (refreshToken.isNullOrBlank()) {
                return AccessTokenResult.LoggedOut
            }

            return when (val result = refresher.refresh(refreshToken)) {
                is RefreshResult.Success -> store.save(result.grant)?.accessToken
                    ?.takeIf { it.isNotBlank() }
                    ?.let { AccessTokenResult.Token(it) }
                    ?: AccessTokenResult.LoggedOut

                RefreshResult.InvalidGrant -> {
                    store.clear()
                    AccessTokenResult.LoggedOut
                }

                is RefreshResult.Failure -> AccessTokenResult.Failed(result.cause)
            }
        }
    }

    private fun isExpiring(tokens: StoredTokens, minTtlMs: Long): Boolean {
        val expiresAt = tokens.expiresAtMillis ?: return false
        return expiresAt <= clock() + minTtlMs
    }
}

fun <G> classifyRefreshError(error: Throwable): RefreshResult<G> {
    val isInvalidGrant = generateSequence(error) { it.cause }
        .any { it.message?.contains("invalid_grant") == true }
    return if (isInvalidGrant) RefreshResult.InvalidGrant else RefreshResult.Failure(error)
}
