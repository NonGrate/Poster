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
import com.example.poster.model.UserGroupLocalRepository
import com.example.poster.model.GroupLocalRepository
import com.example.poster.model.RegisterRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A group belongs to whoever runs the app, not to whoever is signed in.
 *
 * Both of these were possible: any account could overwrite any group —
 * including the invite code, which is the whole of how somebody gets in — and
 * any account could delete one, which takes every post in it out of sight of
 * its members. The routes are gone rather than guarded, because nothing in the
 * app ever used them.
 */
class GroupOwnershipRoutesTest {

    @Test
    fun anAccountCannotCreateOrOverwriteAGroup() = withServer {
        if (!Features.GROUPS) return@withServer
        GroupLocalRepository(testDatabase()).addOrUpdateGroup(
            Group(id = "home", name = "Home", inviteCode = "HOME_SECRET"),
        )
        val stranger = register("Stranger", "stranger@example.com")

        val response = client.post("/groups") {
            bearerAuth(stranger.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    Group(id = "home", name = "Hijacked", inviteCode = "KNOWN_TO_ME"),
                ),
            )
        }

        assertTrue(
            response.status == HttpStatusCode.NotFound || response.status == HttpStatusCode.MethodNotAllowed,
            "creating a group is still reachable: ${response.status}",
        )
        val groups = groups()
        assertEquals("Home", groups.single { it.id == "home" }.name)
        assertEquals("HOME_SECRET", groups.single { it.id == "home" }.inviteCode)
    }

    /**
     * Closing a group is the owner's alone.
     *
     * This used to assert the route did not exist, because the fix at the time
     * was to remove it: the endpoint let any signed-in person delete a
     * group for everybody. It exists again, guarded — the property being
     * protected was never "there is no such route" but "a stranger cannot do
     * this", and that is what is asserted now.
     *
     * A group with no owner — the ones the admin panel makes — belongs to
     * nobody, so nobody passes the check.
     */
    @Test
    fun anAccountCannotDeleteSomebodyElsesGroup() = withServer {
        if (!Features.GROUPS) return@withServer
        GroupLocalRepository(testDatabase()).addOrUpdateGroup(
            Group(id = "home", name = "Home", inviteCode = "HOME_SECRET"),
        )
        val stranger = register("Stranger", "stranger@example.com")

        val response = client.delete("/groups/home") {
            bearerAuth(stranger.tokens.accessToken)
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue(groups().any { it.id == "home" }, "the group was deleted")
    }

    /** Joining with a code, and reading the rooms you are in, are still everybody's. */
    @Test
    fun readingGroupsStillWorks() = withServer {
        if (!Features.GROUPS) return@withServer
        GroupLocalRepository(testDatabase()).addOrUpdateGroup(
            Group(id = "home", name = "Home", inviteCode = "HOME_SECRET"),
        )
        val member = register("Member", "member@example.com")

        // Joining takes an invite now: the group's own code is a record of
        // what it used to be and no longer lets anybody in.
        UserGroupLocalRepository(testDatabase())
            .createInvite("home", "seed", "ONEINVITE", "2026-08-28T10:00:00Z")
        assertEquals(
            HttpStatusCode.NoContent,
            client.post("/groups/join") {
                bearerAuth(member.tokens.accessToken)
                contentType(ContentType.Application.Json)
                setBody("""{"userId":"${member.user.guid}","inviteCode":"ONEINVITE"}""")
            }.status,
        )
        val mine: List<Group> = Json.decodeFromString(
            client.get("/groups/user/${member.user.guid}") {
                bearerAuth(member.tokens.accessToken)
            }.bodyAsText(),
        )
        assertTrue(mine.any { it.id == "home" }, "the room they just joined is not in their list")
    }

    /**
     * A post's tags are the author's. With filtering, retagging one also
     * changes who finds it.
     */
    @Test
    fun anAccountCannotChangeSomebodyElsesPostTags() = withServer {
        if (!Features.TAGS) return@withServer
        val author = register("Author", "author@example.com")
        val stranger = register("Stranger", "stranger@example.com")
        postPost(author, "p-1", message = "message", tags = listOf("health"), date = "2026-08-18T12:00")

        val added = client.post("/tags/addToPost") {
            bearerAuth(stranger.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody("""{"postId":"p-1","tagId":"war"}""")
        }
        val stripped = client.delete("/tags/removeAllFromPost/p-1") {
            bearerAuth(stranger.tokens.accessToken)
        }

        assertTrue(added.status == HttpStatusCode.NotFound || added.status == HttpStatusCode.MethodNotAllowed)
        assertTrue(stripped.status == HttpStatusCode.NotFound || stripped.status == HttpStatusCode.MethodNotAllowed)
        assertEquals(listOf("health"), myPosts(author).single { it.guid == "p-1" }.tags)
    }

    /** Every account, with every email, was one request away. */
    @Test
    fun theListOfEveryAccountIsNotReadable() = withServer {
        register("Author", "author@example.com")
        val stranger = register("Stranger", "stranger@example.com")

        val response = client.get("/accounts") { bearerAuth(stranger.tokens.accessToken) }

        assertTrue(
            response.status == HttpStatusCode.NotFound || response.status == HttpStatusCode.MethodNotAllowed,
            "every account is still listable: ${response.status}",
        )
    }

    @Test
    fun somebodyElsesAccountIsNotReadable() = withServer {
        val author = register("Author", "author@example.com")
        val stranger = register("Stranger", "stranger@example.com")

        val theirs = client.get("/accounts/byId/${author.user.guid}") {
            bearerAuth(stranger.tokens.accessToken)
        }
        val mine = client.get("/accounts/byId/${stranger.user.guid}") {
            bearerAuth(stranger.tokens.accessToken)
        }

        assertEquals(HttpStatusCode.Forbidden, theirs.status, "somebody else's account was readable")
        assertEquals(HttpStatusCode.OK, mine.status, "your own account should still be readable")
    }

    /** A list of people beside a subject they did not choose to be listed under. */
    @Test
    fun whoIsLikedForAPostIsNotReadable() = withServer {
        if (!Features.LIKES) return@withServer
        val author = register("Author", "author@example.com")
        postPost(author, "p-1", message = "message", date = "2026-08-18T12:00")

        val response = client.get("/favorites/users/p-1") { bearerAuth(author.tokens.accessToken) }

        assertTrue(
            response.status == HttpStatusCode.NotFound || response.status == HttpStatusCode.MethodNotAllowed,
            "the list of people liking a post is still readable: ${response.status}",
        )
        // The number still is: that is what a card shows.
        assertEquals(
            HttpStatusCode.OK,
            client.get("/favorites/count/p-1") { bearerAuth(author.tokens.accessToken) }.status,
        )
    }

    private suspend fun ApplicationTestBuilder.myPosts(
        who: AuthResponse,
    ): List<com.example.poster.model.Post> {
        val response = client.get("/posts") { bearerAuth(who.tokens.accessToken) }
        assertEquals(HttpStatusCode.OK, response.status)
        return Json.decodeFromString(response.bodyAsText())
    }

    /**
     * Every group there is, read from the store. Listing them was a route
     * until it turned out to answer any signed-in caller with every group's
     * invite code; these tests are about what a stranger cannot change, so
     * they check the rows themselves.
     */
    private fun groups(): List<Group> = GroupLocalRepository(testDatabase()).allGroups()

    private suspend fun ApplicationTestBuilder.register(name: String, email: String): AuthResponse {
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
