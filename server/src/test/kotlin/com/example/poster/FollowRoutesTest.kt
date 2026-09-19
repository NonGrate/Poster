package com.example.poster

import com.example.poster.config.Features
import com.example.poster.model.AuthResponse
import com.example.poster.model.RegisterRequest
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/** feature.follows: `/follows` and `GET /posts?following=true`. */
class FollowRoutesTest {
    @Test
    fun theFollowingFeedHoldsOnlyFollowedAuthors_andNeverWidensVisibility() = withServer {
        if (!Features.FOLLOWS) return@withServer
        val reader = confirmed("reader@example.com")
        val followed = confirmed("followed@example.com")
        val stranger = confirmed("stranger@example.com")
        postPost(followed, "f-public", "public")
        postPost(followed, "f-private", "private")
        postPost(stranger, "s-public", "public")

        assertEquals(HttpStatusCode.NoContent, client.post("/follows/${followed.user.guid}") { bearerAuth(reader.tokens.accessToken) }.status)
        assertEquals(listOf(followed.user.guid), Json.decodeFromString<List<String>>(client.get("/follows") { bearerAuth(reader.tokens.accessToken) }.bodyAsText()))
        assertEquals(listOf("f-public"), guids(reader, following = true), "only the followed author's visible posts")
        assertEquals(listOf("f-public", "s-public"), guids(reader, following = false).sorted(), "without the filter the feed is unchanged")

        assertEquals(HttpStatusCode.NoContent, client.delete("/follows/${followed.user.guid}") { bearerAuth(reader.tokens.accessToken) }.status)
        assertEquals(emptyList(), guids(reader, following = true))
    }

    @Test
    fun youCannotFollowYourselfOrNobody() = withServer {
        if (!Features.FOLLOWS) return@withServer
        val reader = confirmed("reader@example.com")
        assertEquals(HttpStatusCode.BadRequest, client.post("/follows/${reader.user.guid}") { bearerAuth(reader.tokens.accessToken) }.status)
        assertEquals(HttpStatusCode.NotFound, client.post("/follows/no-such-user") { bearerAuth(reader.tokens.accessToken) }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/follows").status)
    }

    // --- helpers -----------------------------------------------------------

    private suspend fun ApplicationTestBuilder.guids(user: AuthResponse, following: Boolean): List<String> {
        val response = client.get("/posts") { bearerAuth(user.tokens.accessToken); if (following) parameter("following", "true") }
        assertEquals(HttpStatusCode.OK, response.status)
        return Json.parseToJsonElement(response.bodyAsText()).jsonArray.map { it.jsonObject["guid"]!!.jsonPrimitive.content }
    }

    private suspend fun ApplicationTestBuilder.postPost(user: AuthResponse, guid: String, visibility: String) {
        val response = client.post("/posts") {
            bearerAuth(user.tokens.accessToken); contentType(ContentType.Application.Json)
            setBody("""{"guid":"$guid","title":"$guid","message":"m","author":"${user.user.guid}","group":null,"likes":0,"date":"2026-08-23T10:00","visibility":"$visibility","tags":[],"language":"en"}""")
        }
        assertEquals(HttpStatusCode.NoContent, response.status, response.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.confirmed(email: String): AuthResponse {
        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(RegisterRequest.serializer(), RegisterRequest("Some", "Body", email, "password123")))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        confirmAddress(email)
        return Json.decodeFromString(response.bodyAsText())
    }

    private fun withServer(block: suspend ApplicationTestBuilder.() -> Unit) {
        val root = Files.createTempDirectory("poster-follows-test")
        val previous = mapOf("poster.database" to System.getProperty("poster.database"), "io.ktor.development" to System.getProperty("io.ktor.development"))
        System.setProperty("poster.database", root.resolve("test.db").toString())
        System.setProperty("io.ktor.development", "true")
        try {
            testApplication { application { module() }; block() }
        } finally {
            previous.forEach { (key, value) -> if (value == null) System.clearProperty(key) else System.setProperty(key, value) }
            root.toFile().deleteRecursively()
        }
    }
}
