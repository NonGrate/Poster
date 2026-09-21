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
import com.example.poster.model.GroupMember
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Invitations, one row each, good once.
 *
 * The code used to live on the group and never change, so one posted into a
 * group chat kept working for anybody who found it, and the owner could neither
 * see that nor stop it.
 */
class GroupInviteTest {

    @Test
    fun aninviteLetsSomebodyInOnce() = withServer {
        if (!Features.GROUPS) return@withServer
        val owner = confirmed("owner@example.com")
        val group = create(owner, "Family")
        val code = invite(owner, group.id)

        val guest = confirmed("guest@example.com")
        assertEquals(HttpStatusCode.NoContent, join(guest, code))

        val second = confirmed("second@example.com")
        assertEquals(
            HttpStatusCode.NotFound,
            join(second, code),
            "a spent invite let a second person in",
        )
        assertEquals(2, members(owner, group.id).size)
    }

    /** The whole point: a code that never dies is a standing key. */
    @Test
    fun theGroupsOwnCodeNoLongerLetsAnybodyIn() = withServer {
        if (!Features.GROUPS) return@withServer
        val owner = confirmed("owner@example.com")
        val group = create(owner, "Family")

        val guest = confirmed("guest@example.com")

        assertEquals(HttpStatusCode.NotFound, join(guest, group.inviteCode))
        assertEquals(1, members(owner, group.id).size)
    }

    @Test
    fun aWithdrawnInviteIsDead() = withServer {
        if (!Features.GROUPS) return@withServer
        val owner = confirmed("owner@example.com")
        val group = create(owner, "Family")
        val code = invite(owner, group.id)

        val revoked = client.post("/groups/${group.id}/invites/$code/revoke") {
            bearerAuth(owner.tokens.accessToken)
        }
        assertEquals(HttpStatusCode.NoContent, revoked.status)

        val guest = confirmed("guest@example.com")
        assertEquals(HttpStatusCode.NotFound, join(guest, code))
    }

    @Test
    fun onlyTheOwnerCanIssueOrSeeInvites() = withServer {
        if (!Features.GROUPS) return@withServer
        val owner = confirmed("owner@example.com")
        val group = create(owner, "Family")
        val guest = confirmed("guest@example.com")
        join(guest, invite(owner, group.id))

        assertEquals(
            HttpStatusCode.Forbidden,
            client.post("/groups/${group.id}/invites") {
                bearerAuth(guest.tokens.accessToken)
            }.status,
        )
        assertEquals(
            HttpStatusCode.Forbidden,
            client.get("/groups/${group.id}/invites") {
                bearerAuth(guest.tokens.accessToken)
            }.status,
        )
    }

    /** The owner reads this list to see whether the invite they sent was used. */
    @Test
    fun theOwnerSeesWhoSpentWhich() = withServer {
        if (!Features.GROUPS) return@withServer
        val owner = confirmed("owner@example.com")
        val group = create(owner, "Family")
        val code = invite(owner, group.id)
        join(confirmed("anna@example.com"), code)

        val body = client.get("/groups/${group.id}/invites") {
            bearerAuth(owner.tokens.accessToken)
        }.bodyAsText()

        assertTrue(body.contains(code), body)
        assertTrue(body.contains("Some Body"), "the invite does not say who used it: $body")
    }

    // — members —

    @Test
    fun membersAreNamesAndNotAddresses() = withServer {
        if (!Features.GROUPS) return@withServer
        val owner = confirmed("owner@example.com")
        val group = create(owner, "Family")
        join(confirmed("anna@example.com"), invite(owner, group.id))

        val body = client.get("/groups/${group.id}/members") {
            bearerAuth(owner.tokens.accessToken)
        }.bodyAsText()

        assertTrue(body.contains("Some Body"), body)
        assertTrue(!body.contains("anna@example.com"), "an address leaked to the members list: $body")
    }

    @Test
    fun somebodyOutsideTheGroupCannotSeeItsMembers() = withServer {
        if (!Features.GROUPS) return@withServer
        val owner = confirmed("owner@example.com")
        val group = create(owner, "Family")
        val stranger = confirmed("stranger@example.com")

        assertEquals(
            HttpStatusCode.Forbidden,
            client.get("/groups/${group.id}/members") {
                bearerAuth(stranger.tokens.accessToken)
            }.status,
        )
    }

    @Test
    fun theOwnerCanRemoveSomebody() = withServer {
        if (!Features.GROUPS) return@withServer
        val owner = confirmed("owner@example.com")
        val group = create(owner, "Family")
        val anna = confirmed("anna@example.com")
        join(anna, invite(owner, group.id))

        val response = client.delete("/groups/${group.id}/members/${anna.user.guid}") {
            bearerAuth(owner.tokens.accessToken)
        }

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals(listOf(owner.user.guid), members(owner, group.id).map { it.id })
    }

