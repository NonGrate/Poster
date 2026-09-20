package com.example.poster

import kotlin.test.assertTrue
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.example.poster.admin.AdminConfig
import com.example.poster.admin.adminRoutes
import com.example.poster.admin.configureAdminSessions
import com.example.poster.auth.Argon2PasswordHasher
import com.example.poster.auth.AttemptThrottle
import com.example.poster.comments.CommentsRepository
import com.example.poster.config.Features
import com.example.poster.model.AccountLocalRepository
import com.example.poster.model.AuthResponse
import com.example.poster.model.CrashRepository
import com.example.poster.model.EventRepository
import com.example.poster.model.FeedbackRepository
import com.example.poster.model.Group
import com.example.poster.model.GroupLocalRepository
import com.example.poster.model.ModerationRepository
import com.example.poster.model.ReportsRepository
import com.example.poster.model.TagLocalRepository
import com.example.poster.model.User
import com.example.poster.model.UserGroupLocalRepository
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What the routes refuse, for the guards that were missing rather than wrong.
 *
 * Each of these was reachable: a post could be dropped into a room its author
 * had never joined, a post nobody could see could still be liked and counted,
 * an owner could walk out of their own group, and the panel's login was a way
 * around the allowance the API's login spends.
 */
class RouteGuardsTest {

    @Test
    fun aPostCannotBeDroppedIntoARoomItsAuthorIsNotIn() = withServer {
        if (!Features.GROUPS) return@withServer
        val outsider = confirmed("outsider@example.com")
        GroupLocalRepository(testDatabase())
            .addOrUpdateGroup(Group(id = "theirs", name = "Theirs", inviteCode = "THEIRCDE"))

        assertEquals(
            HttpStatusCode.Forbidden,
            postPost(outsider, "injected", visibility = "group", group = "theirs", expect = null).status,
            "a stranger put a post in front of every member of a group",
        )
        // And a group post with no room at all is a client with a bug.
        assertEquals(
            HttpStatusCode.BadRequest,
            postPost(outsider, "roomless", visibility = "group", group = null, expect = null).status,
        )
    }

    @Test
    fun aVisibilityTheServerDoesNotKnowIsRefused() = withServer {
        if (!Features.POST_VISIBILITY) return@withServer
        val writer = confirmed("writer@example.com")
        assertEquals(
            HttpStatusCode.BadRequest,
            postPost(writer, "odd", visibility = "everyone", expect = null).status,
            "an unknown visibility was stored, and matches no clause in the feed",
        )
    }

    @Test
    fun likingAPostYouCannotSeeIsNotFound() = withServer {
        if (!Features.LIKES) return@withServer
        val reader = confirmed("reader@example.com")
        val writer = confirmed("writer@example.com")
        postPost(writer, "hidden", visibility = "private")

        val me = reader.user.guid
        val token = reader.tokens.accessToken
        assertEquals(
            HttpStatusCode.NotFound,
            client.post("/favorites/$me/hidden") { bearerAuth(token) }.status,
        )
        assertEquals(
            HttpStatusCode.NotFound,
            client.delete("/favorites/$me/hidden") { bearerAuth(token) }.status,
        )
        assertEquals(
            HttpStatusCode.NotFound,
            client.get("/favorites/check/$me/hidden") { bearerAuth(token) }.status,
        )
        assertEquals(
            HttpStatusCode.NotFound,
            client.get("/favorites/count/hidden") { bearerAuth(token) }.status,
            "a stranger could count the likes on a private post",
        )
        assertEquals(
            HttpStatusCode.NotFound,
            client.get("/favorites/post/hidden/people") { bearerAuth(token) }.status,
        )
    }

    @Test
    fun anOwnerCannotLeaveTheirOwnGroup() = withServer {
        if (!Features.GROUPS) return@withServer
        val owner = confirmed("owner@example.com")
        val created = client.post("/groups/create") {
            bearerAuth(owner.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Home"}""")
        }
        assertEquals(HttpStatusCode.Created, created.status, created.bodyAsText())
        val id = Json.decodeFromString<Group>(created.bodyAsText()).id

        assertEquals(
            HttpStatusCode.Conflict,
            leave(owner, id),
            "the owner walked out and left a room nobody can administer",
        )
        // And somebody who was never in it is told nothing about whether it exists.
        val stranger = confirmed("stranger@example.com")
        assertEquals(HttpStatusCode.NotFound, leave(stranger, id))
        assertEquals(HttpStatusCode.NotFound, leave(stranger, "no-such-group"))
    }

