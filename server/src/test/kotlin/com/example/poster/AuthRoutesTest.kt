package com.example.poster

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import com.example.poster.auth.Argon2PasswordHasher
import com.example.poster.auth.AuthConfig
import com.example.poster.auth.AuthService
import com.example.poster.auth.TokenService
import com.example.poster.auth.authRoutes
import com.example.poster.auth.configureBearerAuthentication
import com.example.poster.model.AccountLocalRepository
import com.example.poster.model.AuthResponse
import com.example.poster.model.LoginRequest
import com.example.poster.model.LogoutRequest
import com.example.poster.model.RefreshTokenRequest
import com.example.poster.model.RegisterRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AuthRoutesTest {
    @Test
    fun registerLoginRefreshAndLogout() = testApplication {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        PostDatabase.Schema.create(driver)
        val database = PostDatabase(driver)
        val accountRepository = AccountLocalRepository(database)
        val config = AuthConfig(secret = "test-secret-that-is-at-least-thirty-two-characters")
        val tokenService = TokenService(config)
        val authService = AuthService(
            database = database,
            accountRepository = accountRepository,
            passwordHasher = Argon2PasswordHasher(),
            tokenService = tokenService,
            config = config,
        )

        application {
            install(ContentNegotiation) { json() }
            configureBearerAuthentication(config, tokenService, accountRepository)
            routing {
                authRoutes(authService)
                authenticate("auth-jwt") {
                    get("/protected") {
                        call.respondText(call.principal<JWTPrincipal>()!!.payload.subject)
                    }
                }
            }
        }

        val registration = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(
                RegisterRequest("Test", "User", "Test@Example.com", "password123"),
            ))
        }
        assertEquals(HttpStatusCode.OK, registration.status)
        val registrationBody = registration.body<String>()
        assertFalse(registrationBody.contains("password", ignoreCase = true))
        val firstSession = Json.decodeFromString<AuthResponse>(registrationBody)
        assertEquals("test@example.com", firstSession.user.email)

        val storedUser = accountRepository.userByEmail("test@example.com")!!
        assertTrue(storedUser.passwordHash.startsWith("\$argon2id\$"))
        assertNotEquals("password123", storedUser.passwordHash)

        val duplicate = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(
                RegisterRequest("Test", "User", "test@example.com", "password123"),
            ))
        }
        assertEquals(HttpStatusCode.Conflict, duplicate.status)

        val invalidLogin = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(LoginRequest("test@example.com", "wrong-password")))
        }
        assertEquals(HttpStatusCode.Unauthorized, invalidLogin.status)

        val login = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(LoginRequest("test@example.com", "password123")))
        }
        assertEquals(HttpStatusCode.OK, login.status)
        val loginSession = Json.decodeFromString<AuthResponse>(login.body())

        val unauthenticated = client.get("/protected")
        assertEquals(HttpStatusCode.Unauthorized, unauthenticated.status)
        val authenticated = client.get("/protected") {
            bearerAuth(loginSession.tokens.accessToken)
        }
        assertEquals(HttpStatusCode.OK, authenticated.status)
        assertEquals(loginSession.user.guid, authenticated.body<String>())

        val refresh = client.post("/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(RefreshTokenRequest(loginSession.tokens.refreshToken)))
        }
        assertEquals(HttpStatusCode.OK, refresh.status)
        val refreshedSession = Json.decodeFromString<AuthResponse>(refresh.body())
        assertNotEquals(loginSession.tokens.refreshToken, refreshedSession.tokens.refreshToken)

        val reuse = client.post("/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(RefreshTokenRequest(loginSession.tokens.refreshToken)))
        }
        assertEquals(HttpStatusCode.Unauthorized, reuse.status)

        val logout = client.post("/auth/logout") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(LogoutRequest(refreshedSession.tokens.refreshToken)))
        }
        assertEquals(HttpStatusCode.NoContent, logout.status)

        val refreshAfterLogout = client.post("/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(RefreshTokenRequest(refreshedSession.tokens.refreshToken)))
        }
        assertEquals(HttpStatusCode.Unauthorized, refreshAfterLogout.status)

        driver.close()
    }

    @Test
    fun registerCarriesTheNameVisibilityChoice() = testApplication {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        PostDatabase.Schema.create(driver)
        val database = PostDatabase(driver)
        val accountRepository = AccountLocalRepository(database)
        val config = AuthConfig(secret = "test-secret-that-is-at-least-thirty-two-characters")
        val tokenService = TokenService(config)
        val authService = AuthService(
            database = database,
            accountRepository = accountRepository,
            passwordHasher = Argon2PasswordHasher(),
            tokenService = tokenService,
            config = config,
        )
        application {
            install(ContentNegotiation) { json() }
            configureBearerAuthentication(config, tokenService, accountRepository)
            routing { authRoutes(authService) }
        }

        suspend fun registeredUser(email: String, showName: Boolean) =
            Json.decodeFromString<AuthResponse>(
                client.post("/auth/register") {
                    contentType(ContentType.Application.Json)
                    setBody(Json.encodeToString(
                        RegisterRequest("Test", "User", email, "password123", showName = showName),
                    ))
                }.body<String>()
            ).user

        assertTrue(registeredUser("optin@example.com", showName = true).showName)
        assertFalse(registeredUser("default@example.com", showName = false).showName)

        driver.close()
    }
}
