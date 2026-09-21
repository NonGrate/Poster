package com.example.poster

import com.example.poster.config.Features
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
import com.example.poster.model.AuthResponse
import com.example.poster.model.Group
import com.example.poster.model.PostVisibility
import com.example.poster.model.PostsLocalRepository
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Closing a group.
 *
 * There was no way to do this at all: groups could be made and never
 * removed. The question it raised was what becomes of what people shared into
 * one, and the answer is that it stays theirs.
 */
class GroupClosureTest {

    @Test
    fun whatWasSharedWithItBecomesPrivate() = withServer {
        if (!Features.GROUPS) return@withServer
        val owner = confirmed("owner@example.com")
        val group = create(owner, "Family")
        post(owner, "p-1", PostVisibility.GROUP, group.id)

        assertEquals(
            HttpStatusCode.NoContent,
            client.delete("/groups/${group.id}") {
                bearerAuth(owner.tokens.accessToken)
            }.status,
        )

        val post = PostsLocalRepository(testDatabase()).postById("p-1")
        assertEquals(PostVisibility.PRIVATE, post?.visibility, "the post was not made private")
        assertNull(post?.group, "it still points at a group that is gone")
    }

    /** A public post that happened to name the group is not touched. */
    @Test
    fun aPublicPostIsLeftPublic() = withServer {
        if (!Features.GROUPS) return@withServer
        val owner = confirmed("owner@example.com")
        val group = create(owner, "Family")
        post(owner, "p-open", PostVisibility.PUBLIC, group.id)

        client.delete("/groups/${group.id}") { bearerAuth(owner.tokens.accessToken) }

        assertEquals(PostVisibility.PUBLIC, PostsLocalRepository(testDatabase()).postById("p-open")?.visibility)
    }

    @Test
    fun onlyTheOwnerMayCloseIt() = withServer {
        if (!Features.GROUPS) return@withServer
        val owner = confirmed("owner@example.com")
        val group = create(owner, "Family")
        val other = confirmed("other@example.com")

        val response = client.delete("/groups/${group.id}") {
            bearerAuth(other.tokens.accessToken)
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(HttpStatusCode.OK, client.get("/groups/byId/${group.id}") {
            bearerAuth(owner.tokens.accessToken)
        }.status)
    }

    @Test
    fun theGroupIsGoneFromTheOwnersList() = withServer {
        if (!Features.GROUPS) return@withServer
        val owner = confirmed("owner@example.com")
        val group = create(owner, "Family")

        client.delete("/groups/${group.id}") { bearerAuth(owner.tokens.accessToken) }

        val mine = client.get("/groups/user/${owner.user.guid}") {
            bearerAuth(owner.tokens.accessToken)
        }.bodyAsText()
        assertEquals(false, mine.contains(group.id), mine)
    }

    // — helpers —

    private suspend fun ApplicationTestBuilder.post(
        user: AuthResponse,
        guid: String,
        visibility: String,
        groupId: String?,
    ) {
        val group = groupId?.let { "\"$it\"" } ?: "null"
        val response = client.post("/posts") {
            bearerAuth(user.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody(
                """{"guid":"$guid","title":"A post","message":"words","author":"${user.user.guid}",""" +
                    """"group":$group,"likes":0,"date":"2026-08-28T10:00",""" +
                    """"visibility":"$visibility","tags":[],"language":"en"}""",
            )
        }
        assertEquals(HttpStatusCode.NoContent, response.status, response.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.create(user: AuthResponse, name: String): Group {
        val response = client.post("/groups/create") {
            bearerAuth(user.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody("""{"name":"$name"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
        return Json.decodeFromString(response.bodyAsText())
    }

}
