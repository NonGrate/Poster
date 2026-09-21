package com.example.poster.ktor

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import com.example.poster.auth.InMemoryAuthTokenStorage
import com.example.poster.model.AuthTokens
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class ClientTest {
    @Test
    fun bearerTokenIsSentToProtectedRoutes() {
        runBlocking {
            val tokenStorage = InMemoryAuthTokenStorage().apply {
                save(AuthTokens("access-token", "refresh-token", 900))
            }
            val engine = MockEngine { request ->
                assertEquals("Bearer access-token", request.headers[HttpHeaders.Authorization])
                respond("[]", HttpStatusCode.OK)
            }
            val client = createHttpClient(
                engine = engine,
                serverHost = "example.test",
                serverPort = 443,
                serverScheme = "https",
                authTokenStorage = tokenStorage,
            )
            try {
                client.get("tags")
            } finally {
                client.close()
            }
        }
    }
}
