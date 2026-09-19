package com.example.poster

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
import com.example.poster.model.AuthResponse
import com.example.poster.model.Group
import com.example.poster.model.Post
import com.example.poster.model.PostVisibility
import com.example.poster.model.RegisterRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Narrowing the feed to one room.
 *
 * The feed answered "what is it about" and now also answers "who is it from".
 * These are the cases where a wrong clause does not look wrong: it returns a
 * plausible list that is slightly too long, and the post that should not be
 * in it is somebody else's.
 */
class FeedGroupFilterTest {

    @Test
    fun namingAGroupShowsOnlyThatGroupsPosts() = withServer {
        val owner = confirmed("owner@example.com")
        val family = create(owner, "Family")
        val club = create(owner, "Club")
        post(owner, "in-family", PostVisibility.GROUP, family.id, minute = 0)
        post(owner, "in-club", PostVisibility.GROUP, club.id, minute = 1)

        assertEquals(
            listOf("in-family"),
            feed(owner, groups = family.id).map { it.guid },
        )
    }

    /**
     * The point of asking. A group filter that still returned everything
     * public would answer a different question from the one the chip asks, and
     * would look right on any feed where the public posts happened to be old.
     */
    @Test
    fun aGroupFilterLeavesPublicPostsOut() = withServer {
        val owner = confirmed("owner@example.com")
        val family = create(owner, "Family")
        post(owner, "in-family", PostVisibility.GROUP, family.id, minute = 0)
        post(owner, "to-everyone", PostVisibility.PUBLIC, null, minute = 1)

        assertEquals(
            listOf("in-family"),
            feed(owner, groups = family.id).map { it.guid },
        )
    }

    /**
     * A post can be public and still carry a group id — that is what a
     * person posting to everyone from inside a room looks like, and closing a
     * room deliberately leaves such a post alone. It went to everyone, so it
     * is not the room's, and the room's filter must not claim it.
     *
     * Without this the clause needs no visibility check at all: every other
     * public post has no group to match on, so a filter missing that
     * check passes every other test here.
     */
    @Test
    fun aPublicPostFromInsideTheRoomIsNotTheRoomsPost() = withServer {
        val owner = confirmed("owner@example.com")
        val family = create(owner, "Family")
        post(owner, "shared-wider", PostVisibility.PUBLIC, family.id, minute = 0)
        post(owner, "in-family", PostVisibility.GROUP, family.id, minute = 1)

        assertEquals(
            listOf("in-family"),
            feed(owner, groups = family.id).map { it.guid },
        )
    }

    /** Several rooms are an "or", the way several tags are. */
    @Test
    fun severalGroupsAreAnyOfThem() = withServer {
        val owner = confirmed("owner@example.com")
        val family = create(owner, "Family")
        val club = create(owner, "Club")
        post(owner, "in-family", PostVisibility.GROUP, family.id, minute = 0)
        post(owner, "in-club", PostVisibility.GROUP, club.id, minute = 1)
        post(owner, "to-everyone", PostVisibility.PUBLIC, null, minute = 2)

        assertEquals(
            setOf("in-family", "in-club"),
            feed(owner, groups = "${family.id},${club.id}").map { it.guid }.toSet(),
        )
    }

    @Test
    fun noGroupIsNoFilter() = withServer {
        val owner = confirmed("owner@example.com")
        val family = create(owner, "Family")
        post(owner, "in-family", PostVisibility.GROUP, family.id, minute = 0)
        post(owner, "to-everyone", PostVisibility.PUBLIC, null, minute = 1)

        assertEquals(2, feed(owner).size)
        assertEquals(2, feed(owner, groups = "").size, "a blank value should read as no filter")
    }

