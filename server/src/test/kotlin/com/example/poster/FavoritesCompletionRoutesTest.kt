package com.example.poster

import com.example.poster.config.Features
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.delete
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import com.example.poster.model.AuthResponse
import com.example.poster.model.Post
import com.example.poster.model.LikerList
import com.example.poster.model.RegisterRequest
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The journey the completion feature exists for: someone likes a post, the
 * author says how it went, and the person liking finds out.
 *
 * Their Liked list is where they look, and it is served by a different query
 * from the feed — which is how it came to drop the completion on the way out.
 */
class FavoritesCompletionRoutesTest {

    @Test
    fun someoneLikedSeesThatItWasAnswered() = withServer {
        if (!Features.LIKES || !Features.POST_COMPLETION) return@withServer
        val author = client.register("Author", "author@example.com")
        val postful = client.register("Postful", "postful@example.com")

        val post = Post(
            guid = "answered-post",
            title = "For my mother",
            message = "She is in hospital",
            author = author.user.guid,
            group = null,
            date = Clock.System.now().toLocalDateTime(TimeZone.UTC),
        )
        assertEquals(
            HttpStatusCode.NoContent,
            client.post("/posts") {
                bearerAuth(author.tokens.accessToken)
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(post))
            }.status,
        )

        // Someone likes it.
        assertEquals(
            HttpStatusCode.NoContent,
            client.post("/favorites/${postful.user.guid}/${post.guid}") {
                bearerAuth(postful.tokens.accessToken)
            }.status,
        )

        // The author says how it went.
        assertEquals(
            HttpStatusCode.NoContent,
            client.post("/posts/${post.guid}/complete") {
                bearerAuth(author.tokens.accessToken)
                contentType(ContentType.Application.Json)
                setBody("""{"message":"She is home and resting."}""")
            }.status,
        )

        val liking = client.favorites(postful)
        val seen = liking.single { it.guid == post.guid }
        assertNotNull(seen.completedAt, "the person liking cannot see that it was resolved")
        assertEquals("She is home and resting.", seen.completionMessage)
    }

    @Test
    fun deletingAPostRemovesItFromEveryonesLikerList() = withServer {
        if (!Features.LIKES) return@withServer
        val author = client.register("Author", "author2@example.com")
        val postful = client.register("Postful", "postful2@example.com")

        val post = Post(
            guid = "doomed-post",
            title = "Will be withdrawn",
            message = "…",
            author = author.user.guid,
            group = null,
            date = Clock.System.now().toLocalDateTime(TimeZone.UTC),
        )
        client.post("/posts") {
            bearerAuth(author.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(post))
        }
        client.post("/favorites/${postful.user.guid}/${post.guid}") {
            bearerAuth(postful.tokens.accessToken)
        }
        assertTrue(client.favorites(postful).any { it.guid == post.guid })

        assertEquals(
            HttpStatusCode.NoContent,
            client.delete("/posts/${post.guid}") {
                bearerAuth(author.tokens.accessToken)
            }.status,
        )

        assertTrue(
            client.favorites(postful).none { it.guid == post.guid },
            "a withdrawn post should not linger in someone else's list",
        )
    }

    @Test
    fun theRosterNamesOnlyThoseWhoOptedIn() = withServer {
        if (!Features.LIKES) return@withServer
        val author = client.register("Author", "roster-author@example.com")
        val named = client.register("Named", "roster-named@example.com")
        val quiet = client.register("Quiet", "roster-quiet@example.com")

        // Named opts in through the profile allowlist; Quiet does not.
        assertEquals(
            HttpStatusCode.NoContent,
            client.post("/accounts") {
                bearerAuth(named.tokens.accessToken)
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(named.user.copy(showName = true)))
            }.status,
        )

        val post = Post(
            guid = "roster-post",
            title = "A post with a crowd",
            message = "…",
            author = author.user.guid,
            group = null,
            date = Clock.System.now().toLocalDateTime(TimeZone.UTC),
        )
        client.post("/posts") {
            bearerAuth(author.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(post))
        }
        client.post("/favorites/${named.user.guid}/${post.guid}") { bearerAuth(named.tokens.accessToken) }
        client.post("/favorites/${quiet.user.guid}/${post.guid}") { bearerAuth(quiet.tokens.accessToken) }

        val response = client.get("/favorites/post/${post.guid}/people") {
            bearerAuth(author.tokens.accessToken)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val roster: LikerList = Json.decodeFromString(response.bodyAsText())
        assertEquals(2, roster.total, "both people liking are counted")
        // With feature.authors every post is signed, so the roster names everybody;
        // without it only the person who opted in, as first name + surname initial.
        if (Features.AUTHORS) {
            assertEquals(2, roster.named.size, "with authors on, everybody liking is named")
            assertTrue(roster.named.any { it.name == "Named U." }, roster.named.map { it.name }.toString())
        } else {
            assertEquals(
                listOf("Named U."),
                roster.named.map { it.name },
                "only the opted-in person is named, as first name + surname initial",
            )
        }
        assertNotNull(roster.named.first().date, "the roster carries when they started liking")
    }

    private suspend fun io.ktor.client.HttpClient.favorites(who: AuthResponse): List<Post> {
        val response = get("/favorites/me") { bearerAuth(who.tokens.accessToken) }
        assertEquals(HttpStatusCode.OK, response.status)
        return Json.decodeFromString(response.bodyAsText())
    }

    private suspend fun io.ktor.client.HttpClient.register(
        name: String,
        email: String,
    ): AuthResponse {
        val response = post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(RegisterRequest(name, "User", email, "password123")))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        // Posting needs a confirmed address; this suite is not about that.
        confirmAddress(email)
        return Json.decodeFromString(response.bodyAsText())
    }

    /** What the heart is drawn from, and it answers about you only. */
    @Test
    fun whetherYouLikedAPostIsReadable_andOnlyAboutYourself() = withServer {
        if (!Features.LIKES) return@withServer
        val author = confirmed("author@example.com")
        val fan = confirmed("fan@example.com")
        postPost(author, "p-1")
        val token = fan.tokens.accessToken

        assertEquals("false", check(fan.user.guid, token))
        assertEquals(
            HttpStatusCode.NoContent,
            client.post("/favorites/${fan.user.guid}/p-1") { bearerAuth(token) }.status,
        )
        assertEquals("true", check(fan.user.guid, token))

        assertEquals(
            HttpStatusCode.Forbidden,
            client.get("/favorites/check/${author.user.guid}/p-1") { bearerAuth(token) }.status,
            "somebody else's likes were readable",
        )
    }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.check(userId: String, token: String): String {
        val response = client.get("/favorites/check/$userId/p-1") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return response.bodyAsText()
    }
}