    /**
     * The panel's form checks an ordinary account's password, so without a
     * limit it was a second, unlimited five-guesses-a-window against exactly
     * the accounts that can ban people.
     */
    @Test
    fun theAdminLoginSpendsTheSameAllowanceTheApiLoginDoes() = testApplication {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        PostDatabase.Schema.create(driver)
        val database = PostDatabase(driver)
        val accounts = AccountLocalRepository(database)
        val hasher = Argon2PasswordHasher()
        accounts.addOrUpdateUser(
            User(
                guid = "admin-1", name = "T", surname = "T", email = "admin@example.com",
                passwordHash = hasher.hash("password123"), photo = null, role = User.ROLE_ADMIN,
            ),
        )
        application {
            configureAdminSessions(AdminConfig(bootstrapEmail = null, enabled = true, signKey = "k".repeat(32)))
            routing {
                adminRoutes(
                    accounts, ModerationRepository(database), hasher,
                    GroupLocalRepository(database), TagLocalRepository(database),
                    UserGroupLocalRepository(database),
                    revokeSessions = {},
                    throttle = AttemptThrottle(),
                    crashes = CrashRepository(driver),
                    reports = ReportsRepository(database),
                    feedback = FeedbackRepository(database),
                    events = EventRepository(driver),
                    comments = CommentsRepository(database),
                )
            }
        }
        suspend fun attempt(password: String) = client.post("/admin/login") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("email=admin%40example.com&password=$password")
        }.status

        repeat(5) { assertEquals(HttpStatusCode.Unauthorized, attempt("wrong"), "guess ${it + 1}") }
        assertEquals(HttpStatusCode.TooManyRequests, attempt("wrong"))
        // The real password too: an allowance that the right password reopens
        // is no allowance at all.
        assertEquals(HttpStatusCode.TooManyRequests, attempt("password123"))
    }

    // --- helpers -----------------------------------------------------------

    private suspend fun ApplicationTestBuilder.leave(user: AuthResponse, groupId: String): HttpStatusCode =
        client.delete("/groups/leave") {
            bearerAuth(user.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody("""{"groupId":"$groupId"}""")
        }.status

    /**
     * The invite code is the whole of the security on an invite-only group, so
     * it travels only to the people who may hand it out. It used to come back
     * to every member on every group they are in.
     */
    @Test
    fun aPlainMemberIsNotToldTheInviteCode() = withServer {
        if (!Features.GROUPS) return@withServer
        val owner = confirmed("owner@example.com")
        val created = client.post("/groups/create") {
            bearerAuth(owner.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Home"}""")
        }
        assertEquals(HttpStatusCode.Created, created.status, created.bodyAsText())
        val group = Json.decodeFromString<Group>(created.bodyAsText())
        assertTrue(group.inviteCode.isNotBlank(), "the owner was not given a code to hand out")

        val member = confirmed("member@example.com")
        val invited = client.post("/groups/${group.id}/invites") { bearerAuth(owner.tokens.accessToken) }
        val code = Json.decodeFromString<Map<String, String>>(invited.bodyAsText()).getValue("code")
        val joined = client.post("/groups/join") {
            bearerAuth(member.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody("""{"inviteCode":"$code"}""")
        }
        assertEquals(HttpStatusCode.NoContent, joined.status, joined.bodyAsText())

        assertEquals("", byId(member, group.id).inviteCode, "a member was handed the key to the group")
        assertEquals(group.inviteCode, byId(owner, group.id).inviteCode, "the owner cannot see the code to pass on")
        assertEquals(listOf(""), mine(member).map { it.inviteCode })
        assertEquals(listOf(group.inviteCode), mine(owner).map { it.inviteCode })
    }

    private suspend fun ApplicationTestBuilder.byId(user: AuthResponse, groupId: String): Group {
        val response = client.get("/groups/byId/$groupId") { bearerAuth(user.tokens.accessToken) }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return Json.decodeFromString(response.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.mine(user: AuthResponse): List<Group> =
        Json.decodeFromString(
            client.get("/groups/user/${user.user.guid}") { bearerAuth(user.tokens.accessToken) }.bodyAsText(),
        )
}
