package com.example.poster

import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import com.example.poster.model.AuthResponse
import com.example.poster.model.RegisterRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What "delete my account" has to mean.
 *
 * It used to delete one row in User and nothing else. The posts stayed in
 * the feed, authored by an id that no longer resolved to anybody — which is
 * the worst possible outcome, because the posts are the reason somebody
 * asks: they are where the pets, the marriage and the children were
 * written down.
 */
class AccountDeletionTest {

    @Test
    fun deletingAnAccountTakesThePostsWithIt() = withServer {
        val leaving = register("leaving@example.com")
        confirmAddress("leaving@example.com")
        postPost(leaving, guid = "gone-1", title = "Something private")
        val staying = register("staying@example.com")
        confirmAddress("staying@example.com")
        postPost(staying, guid = "kept-1", title = "Still here")

        assertEquals(HttpStatusCode.NoContent, deleteAccount(leaving))

        val feed = client.get("/posts") { bearerAuth(staying.tokens.accessToken) }.bodyAsText()
        assertFalse("Something private" in feed, "a deleted account left its posts in the feed")
        assertTrue("Still here" in feed, "somebody else's post went with it")
    }

    /** Their session dies with them: a token outliving the account is a way back in. */
    @Test
    fun theirTokensStopWorking() = withServer {
        val leaving = register("leaving@example.com")

        assertEquals(HttpStatusCode.NoContent, deleteAccount(leaving))

        val refreshed = client.post("/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"${leaving.tokens.refreshToken}"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, refreshed.status, "a deleted account could still refresh")
    }

    /** The address is free again, and signing up with it is a new person. */
    @Test
    fun theAddressCanBeUsedAgainAndIsNobodyFromBefore() = withServer {
        val leaving = register("leaving@example.com")
        confirmAddress("leaving@example.com")
        postPost(leaving, guid = "gone-1", title = "Something private")
        deleteAccount(leaving)

        val returning = register("leaving@example.com")

        assertTrue(returning.user.guid != leaving.user.guid, "the same account came back")
        val feed = client.get("/posts") { bearerAuth(returning.tokens.accessToken) }.bodyAsText()
        assertFalse("Something private" in feed, "the old posts came back with the address")
    }

    /** Deleting somebody else's account is the obvious thing to try. */
    @Test
    fun nobodyCanDeleteAnybodyElse() = withServer {
        val one = register("one@example.com")
        val two = register("two@example.com")

        val response = client.delete("/accounts/${two.user.guid}") {
            bearerAuth(one.tokens.accessToken)
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        // And two is still there, which is the part that matters.
        assertEquals(HttpStatusCode.OK, login("two@example.com").status)
    }

    @Test
    fun deletingWithoutASessionIsRefused() = withServer {
        val one = register("one@example.com")

        assertEquals(HttpStatusCode.Unauthorized, client.delete("/accounts/${one.user.guid}").status)
    }

    // — helpers —

    private suspend fun ApplicationTestBuilder.deleteAccount(user: AuthResponse) =
        client.delete("/accounts/${user.user.guid}") { bearerAuth(user.tokens.accessToken) }.status

    private suspend fun ApplicationTestBuilder.register(email: String): AuthResponse {
        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(RegisterRequest("Some", "Body", email, "password123")))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        return Json.decodeFromString(response.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.login(email: String) = client.post("/auth/login") {
        contentType(ContentType.Application.Json)
        setBody("""{"email":"$email","password":"password123"}""")
    }

    private suspend fun ApplicationTestBuilder.postPost(user: AuthResponse, guid: String, title: String) {
        val response = client.post("/posts") {
            bearerAuth(user.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody(
                """{"guid":"$guid","title":"$title","message":"words","author":"${user.user.guid}",""" +
                    """"group":null,"likes":0,"date":"2026-08-23T10:00","visibility":"public",""" +
                    """"tags":[],"language":"en"}""",
            )
        }
        assertEquals(HttpStatusCode.NoContent, response.status, "could not post the post this test needs")
    }

    private fun withServer(block: suspend ApplicationTestBuilder.() -> Unit) {
        val databasePath = Files.createTempDirectory("poster-deletion").resolve("test.db")
        val previousDatabase = System.getProperty("poster.database")
        val previousDevelopment = System.getProperty("io.ktor.development")
        System.setProperty("poster.database", databasePath.toString())
        System.setProperty("io.ktor.development", "true")
        try {
            testApplication {
                application { module() }
                block()
            }
        } finally {
            if (previousDatabase == null) System.clearProperty("poster.database")
            else System.setProperty("poster.database", previousDatabase)
            if (previousDevelopment == null) System.clearProperty("io.ktor.development")
            else System.setProperty("io.ktor.development", previousDevelopment)
            databasePath.toFile().parentFile.deleteRecursively()
        }
    }
}