    @Test
    fun aMemberCannotRemoveAnybody() = withServer {
        if (!Features.GROUPS) return@withServer
        val owner = confirmed("owner@example.com")
        val group = create(owner, "Family")
        val anna = confirmed("anna@example.com")
        val pyotr = confirmed("pyotr@example.com")
        join(anna, invite(owner, group.id))
        join(pyotr, invite(owner, group.id))

        val response = client.delete("/groups/${group.id}/members/${pyotr.user.guid}") {
            bearerAuth(anna.tokens.accessToken)
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(3, members(owner, group.id).size)
    }

    /** An owner who removes themselves leaves a room nobody can administer. */
    @Test
    fun theOwnerCannotRemoveThemselves() = withServer {
        if (!Features.GROUPS) return@withServer
        val owner = confirmed("owner@example.com")
        val group = create(owner, "Family")

        val response = client.delete("/groups/${group.id}/members/${owner.user.guid}") {
            bearerAuth(owner.tokens.accessToken)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(1, members(owner, group.id).size)
    }

    /** The owner is marked, so the app can show the controls to the right person. */
    @Test
    fun theOwnerIsMarkedInTheList() = withServer {
        if (!Features.GROUPS) return@withServer
        val owner = confirmed("owner@example.com")
        val group = create(owner, "Family")
        join(confirmed("anna@example.com"), invite(owner, group.id))

        val list = members(owner, group.id)

        assertEquals(listOf(true, false), list.sortedBy { !it.isOwner }.map { it.isOwner })
        assertEquals(owner.user.guid, list.single { it.isOwner }.id)
    }

    // — helpers —

    /**
     * Joining a room you are already in.
     *
     * This spent the invitation and then failed: the membership row has a
     * primary key of (userId, groupId), the second insert raised a
     * constraint violation, and the route had already marked the code used. So
     * the code was burned, the caller was told it was invalid, and the person
     * it was meant for could never use it. Both halves are fixed — the insert
     * ignores a duplicate, and the code is not spent by somebody already
     * inside.
     */
    @Test
    fun rejoiningDoesNotBurnTheInviteOrFail() = withServer {
        if (!Features.GROUPS) return@withServer
        val owner = confirmed("owner@example.com")
        val group = create(owner, "Family")
        val guest = confirmed("guest@example.com")
        val code = invite(owner, group.id)

        assertEquals(HttpStatusCode.NoContent, join(guest, code))
        assertEquals(
            HttpStatusCode.NoContent,
            join(guest, code),
            "a second join by the same member failed",
        )
        assertEquals(2, members(owner, group.id).size, "the member was added twice")
    }

    /** And the owner's own code still lets the person it was made for in. */
    @Test
    fun anInviteSpentByNobodyIsStillGood() = withServer {
        if (!Features.GROUPS) return@withServer
        val owner = confirmed("owner@example.com")
        val group = create(owner, "Family")
        val code = invite(owner, group.id)

        // The owner is already a member, so this must not spend it.
        assertEquals(HttpStatusCode.NoContent, join(owner, code))

        val guest = confirmed("guest@example.com")
        assertEquals(
            HttpStatusCode.NoContent,
            join(guest, code),
            "the owner opening their own link burned the invitation",
        )
        assertEquals(2, members(owner, group.id).size)
    }

    private suspend fun ApplicationTestBuilder.invite(user: AuthResponse, groupId: String): String {
        val response = client.post("/groups/$groupId/invites") {
            bearerAuth(user.tokens.accessToken)
        }
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
        return Json.decodeFromString<Map<String, String>>(response.bodyAsText()).getValue("code")
    }

    private suspend fun ApplicationTestBuilder.join(user: AuthResponse, code: String) =
        client.post("/groups/join") {
            bearerAuth(user.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody("""{"inviteCode":"$code"}""")
        }.status

    private suspend fun ApplicationTestBuilder.members(
        user: AuthResponse,
        groupId: String,
    ): List<GroupMember> = Json.decodeFromString(
        client.get("/groups/$groupId/members") {
            bearerAuth(user.tokens.accessToken)
        }.bodyAsText(),
    )

    private suspend fun ApplicationTestBuilder.create(user: AuthResponse, name: String): Group {
        val response = client.post("/groups/create") {
            bearerAuth(user.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody("""{"name":"$name"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
        return Json.decodeFromString(response.bodyAsText())
    }

    /** A pasted code becomes a group's name before anybody is asked to join it. */
    @Test
    fun aCodeNamesTheGroupItOpens() = withServer {
        if (!Features.GROUPS) return@withServer
        val owner = confirmed("owner@example.com")
        val group = create(owner, "Family")
        val guest = confirmed("guest@example.com")

        val response = client.get("/groups/byInvite/${group.inviteCode}") { bearerAuth(guest.tokens.accessToken) }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        assertEquals(group.id, Json.decodeFromString<Group>(response.bodyAsText()).id)

        assertEquals(
            HttpStatusCode.NotFound,
            client.get("/groups/byInvite/ZZZZZZZZ") { bearerAuth(guest.tokens.accessToken) }.status,
        )
        assertEquals(HttpStatusCode.Unauthorized, client.get("/groups/byInvite/${group.inviteCode}").status)
    }
}
