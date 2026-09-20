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
import com.example.poster.model.AuthResponse
import com.example.poster.model.Language
import com.example.poster.model.RegisterRequest
import com.example.poster.model.User
import kotlinx.serialization.encodeToString
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Editing your own account.
 *
 * The handler used to take the body as the new account and put the password
 * back, which meant every other field arrived from the client — including the
 * two that decide what somebody is allowed to do.
 */
class AccountUpdateRoutesTest {

    /**
     * One POST used to be enough to become an admin, and from there the panel:
     * banning people, deleting posts, resetting anybody's password.
     */
    @Test
    fun aPersonCannotMakeThemselvesAnAdmin() = withServer {
        val member = register("Member", "member@example.com")

        val response = client.post("/accounts") {
            bearerAuth(member.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    member.user.copy(name = "Member", role = User.ROLE_ADMIN),
                ),
            )
        }

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals(User.ROLE_USER, me(member).role, "an ordinary account granted itself admin")
    }

    /** The other half of the same hole: a banned person lifting their own ban. */
    @Test
    fun aPersonCannotChangeTheirOwnStatus() = withServer {
        val member = register("Member", "member@example.com")

        client.post("/accounts") {
            bearerAuth(member.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(member.user.copy(status = User.STATUS_BANNED)))
        }

        assertEquals(User.STATUS_ACTIVE, me(member).status, "status is not the client's to set")
    }

    @Test
    fun theThingsThatAreYoursToChangeStillChange() = withServer {
        val member = register("Member", "member@example.com")

        client.post("/accounts") {
            bearerAuth(member.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    member.user.copy(
                        name = "Renamed",
                        surname = "Person",
                        languages = listOf(Language.ENGLISH, Language.RUSSIAN),
                        defaultLanguage = Language.RUSSIAN,
                    ),
                ),
            )
        }

        val updated = me(member)
        assertEquals("Renamed", updated.name)
        assertEquals("Person", updated.surname)
        assertEquals(listOf(Language.ENGLISH, Language.RUSSIAN), updated.languages)
        assertEquals(Language.RUSSIAN, updated.defaultLanguage)
    }

    /** Somebody who reads nothing would have an empty feed and no way to see why. */
    @Test
    fun turningEveryLanguageOffKeepsTheOnesYouHad() = withServer {
        val member = register("Member", "member@example.com")

        client.post("/accounts") {
            bearerAuth(member.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(member.user.copy(languages = emptyList())))
        }

        assertEquals(listOf(Language.ENGLISH), me(member).languages)
    }

    /** The language your own posts start in has to be one you read. */
    @Test
    fun aDefaultLanguageYouDoNotReadIsCorrected() = withServer {
        val member = register("Member", "member@example.com")

        client.post("/accounts") {
            bearerAuth(member.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    member.user.copy(
                        languages = listOf(Language.RUSSIAN),
                        defaultLanguage = Language.ENGLISH,
                    ),
                ),
            )
        }

        assertEquals(Language.RUSSIAN, me(member).defaultLanguage)
    }

    /**
     * Filtered on the way in *and* on the way out — this pins the way out,
     * which is the one that decides what anybody ever sees. Junk in the column
     * of an old row, or from a client sending something invented, never becomes
     * a language as far as the rest of the app is concerned.
     */
    @Test
    fun anUnknownLanguageNeverComesBack() = withServer {
        val member = register("Member", "member@example.com")

        client.post("/accounts") {
            bearerAuth(member.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    member.user.copy(languages = listOf(Language.RUSSIAN, "klingon")),
                ),
            )
        }

        assertTrue("klingon" !in me(member).languages)
        assertEquals(listOf(Language.RUSSIAN), me(member).languages)
    }

    /**
     * The feed obeys the languages somebody reads. This is the whole point of
     * the field, so it is pinned on the route rather than left to the screens.
     */
    @Test
    fun theFeedShowsOnlyLanguagesTheViewerReads() = withServer {
        val author = register("Author", "author@example.com")
        post(author, "In English", Language.ENGLISH)
        post(author, "По-русски", Language.RUSSIAN)

        val reader = register("Reader", "reader@example.com")
        setLanguages(reader, listOf(Language.RUSSIAN))

        assertEquals(setOf("По-русски"), titles(reader))
    }

    @Test
    fun readingBothLanguagesShowsBoth() = withServer {
        val author = register("Author", "author@example.com")
        post(author, "In English", Language.ENGLISH)
        post(author, "По-русски", Language.RUSSIAN)

        val reader = register("Reader", "reader@example.com")
        setLanguages(reader, listOf(Language.ENGLISH, Language.RUSSIAN))

        assertEquals(setOf("In English", "По-русски"), titles(reader))
    }

    /**
     * Your own posts are always yours to see. Otherwise writing one in a
     * language you then stop reading would take it out of My Posts, which
     * reads as the post having been lost.
     */
    @Test
    fun yourOwnPostsComeBackWhateverLanguageTheyAreIn() = withServer {
        val author = register("Author", "author@example.com")
        post(author, "Mine, in English", Language.ENGLISH)
        setLanguages(author, listOf(Language.RUSSIAN))

        assertEquals(setOf("Mine, in English"), titles(author))
    }

    private suspend fun ApplicationTestBuilder.setLanguages(
        who: AuthResponse,
        languages: List<String>,
    ) {
        val response = client.post("/accounts") {
            bearerAuth(who.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    who.user.copy(languages = languages, defaultLanguage = languages.first()),
                ),
            )
        }
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    private suspend fun ApplicationTestBuilder.post(
        author: AuthResponse,
        title: String,
        language: String,
    ) {
        val post = com.example.poster.model.Post(
            guid = title,
            title = title,
            message = "message",
            author = author.user.guid,
            group = null,
            date = kotlinx.datetime.Clock.System.now()
                .toLocalDateTime(kotlinx.datetime.TimeZone.UTC),
            language = language,
        )
        assertEquals(
            HttpStatusCode.NoContent,
            client.post("/posts") {
                bearerAuth(author.tokens.accessToken)
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(post))
            }.status,
        )
    }

    private suspend fun ApplicationTestBuilder.titles(viewer: AuthResponse): Set<String> {
        val response = client.get("/posts") { bearerAuth(viewer.tokens.accessToken) }
        assertEquals(HttpStatusCode.OK, response.status)
        return Json.decodeFromString<List<com.example.poster.model.Post>>(response.bodyAsText())
            .map { it.title }
            .toSet()
    }

    private suspend fun ApplicationTestBuilder.me(who: AuthResponse): User {
        val response = client.get("/auth/me") { bearerAuth(who.tokens.accessToken) }
        assertEquals(HttpStatusCode.OK, response.status)
        return Json.decodeFromString(response.bodyAsText())
    }

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