    /**
     * The one that matters.
     *
     * A reader who names a room they are not in must get nothing — not the
     * room's posts. The membership check lives in the visibility clause, so
     * this is what proves the new clause did not replace it with its own,
     * looser, idea of who may read what.
     */
    @Test
    fun namingSomebodyElsesGroupReturnsNothing() = withServer {
        val owner = confirmed("owner@example.com")
        val family = create(owner, "Family")
        post(owner, "in-family", PostVisibility.GROUP, family.id, minute = 0)

        val stranger = confirmed("stranger@example.com")

        assertEquals(emptyList(), feed(stranger, groups = family.id).map { it.guid })
    }

    /** And it must not widen an unfiltered feed either. */
    @Test
    fun aStrangersUnfilteredFeedStillExcludesTheRoom() = withServer {
        val owner = confirmed("owner@example.com")
        val family = create(owner, "Family")
        post(owner, "in-family", PostVisibility.GROUP, family.id, minute = 0)
        post(owner, "to-everyone", PostVisibility.PUBLIC, null, minute = 1)

        val stranger = confirmed("stranger@example.com")

        assertEquals(listOf("to-everyone"), feed(stranger).map { it.guid })
    }

    /**
     * Both dimensions at once: "from this room" and "about this". They narrow
     * together, so a post must satisfy both to survive — an implementation
     * that ORed them would return the second post here as well.
     */
    @Test
    fun aGroupAndATagBothHaveToHold() = withServer {
        val owner = confirmed("owner@example.com")
        val family = create(owner, "Family")
        post(owner, "family-wellbeing", PostVisibility.GROUP, family.id, minute = 0, tags = listOf(TAG))
        post(owner, "family-untagged", PostVisibility.GROUP, family.id, minute = 1)

        assertEquals(
            listOf("family-wellbeing"),
            feed(owner, groups = family.id, tag = TAG).map { it.guid },
        )
    }

    /** A private post is nobody's group's, including its author's own filter. */
    @Test
    fun aPrivatePostIsNotInAnyRoom() = withServer {
        val owner = confirmed("owner@example.com")
        val family = create(owner, "Family")
        post(owner, "just-me", PostVisibility.PRIVATE, null, minute = 0)

        assertEquals(emptyList(), feed(owner, groups = family.id).map { it.guid })
    }

    // — helpers —

    private suspend fun ApplicationTestBuilder.feed(
        viewer: AuthResponse,
        groups: String? = null,
        tag: String? = null,
    ): List<Post> {
        val parts = buildList {
            groups?.let { add("groups=$it") }
            tag?.let { add("tag=$it") }
        }
        val query = if (parts.isEmpty()) "" else "?" + parts.joinToString("&")
        val response = client.get("/posts$query") { bearerAuth(viewer.tokens.accessToken) }
        assertEquals(HttpStatusCode.OK, response.status)
        return Json.decodeFromString(response.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.post(
        user: AuthResponse,
        guid: String,
        visibility: String,
        groupId: String?,
        minute: Int,
        tags: List<String> = emptyList(),
    ) {
        val group = groupId?.let { "\"$it\"" } ?: "null"
        val tagList = tags.joinToString(",") { "\"$it\"" }
        val minutes = minute.toString().padStart(2, '0')
        val response = client.post("/posts") {
            bearerAuth(user.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody(
                """{"guid":"$guid","title":"A post","message":"words","author":"${user.user.guid}",""" +
                    """"group":$group,"likes":0,"date":"2026-08-28T10:$minutes",""" +
                    """"visibility":"$visibility","tags":[$tagList],"language":"en"}""",
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

    private suspend fun ApplicationTestBuilder.confirmed(email: String): AuthResponse {
        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(RegisterRequest("Some", "Body", email, "password123")))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        confirmAddress(email)
        return Json.decodeFromString(response.bodyAsText())
    }

    private companion object {
        /** One of the curated tags, so the server will actually link it. */
        const val TAG = "wellbeing"
    }

    private fun withServer(block: suspend ApplicationTestBuilder.() -> Unit) {
        val databasePath = Files.createTempDirectory("poster-group-filter").resolve("test.db")
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
