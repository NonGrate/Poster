package com.example.poster.ktor

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import com.example.poster.auth.InMemoryAuthTokenStorage
import com.example.poster.model.AuthTokens
import com.example.poster.network.SessionCheck
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData

/**
 * Which HTTP answers end a session, and which are merely silence.
 *
 * Everything used to be caught and turned into `null`, so a 401 and a dead
 * network were the same value and the app had to guess what it meant.
 */
class SessionCheckTest {

    private fun apiThatResponds(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): KtorUserApi {
        val storage = InMemoryAuthTokenStorage()
        runBlocking { storage.save(AuthTokens("access", "refresh", 900)) }
        val engine = MockEngine(handler)
        return KtorUserApi(
            createHttpClient(
                engine = engine,
                serverHost = "example.test",
                serverPort = 443,
                serverScheme = "https",
                authTokenStorage = storage,
            ),
            storage,
        )
    }

    private val userJson = """
        {"guid":"user-1","name":"A","surname":"B","email":"a@example.com",
         "passwordHash":"","photo":null,"role":"user","status":"active"}
    """.trimIndent()

    @Test
    fun anAnswerWithTheUserIsValid() = runBlocking {
        val api = apiThatResponds {
            respond(userJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        val check = api.verifySession("user-1")

        assertTrue(check is SessionCheck.Valid, "a good answer was not treated as valid")
        assertEquals("user-1", (check as SessionCheck.Valid).user.guid)
    }

    @Test
    fun unauthorizedEndsTheSession() = runBlocking {
        val api = apiThatResponds { respondError(HttpStatusCode.Unauthorized) }

        assertEquals(SessionCheck.Rejected, api.verifySession("user-1"))
    }

    @Test
    fun anAccountThatNoLongerExistsEndsTheSession() = runBlocking {
        val api = apiThatResponds { respondError(HttpStatusCode.NotFound) }

        assertEquals(SessionCheck.Rejected, api.verifySession("user-1"))
    }

    /** A server having a bad day says nothing about whether the session is good. */
    @Test
    fun aServerErrorIsNotAnAnswer() = runBlocking {
        val api = apiThatResponds { respondError(HttpStatusCode.InternalServerError) }

        assertTrue(
            api.verifySession("user-1") is SessionCheck.Unreachable,
            "a 500 was treated as the server rejecting the session",
        )
    }

    @Test
    fun noNetworkIsNotAnAnswer() = runBlocking {
        val api = apiThatResponds { throw kotlinx.io.IOException("no route to host") }

        assertTrue(
            api.verifySession("user-1") is SessionCheck.Unreachable,
            "a network failure was treated as the server rejecting the session",
        )
    }
}
