package com.example.poster

import com.example.poster.model.AuthResponse
import com.example.poster.model.RegisterRequest
import io.ktor.client.request.bearerAuth
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

/** `GET /posts?q=`: title and message, any case, alongside the other filters. */
class FeedSearchRoutesTest {
    @Test
    fun searchFindsWordsInTitlesAndMessages_caseInsensitively() = withServer {
        val writer = confirmed("writer@example.com")
        val reader = confirmed("reader@example.com")
        postPost(writer, "p1", "Sourdough starter", "The bread rises overnight", listOf("food"))
        postPost(writer, "p2", "Long run", "Twelve kilometres before the BREAD was out of the oven", listOf("fitness"))
        postPost(writer, "p3", "Quiet evening", "Nothing to report", emptyList())

        assertEquals(listOf("p1"), titlesFor(reader, q = "sourdough"))
        assertEquals(listOf("p2", "p1").sorted(), titlesFor(reader, q = "bread").sorted().let { it })
        assertEquals(emptyList(), titlesFor(reader, q = "zebra"))
        assertEquals(3, titlesFor(reader, q = "").size)
    }

    @Test
    fun searchComposesWithTheTagFilter() = withServer {
        val writer = confirmed("writer@example.com")
        val reader = confirmed("reader@example.com")
        postPost(writer, "p1", "Bread day", "Baked", listOf("food"))
        postPost(writer, "p2", "Bread run", "Ran to the bakery", listOf("fitness"))
        val response = client.get("/posts") { bearerAuth(reader.tokens.accessToken); parameter("q", "bread"); parameter("tags", "food") }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(listOf("p1"), Json.parseToJsonElement(response.bodyAsText()).jsonArray.map { it.jsonObject["guid"]!!.jsonPrimitive.content })
    }

    // --- helpers -----------------------------------------------------------

    private suspend fun ApplicationTestBuilder.titlesFor(user: AuthResponse, q: String): List<String> {
        val response = client.get("/posts") { bearerAuth(user.tokens.accessToken); if (q.isNotEmpty()) parameter("q", q) }
        assertEquals(HttpStatusCode.OK, response.status)
        return Json.parseToJsonElement(response.bodyAsText()).jsonArray.map { it.jsonObject["guid"]!!.jsonPrimitive.content }
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

    private suspend fun ApplicationTestBuilder.postPost(user: AuthResponse, guid: String, title: String, message: String, tags: List<String>) {
        val response = client.post("/posts") {
            bearerAuth(user.tokens.accessToken); contentType(ContentType.Application.Json)
            setBody("""{"guid":"$guid","title":"$title","message":"$message","author":"${user.user.guid}","group":null,"likes":0,"date":"2026-08-23T10:00","visibility":"public","tags":${tags.joinToString(",", "[", "]") { "\"$it\"" }},"language":"en"}""")
        }
        assertEquals(HttpStatusCode.NoContent, response.status, response.bodyAsText())
    }

    private fun withServer(block: suspend ApplicationTestBuilder.() -> Unit) {
        val root = Files.createTempDirectory("poster-search-test")
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
