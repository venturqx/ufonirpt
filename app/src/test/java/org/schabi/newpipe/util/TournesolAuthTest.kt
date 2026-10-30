package org.schabi.newpipe.util

import android.net.Uri
import android.text.TextUtils
import android.util.Base64
import android.util.Log
import java.io.File
import java.util.Properties
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.MockedStatic
import org.mockito.Mockito

class TournesolAuthTest {
    private lateinit var logMock: MockedStatic<Log>
    private lateinit var base64Mock: MockedStatic<Base64>
    private lateinit var uriMock: MockedStatic<Uri>
    private lateinit var textUtilsMock: MockedStatic<TextUtils>

    @Before
    fun setUp() {
        logMock = Mockito.mockStatic(Log::class.java)
        logMock.`when`<Int> { Log.d(anyString(), anyString()) }.thenReturn(0)
        logMock.`when`<Int> { Log.w(anyString(), anyString(), any()) }.thenReturn(0)

        base64Mock = Mockito.mockStatic(Base64::class.java)
        base64Mock.`when`<String> {
            Base64.encodeToString(any(ByteArray::class.java), anyInt())
        }.thenAnswer { invocation ->
            val bytes = invocation.getArgument<ByteArray>(0)
            java.util.Base64.getEncoder().encodeToString(bytes)
        }

        uriMock = Mockito.mockStatic(Uri::class.java)
        uriMock.`when`<Uri> { Uri.parse(anyString()) }.thenAnswer {
            Mockito.mock(Uri::class.java)
        }

        textUtilsMock = Mockito.mockStatic(TextUtils::class.java)
        textUtilsMock.`when`<Boolean> { TextUtils.isEmpty(any()) }.thenAnswer { invocation ->
            val cs = invocation.getArgument<CharSequence?>(0)
            cs == null || cs.isEmpty()
        }
    }

    @After
    fun tearDown() {
        logMock.close()
        base64Mock.close()
        uriMock.close()
        textUtilsMock.close()
    }

    private fun loadLocalCredentials(): Pair<String, String>? {
        val envUser = System.getenv("TOURNESOL_USERNAME")
        val envPass = System.getenv("TOURNESOL_PASSWORD")
        if (!envUser.isNullOrBlank() && !envPass.isNullOrBlank()) {
            return Pair(envUser, envPass)
        }

        val candidates = listOf(File("local.properties"), File("../local.properties"))
        val propsFile = candidates.firstOrNull { it.isFile } ?: return null
        val props = Properties()
        propsFile.inputStream().use { props.load(it) }
        val user = props.getProperty("tournesol.username")?.trim()
        val pass = props.getProperty("tournesol.password")?.trim()
        if (user.isNullOrBlank() || pass.isNullOrBlank()) {
            return null
        }
        return Pair(user, pass)
    }

    @Test
    fun `extractSecrets extracts valid client id and secret from tournesol app`() {
        val secrets = TournesolSecretExtractor().extractSecrets().blockingGet()
        assertNotNull(secrets)
        assertEquals(40, secrets.first.length)
        assertEquals(128, secrets.second.length)
    }

    @Test
    fun `performPasswordLogin authenticates with local tournesol credentials`() {
        val credentials = loadLocalCredentials()
        assumeTrue(
            "Skipping live Tournesol auth test: credentials not set in local.properties",
            credentials != null
        )
        val (username, password) = credentials!!

        val tokenResponse = TournesolAuthManager.performPasswordLogin(username, password)
            .blockingGet()

        assertNotNull(tokenResponse)
        assertFalse(tokenResponse.accessToken.isNullOrBlank())
        assertFalse(tokenResponse.refreshToken.isNullOrBlank())
        assertTrue(tokenResponse.tokenType.equals("Bearer", ignoreCase = true))
    }
}
