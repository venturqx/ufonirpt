package org.schabi.newpipe.util

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TournesolSessionTest {
    private class FakeStore(var tokens: StoredTokens?) : TokenStore<StoredTokens> {
        var saves = 0
        var clears = 0

        @Synchronized
        override fun load() = tokens

        @Synchronized
        override fun save(grant: StoredTokens): StoredTokens? {
            saves++
            if (tokens == null) {
                return null
            }
            tokens = grant
            return grant
        }

        @Synchronized
        override fun clear() {
            clears++
            tokens = null
        }
    }

    private class CountingRefresher(
        private val result: (String) -> RefreshResult<StoredTokens>
    ) : TokenRefresher<StoredTokens> {
        val calls = AtomicInteger()

        override fun refresh(refreshToken: String): RefreshResult<StoredTokens> {
            calls.incrementAndGet()
            return result(refreshToken)
        }
    }

    private val refreshed = StoredTokens("new-access", "new-refresh", NOW + HOUR)

    private fun session(store: FakeStore, refresher: CountingRefresher) = TournesolSession(store, refresher) { NOW }

    private fun succeeding() = CountingRefresher { RefreshResult.Success(refreshed) }

    @Test
    fun `no stored tokens means logged out without touching the network`() {
        val refresher = succeeding()
        val result = session(FakeStore(null), refresher).validToken(TTL)

        assertEquals(AccessTokenResult.LoggedOut, result)
        assertEquals(0, refresher.calls.get())
    }

    @Test
    fun `unexpired token is returned without refreshing`() {
        val refresher = succeeding()
        val store = FakeStore(StoredTokens("access", "refresh", NOW + TTL + 1))

        assertEquals(
            AccessTokenResult.Token("access"),
            session(store, refresher).validToken(TTL)
        )
        assertEquals(0, refresher.calls.get())
    }

    @Test
    fun `token without an expiry is treated as valid`() {
        val refresher = succeeding()
        val store = FakeStore(StoredTokens("access", "refresh", null))

        assertEquals(
            AccessTokenResult.Token("access"),
            session(store, refresher).validToken(TTL)
        )
        assertEquals(0, refresher.calls.get())
    }

    @Test
    fun `token expiring exactly at the ttl boundary is refreshed and persisted`() {
        val refresher = succeeding()
        val store = FakeStore(StoredTokens("access", "refresh", NOW + TTL))

        assertEquals(
            AccessTokenResult.Token("new-access"),
            session(store, refresher).validToken(TTL)
        )
        assertEquals(1, refresher.calls.get())
        assertEquals(refreshed, store.tokens)
    }

    @Test
    fun `blank access token is refreshed`() {
        val refresher = succeeding()
        val store = FakeStore(StoredTokens(" ", "refresh", null))

        assertEquals(
            AccessTokenResult.Token("new-access"),
            session(store, refresher).validToken(TTL)
        )
    }

    @Test
    fun `refresh uses the stored refresh token`() {
        var seen: String? = null
        val refresher = CountingRefresher {
            seen = it
            RefreshResult.Success(refreshed)
        }
        session(FakeStore(StoredTokens(null, "refresh", null)), refresher).validToken(TTL)

        assertEquals("refresh", seen)
    }

    @Test
    fun `expired token without refresh token is logged out and keeps the store`() {
        val refresher = succeeding()
        val store = FakeStore(StoredTokens("access", null, NOW - 1))

        assertEquals(AccessTokenResult.LoggedOut, session(store, refresher).validToken(TTL))
        assertEquals(0, refresher.calls.get())
        assertEquals(0, store.clears)
    }

    @Test
    fun `invalid grant clears the session so the login prompt can appear`() {
        val refresher = CountingRefresher { RefreshResult.InvalidGrant }
        val store = FakeStore(StoredTokens("access", "refresh", NOW - 1))

        assertEquals(AccessTokenResult.LoggedOut, session(store, refresher).validToken(TTL))
        assertEquals(1, store.clears)
        assertNull(store.tokens)
    }

    @Test
    fun `network failure keeps the tokens and reports the cause`() {
        val cause = IOException("offline")
        val original = StoredTokens("access", "refresh", NOW - 1)
        val store = FakeStore(original)
        val refresher = CountingRefresher { RefreshResult.Failure(cause) }

        val result = session(store, refresher).validToken(TTL)

        assertSame(cause, (result as AccessTokenResult.Failed).cause)
        assertEquals(original, store.tokens)
        assertEquals(0, store.clears)
    }

    @Test
    fun `logout during a refresh is not undone by the refresh result`() {
        val store = FakeStore(StoredTokens("access", "refresh", NOW - 1))
        val refresher = CountingRefresher {
            store.clear()
            RefreshResult.Success(refreshed)
        }

        assertEquals(AccessTokenResult.LoggedOut, session(store, refresher).validToken(TTL))
        assertNull(store.tokens)
    }

    @Test
    fun `refresh returning a blank access token is logged out`() {
        val store = FakeStore(StoredTokens("access", "refresh", NOW - 1))
        val refresher = CountingRefresher {
            RefreshResult.Success(StoredTokens("", "refresh", NOW + HOUR))
        }

        assertEquals(AccessTokenResult.LoggedOut, session(store, refresher).validToken(TTL))
    }

    @Test
    fun `parallel callers share a single refresh`() {
        val callers = 8
        val store = FakeStore(StoredTokens("access", "refresh", NOW - 1))
        val refresher = CountingRefresher {
            Thread.sleep(50)
            RefreshResult.Success(refreshed)
        }
        val session = session(store, refresher)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(callers)

        val results = (1..callers).map {
            pool.submit<AccessTokenResult> {
                start.await()
                session.validToken(TTL)
            }
        }
        start.countDown()

        results.forEach {
            assertEquals(
                AccessTokenResult.Token("new-access"),
                it.get(5, TimeUnit.SECONDS)
            )
        }
        pool.shutdown()
        assertEquals(1, refresher.calls.get())
        assertEquals(1, store.saves)
    }

    @Test
    fun `invalid_grant is recognised at any depth of the cause chain`() {
        val direct = IOException("""Token refresh failed: 400 {"error":"invalid_grant"}""")

        assertEquals(RefreshResult.InvalidGrant, classifyRefreshError<Unit>(direct))
        assertEquals(
            RefreshResult.InvalidGrant,
            classifyRefreshError<Unit>(RuntimeException(direct))
        )
    }

    @Test
    fun `other refresh errors stay retryable failures`() {
        val error = IOException("Token refresh failed: 500 oops")
        val result = classifyRefreshError<Unit>(error)

        assertTrue(result is RefreshResult.Failure)
        assertSame(error, (result as RefreshResult.Failure).cause)
    }

    private companion object {
        const val NOW = 1_000_000L
        const val TTL = 120_000L
        const val HOUR = 3_600_000L
    }
}
