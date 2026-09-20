package com.example.poster

import com.example.poster.config.Features
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
import com.example.poster.model.ApiError
import com.example.poster.model.Group
import com.example.poster.model.GroupLocalRepository
import com.example.poster.model.RegisterRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Creating a group.
 *
 * Renaming and deleting stay the admin panel's — the endpoints for those let
 * any signed-in person overwrite a group's invite code. This one takes a
 * name and touches only the row it just made.
 */
class CreateGroupTest {

    @Test
    fun creatingOneJoinsYouToIt() = withServer {
        if (!Features.GROUPS) return@withServer
        val user = register("owner@example.com")

        val group = create(user, "Tuesday evening group")

        assertEquals("Tuesday evening group", group.name)
        assertEquals(user.user.guid, group.owner)
        assertEquals(
            listOf(group.id),
            myGroups(user).map { it.id },
            "the creator should be in the group they made",
        )
    }

    /** A code the client chose is a code somebody else can guess. */
    @Test
    fun theServerPicksTheInviteCode() = withServer {
        if (!Features.GROUPS) return@withServer
        val user = register("owner@example.com")

        val first = create(user, "One")
        val second = create(user, "Two")

        assertTrue(first.inviteCode.length >= 6, "too short to be unguessable: ${first.inviteCode}")
        assertNotEquals(first.inviteCode, second.inviteCode)
        assertTrue(
            first.inviteCode.none { it in "AEIOU01IL" },
            "the code should avoid letters that spell things or misread aloud: ${first.inviteCode}",
        )
    }

    @Test
    fun thereIsALimitOnHowManyOneAccountMayCreate() = withServer {
        if (!Features.GROUPS) return@withServer
        val user = register("owner@example.com")
        repeat(5) { create(user, "Group $it") }

        val response = post(user, "Group six")

        assertEquals(HttpStatusCode.Conflict, response.first)
        assertEquals(5, myGroups(user).size)
    }

    /** The cap is per account, not global — one person filling up is not everybody. */
    @Test
    fun theLimitIsPerAccount() = withServer {
        if (!Features.GROUPS) return@withServer
        val first = register("one@example.com")
        repeat(5) { create(first, "Group $it") }
        val second = register("two@example.com")

        val group = create(second, "Somewhere else")

        assertEquals(second.user.guid, group.owner)
    }

    @Test
    fun aBlankNameIsRefused() = withServer {
        if (!Features.GROUPS) return@withServer
        val user = register("owner@example.com")

        assertEquals(HttpStatusCode.BadRequest, post(user, "   ").first)
        assertTrue(myGroups(user).isEmpty())
    }

    @Test
    fun aNameTooLongForARowIsRefused() = withServer {
        if (!Features.GROUPS) return@withServer
        val user = register("owner@example.com")

        assertEquals(HttpStatusCode.BadRequest, post(user, "x".repeat(61)).first)
        assertTrue(myGroups(user).isEmpty())
    }

    /** Surrounding space is a typo, not a different group. */
    @Test
    fun theNameIsTrimmed() = withServer {
        if (!Features.GROUPS) return@withServer
        val user = register("owner@example.com")

        assertEquals("Sunday group", create(user, "  Sunday group  ").name)
    }

    /**
     * A group is a room other people are invited into, so it takes the same
     * confirmed address that writing a post does. An unconfirmed account
     * could make them, which meant anybody with a typo'd address — or a
     * throwaway one nobody could reach — could put a room in front of people.
     */
    @Test
    fun anUnconfirmedAddressCannotMakeAGroup() = withServer {
        if (!Features.GROUPS) return@withServer
        val user = registerUnconfirmed("unconfirmed@example.com")

        val response = client.post("/groups/create") {
            bearerAuth(user.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Ours"}""")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue(
            response.bodyAsText().contains(ApiError.EMAIL_NOT_VERIFIED),
            "the app needs the code to know which screen to show: ${response.bodyAsText()}",
        )
        assertTrue(myGroups(user).isEmpty(), "a group was made anyway")
    }

    /** And confirming it is what opens the door. */
    @Test
    fun aConfirmedAddressCan() = withServer {
        if (!Features.GROUPS) return@withServer
        val user = registerUnconfirmed("later@example.com")
        confirmAddress("later@example.com")

        assertEquals("Ours", create(user, "Ours").name)
    }

    @Test
    fun creatingNeedsASession() = withServer {
        if (!Features.GROUPS) return@withServer
        val response = client.post("/groups/create") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Anonymous"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    /**
     * The group the admin panel made has no owner, and counting it against
     * somebody would spend a person's allowance on a row they never made.
     */
    @Test
    fun groupsWithNoOwnerCountAgainstNobody() = withServer {
        if (!Features.GROUPS) return@withServer
        val user = register("owner@example.com")
        assertNull(create(user, "Mine").owner?.takeIf { it != user.user.guid })

        val all = allGroups()

        assertEquals(1, all.count { it.owner == user.user.guid })
    }

    /**
     * A group outlives the person who made it: other people are in it and
     * their posts are in it. What must not outlive them is the claim of
     * ownership — a row pointing at a user who no longer exists, still counting
     * against a cap nobody can reach.
     */
    @Test
    fun deletingAnAccountLeavesItsGroupsWithoutAnOwner() = withServer {
        if (!Features.GROUPS) return@withServer
        val user = register("owner@example.com")
        val group = create(user, "Theirs")

        val deleted = client.post("/delete-account") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("email=owner%40example.com&password=password123&confirm=on")
        }
        assertEquals(HttpStatusCode.OK, deleted.status)

        val survivor = register("someone@example.com")
        val stored = allGroups().single { it.id == group.id }
        assertEquals("Theirs", stored.name, "the group went with the account")
        assertNull(stored.owner, "the group still claims a user who no longer exists")
    }

    // — helpers —

    private suspend fun ApplicationTestBuilder.post(
        user: AuthResponse,
        name: String,
    ): Pair<HttpStatusCode, String> {
        val response = client.post("/groups/create") {
            bearerAuth(user.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(mapOf("name" to name)))
        }
        return response.status to response.bodyAsText()
    }

    private suspend fun ApplicationTestBuilder.create(user: AuthResponse, name: String): Group {
        val (status, body) = post(user, name)
        assertEquals(HttpStatusCode.Created, status, body)
        return Json.decodeFromString(body)
    }

    private suspend fun ApplicationTestBuilder.myGroups(user: AuthResponse): List<Group> =
        Json.decodeFromString(
            client.get("/groups/user/${user.user.guid}") {
                bearerAuth(user.tokens.accessToken)
            }.bodyAsText(),
        )

    /** Every group there is, read from the store: no route lists them any more. */
    private fun allGroups(): List<Group> = GroupLocalRepository(testDatabase()).allGroups()

    /**
     * Registers and confirms, which is what every test here wants — making a
     * group needs a confirmed address, and none of these are about that.
     * The one that is uses [registerUnconfirmed].
     */
    private suspend fun ApplicationTestBuilder.register(email: String): AuthResponse =
        registerUnconfirmed(email).also { confirmAddress(email) }

    private suspend fun ApplicationTestBuilder.registerUnconfirmed(email: String): AuthResponse {
        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(RegisterRequest("Some", "Body", email, "password123")))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        return Json.decodeFromString(response.bodyAsText())
    }

}
