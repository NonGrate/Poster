package com.example.poster

import com.example.poster.model.AuthResponse
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/** `GET /posts?q=`: title and message, any case, alongside the other filters. */
class FeedSearchRoutesTest {
    @Test
    fun searchFindsWordsInTitlesAndMessages_caseInsensitively() = withServer {
        val writer = confirmed("writer@example.com")
        val reader = confirmed("reader@example.com")
        postPost(writer, "p1", title = "Sourdough starter", message = "The bread rises overnight", tags = listOf("food"))
        postPost(writer, "p2", title = "Long run", message = "Twelve kilometres before the BREAD was out of the oven", tags = listOf("fitness"))
        postPost(writer, "p3", title = "Quiet evening", message = "Nothing to report", tags = emptyList())

        assertEquals(listOf("p1"), titlesFor(reader, q = "sourdough"))
        assertEquals(listOf("p1", "p2"), titlesFor(reader, q = "bread").sorted())
        assertEquals(emptyList(), titlesFor(reader, q = "zebra"))
        assertEquals(3, titlesFor(reader, q = "").size)
    }

    @Test
    fun searchComposesWithTheTagFilter() = withServer {
        val writer = confirmed("writer@example.com")
        val reader = confirmed("reader@example.com")
        postPost(writer, "p1", title = "Bread day", message = "Baked", tags = listOf("food"))
        postPost(writer, "p2", title = "Bread run", message = "Ran to the bakery", tags = listOf("fitness"))
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

}
