package com.example.poster

import com.example.poster.config.Features
import com.example.poster.model.AuthResponse
import com.example.poster.model.Group
import com.example.poster.model.RegisterRequest
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** feature.publicGroups: listed, joinable by id; invite-only groups stay invite-only. */
class PublicGroupsTest {
    @Test
    fun aPublicGroupIsListedWithItsCountAndJoinableById() = withServer {
        if (!Features.PUBLIC_GROUPS) return@withServer
        val owner = confirmed("owner@example.com")
        val joiner = confirmed("joiner@example.com")
        val public = create(owner, "Open house", "public")
        val private = create(owner, "Closed door", "private")

        val listed = Json.decodeFromString<List<Group>>(client.get("/groups/public") { bearerAuth(joiner.tokens.accessToken) }.bodyAsText())
        assertEquals(listOf(public.id), listed.map { it.id }, "only the public group is listed")
        assertEquals(1, listed.single().memberCount, "the owner counts as a member")

        assertEquals(HttpStatusCode.NoContent, join(joiner, public.id), "joining a public group by id was refused")
        assertEquals(HttpStatusCode.NotFound, join(joiner, private.id), "an invite-only group could be joined by id")
        val mine = Json.decodeFromString<List<Group>>(client.get("/groups/user/${joiner.user.guid}") { bearerAuth(joiner.tokens.accessToken) }.bodyAsText())
        assertEquals(listOf(public.id), mine.map { it.id })
    }

    @Test
    fun theOwnerCanChangeVisibility_nobodyElseCan() = withServer {
        if (!Features.PUBLIC_GROUPS) return@withServer
        val owner = confirmed("owner@example.com")
        val other = confirmed("other@example.com")
        val group = create(owner, "Sometimes open", "private")
        assertEquals(HttpStatusCode.Forbidden, setVisibility(other, group.id, "public"))
        assertEquals(HttpStatusCode.NoContent, setVisibility(owner, group.id, "public"))
        assertEquals(HttpStatusCode.BadRequest, setVisibility(owner, group.id, "secret"))
        val listed = Json.decodeFromString<List<Group>>(client.get("/groups/public") { bearerAuth(other.tokens.accessToken) }.bodyAsText())
        assertTrue(listed.any { it.id == group.id })
    }

    // --- helpers -----------------------------------------------------------

    private suspend fun ApplicationTestBuilder.create(user: AuthResponse, name: String, visibility: String): Group {
        val response = client.post("/groups/create") {
            bearerAuth(user.tokens.accessToken); contentType(ContentType.Application.Json)
            setBody("""{"name":"$name","visibility":"$visibility"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
        return Json.decodeFromString(response.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.join(user: AuthResponse, groupId: String): HttpStatusCode =
        client.post("/groups/join") {
            bearerAuth(user.tokens.accessToken); contentType(ContentType.Application.Json)
            setBody("""{"userId":"${user.user.guid}","groupId":"$groupId"}""")
        }.status

    private suspend fun ApplicationTestBuilder.setVisibility(user: AuthResponse, groupId: String, visibility: String): HttpStatusCode =
        client.post("/groups/$groupId/visibility") {
            bearerAuth(user.tokens.accessToken); contentType(ContentType.Application.Json)
            setBody("""{"visibility":"$visibility"}""")
        }.status

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
        val root = Files.createTempDirectory("poster-public-groups")
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
