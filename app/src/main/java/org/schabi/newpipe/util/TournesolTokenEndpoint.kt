package org.schabi.newpipe.util

import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonParserException
import java.io.IOException
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

data class TokenGrant(
    val accessToken: String,
    val tokenType: String?,
    val expiresAtMillis: Long?,
    val refreshToken: String?,
    val scope: String?
)

sealed class TokenCommand(val grantType: String, val errorLabel: String) {
    class Password(val username: String, val password: String) : TokenCommand("password", "Login failed")

    class Refresh(val refreshToken: String) : TokenCommand("refresh_token", "Token refresh failed")
}

class TokenEndpointException(
    val httpStatus: Int,
    val oauthError: String?,
    message: String
) : IOException(message) {
    val isInvalidGrant: Boolean get() = isRejectedAs("invalid_grant")

    val isInvalidClient: Boolean get() = isRejectedAs("invalid_client")

    private fun isRejectedAs(error: String) = httpStatus in 400..499 && oauthError == error
}

class TournesolTokenEndpoint(
    private val client: OkHttpClient,
    private val tokenUrl: String,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    fun request(clientId: String, clientSecret: String, command: TokenCommand): TokenGrant {
        val request = Request.Builder()
            .url(tokenUrl)
            .post(buildForm(command))
            .header("Authorization", Credentials.basic(clientId, clientSecret, Charsets.UTF_8))
            .build()

        return client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw TokenEndpointException(
                    httpStatus = response.code,
                    oauthError = parseOAuthError(body),
                    message = "${command.errorLabel}: ${response.code} $body"
                )
            }
            parseGrant(body, (command as? TokenCommand.Refresh)?.refreshToken)
        }
    }

    private fun buildForm(command: TokenCommand): FormBody {
        val form = FormBody.Builder().add("grant_type", command.grantType)
        when (command) {
            is TokenCommand.Password ->
                form
                    .add("username", command.username)
                    .add("password", command.password)

            is TokenCommand.Refresh -> form.add("refresh_token", command.refreshToken)
        }
        return form.add("scope", OAUTH_SCOPE).build()
    }

    private fun parseGrant(body: String, refreshTokenFallback: String?): TokenGrant {
        val json = JsonParser.`object`().from(body)
        val accessToken = json.getString("access_token", "")
        if (accessToken.isBlank()) {
            throw IOException("Token response missing access_token")
        }
        return TokenGrant(
            accessToken = accessToken,
            tokenType = json.getString("token_type", "").takeIf { it.isNotBlank() },
            expiresAtMillis = if (json.has("expires_in")) {
                nowMillis() + json.getLong("expires_in") * 1000
            } else {
                null
            },
            refreshToken = json.getString("refresh_token", "").takeIf { it.isNotBlank() }
                ?: refreshTokenFallback,
            scope = json.getString("scope", "").takeIf { it.isNotBlank() }
        )
    }

    private fun parseOAuthError(body: String): String? = try {
        JsonParser.`object`().from(body).getString("error")
    } catch (_: JsonParserException) {
        null
    }

    private companion object {
        const val OAUTH_SCOPE = "read write groups"
    }
}
