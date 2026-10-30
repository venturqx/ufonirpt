package org.schabi.newpipe.util

import java.io.IOException
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TournesolTokenEndpointTest {
    private class FakeServer(private val status: Int, private val body: String) : Interceptor {
        var lastRequest: Request? = null

        override fun intercept(chain: Interceptor.Chain): Response {
            lastRequest = chain.request()
            return Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(status)
                .message("")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    private fun endpoint(server: FakeServer) = TournesolTokenEndpoint(
        client = OkHttpClient.Builder().addInterceptor(server).build(),
        tokenUrl = TOKEN_URL,
        nowMillis = { NOW }
    )

    private fun login(server: FakeServer) = endpoint(server).request(CLIENT_ID, CLIENT_SECRET, TokenCommand.Password("user", "pass"))

    private fun refresh(server: FakeServer) = endpoint(server).request(CLIENT_ID, CLIENT_SECRET, TokenCommand.Refresh(OLD_REFRESH))

    private fun formOf(server: FakeServer): Map<String, String> {
        val form = server.lastRequest!!.body as FormBody
        return (0 until form.size).associate { form.name(it) to form.value(it) }
    }

    @Test
    fun `password login posts the form with client credentials and parses the grant`() {
        val server = FakeServer(
            200,
            """{"access_token":"a","token_type":"Bearer","expires_in":3600,
                "refresh_token":"r","scope":"read write"}"""
        )

        val grant = login(server)

        assertEquals(TokenGrant("a", "Bearer", NOW + 3_600_000L, "r", "read write"), grant)
        assertEquals("POST", server.lastRequest!!.method)
        assertEquals(TOKEN_URL, server.lastRequest!!.url.toString())
        assertEquals(
            Credentials.basic(CLIENT_ID, CLIENT_SECRET),
            server.lastRequest!!.header("Authorization")
        )
        assertEquals(
            mapOf(
                "grant_type" to "password",
                "username" to "user",
                "password" to "pass",
                "scope" to "read write groups"
            ),
            formOf(server)
        )
    }

    @Test
    fun `refresh posts the refresh token`() {
        val server = FakeServer(200, """{"access_token":"a"}""")

        refresh(server)

        assertEquals(
            mapOf(
                "grant_type" to "refresh_token",
                "refresh_token" to OLD_REFRESH,
                "scope" to "read write groups"
            ),
            formOf(server)
        )
    }

    @Test
    fun `rotated refresh token replaces the previous one`() {
        val grant = refresh(FakeServer(200, """{"access_token":"a","refresh_token":"rotated"}"""))

        assertEquals("rotated", grant.refreshToken)
    }

    @Test
    fun `refresh without a rotated token keeps the previous one`() {
        val grant = refresh(FakeServer(200, """{"access_token":"a"}"""))

        assertEquals(OLD_REFRESH, grant.refreshToken)
    }

    @Test
    fun `password login has no refresh token to fall back on`() {
        assertNull(login(FakeServer(200, """{"access_token":"a"}""")).refreshToken)
    }

    @Test
    fun `grant without expires_in has no expiry`() {
        assertNull(login(FakeServer(200, """{"access_token":"a"}""")).expiresAtMillis)
    }

    @Test
    fun `missing access_token fails without an endpoint error`() {
        val error = assertThrows(IOException::class.java) {
            login(FakeServer(200, """{"token_type":"Bearer"}"""))
        }

        assertFalse(error is TokenEndpointException)
        assertEquals("Token response missing access_token", error.message)
    }

    @Test
    fun `invalid_grant is reported as a typed error`() {
        val error = assertThrows(TokenEndpointException::class.java) {
            refresh(FakeServer(400, """{"error":"invalid_grant"}"""))
        }

        assertEquals(400, error.httpStatus)
        assertEquals("invalid_grant", error.oauthError)
        assertTrue(error.isInvalidGrant)
        assertFalse(error.isInvalidClient)
        assertEquals(
            """Token refresh failed: 400 {"error":"invalid_grant"}""",
            error.message
        )
    }

    @Test
    fun `invalid_client is reported as a typed error`() {
        val error = assertThrows(TokenEndpointException::class.java) {
            login(FakeServer(401, """{"error":"invalid_client"}"""))
        }

        assertEquals(401, error.httpStatus)
        assertTrue(error.isInvalidClient)
        assertFalse(error.isInvalidGrant)
        assertEquals("""Login failed: 401 {"error":"invalid_client"}""", error.message)
    }

    @Test
    fun `non-JSON server error keeps the status and has no oauth error`() {
        val error = assertThrows(TokenEndpointException::class.java) {
            refresh(FakeServer(500, "<html>Bad Gateway</html>"))
        }

        assertEquals(500, error.httpStatus)
        assertNull(error.oauthError)
        assertFalse(error.isInvalidGrant)
        assertFalse(error.isInvalidClient)
        assertEquals("Token refresh failed: 500 <html>Bad Gateway</html>", error.message)
    }

    @Test
    fun `error description mentioning invalid_grant is not an invalid grant`() {
        val error = assertThrows(TokenEndpointException::class.java) {
            refresh(
                FakeServer(
                    400,
                    """{"error":"invalid_request","error_description":"not an invalid_grant"}"""
                )
            )
        }

        assertEquals("invalid_request", error.oauthError)
        assertFalse(error.isInvalidGrant)
    }

    private companion object {
        const val TOKEN_URL = "https://api.tournesol.test/o/token/"
        const val CLIENT_ID = "client"
        const val CLIENT_SECRET = "secret"
        const val OLD_REFRESH = "old-refresh"
        const val NOW = 1_000_000L
    }
}
