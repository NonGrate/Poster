package com.example.poster

import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import com.example.poster.model.AuthResponse
import com.example.poster.model.RegisterRequest
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthorizationRoutesTest {
    @Test
    fun authenticatedUserCannotActAsAnotherUser() {
        val databasePath = Files.createTempDirectory("poster-authz").resolve("test.db")
        val oldDevelopment = System.getProperty("io.ktor.development")
        val oldDatabase = System.getProperty("poster.database")
        System.setProperty("io.ktor.development", "true")
        System.setProperty("poster.database", databasePath.toString())

        try {
            testApplication {
                application { module() }

                val first = register("First", "first@example.com")
                val second = register("Second", "second@example.com")

                val forgedPost = """
                    {
                      "guid":"forged-post",
                      "title":"Forged",
                      "message":"Not allowed",
                      "author":"${second.user.guid}",
                      "group":null,
                      "likes":0,
                      "date":"${Clock.System.now().toLocalDateTime(TimeZone.UTC)}",
                      "tags":[]
                    }
                """.trimIndent()
                assertEquals(
                    HttpStatusCode.Forbidden,
                    client.post("/posts") {
                        bearerAuth(first.tokens.accessToken)
                        contentType(ContentType.Application.Json)
                        setBody(forgedPost)
                    }.status,
                )
                assertEquals(
                    HttpStatusCode.Forbidden,
                    client.get("/favorites/user/${second.user.guid}") {
                        bearerAuth(first.tokens.accessToken)
                    }.status,
                )
                assertEquals(
                    HttpStatusCode.OK,
                    client.get("/favorites/me") {
                        bearerAuth(first.tokens.accessToken)
                    }.status,
                )
                assertEquals(
                    HttpStatusCode.Forbidden,
                    client.get("/groups/user/${second.user.guid}") {
                        bearerAuth(first.tokens.accessToken)
                    }.status,
                )
                assertEquals(
                    HttpStatusCode.Unauthorized,
                    client.get("/tags").status,
                )
                assertEquals(
                    HttpStatusCode.OK,
                    client.get("/tags") {
                        bearerAuth(first.tokens.accessToken)
                    }.status,
                )
            }
        } finally {
            if (oldDevelopment == null) System.clearProperty("io.ktor.development")
            else System.setProperty("io.ktor.development", oldDevelopment)
            if (oldDatabase == null) System.clearProperty("poster.database")
            else System.setProperty("poster.database", oldDatabase)
            databasePath.toFile().parentFile.deleteRecursively()
        }
    }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.register(
        name: String,
        email: String,
    ): AuthResponse {
        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(RegisterRequest(name, "User", email, "password123")))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        // Posting needs a confirmed address; this suite is not about that.
        confirmAddress(email)
        return Json.decodeFromString(response.bodyAsText())
    }
}
