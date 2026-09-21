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
import com.example.poster.model.RegisterRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Co-admins: the owner can promote a member to admin, and an admin then manages
 * members and invites — but not the things reserved to the owner.
 */
class GroupAdminRoutesTest {

    @Test
    fun ownerCanPromoteAMemberAndTheListReflectsIt() = withServer {
        if (!Features.GROUPS) return@withServer
        val owner = register("Owner", "owner@example.com")
        val member = register("Member", "member@example.com")
        val group = startsGroup(owner, "Grace")
        joins(member, group, owner)

        assertFalse(members(owner, group).single { it.id == member.user.guid }.isAdmin)

        assertEquals(HttpStatusCode.NoContent, setsRole(owner, member, group, "admin"))
        assertTrue(
            members(owner, group).single { it.id == member.user.guid }.isAdmin,
            "the promoted member should read as admin",
        )

        // And back again.
        assertEquals(HttpStatusCode.NoContent, setsRole(owner, member, group, "member"))
        assertFalse(members(owner, group).single { it.id == member.user.guid }.isAdmin)
    }

    @Test
    fun anAdminManagesInvitesButCannotCloseOrChangeRoles() = withServer {
        if (!Features.GROUPS) return@withServer
        val owner = register("Owner", "owner@example.com")
        val admin = register("Admin", "admin@example.com")
        val plain = register("Plain", "plain@example.com")
        val group = startsGroup(owner, "Grace")
        joins(admin, group, owner)
        joins(plain, group, owner)
        assertEquals(HttpStatusCode.NoContent, setsRole(owner, admin, group, "admin"))

        // An admin can do the member/invite work.
        assertEquals(
            HttpStatusCode.Created,
            client.post("/groups/$group/invites") { bearerAuth(admin.tokens.accessToken) }.status,
        )
        assertEquals(
            HttpStatusCode.NoContent,
            client.delete("/groups/$group/members/${plain.user.guid}") {
                bearerAuth(admin.tokens.accessToken)
            }.status,
        )

        // But not the owner-only things.
        assertEquals(
            HttpStatusCode.Forbidden,
            client.delete("/groups/$group") { bearerAuth(admin.tokens.accessToken) }.status,
        )
        assertEquals(HttpStatusCode.Forbidden, setsRole(admin, admin, group, "member"))
    }

    @Test
    fun theOwnersRoleCannotBeChanged() = withServer {
        if (!Features.GROUPS) return@withServer
        val owner = register("Owner", "owner@example.com")
        val group = startsGroup(owner, "Grace")
        assertEquals(HttpStatusCode.BadRequest, setsRole(owner, owner, group, "admin"))
    }

    @Test
    fun anAdminCannotRemoveTheOwnerOrAnotherAdmin() = withServer {
        if (!Features.GROUPS) return@withServer
        val owner = register("Owner", "owner@example.com")
        val a = register("A", "a@example.com")
        val b = register("B", "b@example.com")
        val group = startsGroup(owner, "Grace")
        joins(a, group, owner)
        joins(b, group, owner)
        setsRole(owner, a, group, "admin")
        setsRole(owner, b, group, "admin")

        // Admin A cannot remove the owner, nor admin B — that stays the owner's.
        assertEquals(
            HttpStatusCode.Forbidden,
            client.delete("/groups/$group/members/${owner.user.guid}") {
                bearerAuth(a.tokens.accessToken)
            }.status,
        )
        assertEquals(
            HttpStatusCode.Forbidden,
            client.delete("/groups/$group/members/${b.user.guid}") {
                bearerAuth(a.tokens.accessToken)
            }.status,
        )
    }

    // — helpers —

    private suspend fun ApplicationTestBuilder.startsGroup(owner: AuthResponse, name: String): String {
        val response = client.post("/groups/create") {
            bearerAuth(owner.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody("""{"name":"$name"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
        return Json.decodeFromString<Group>(response.bodyAsText()).id
    }

    private suspend fun ApplicationTestBuilder.joins(who: AuthResponse, group: String, owner: AuthResponse) {
        val invite = client.post("/groups/$group/invites") {
            bearerAuth(owner.tokens.accessToken)
        }
        assertEquals(HttpStatusCode.Created, invite.status)
        val code = Json.decodeFromString<Map<String, String>>(invite.bodyAsText())["code"]
        val joined = client.post("/groups/join") {
            bearerAuth(who.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody("""{"userId":"${who.user.guid}","inviteCode":"$code"}""")
        }
        assertEquals(HttpStatusCode.NoContent, joined.status)
    }

    private suspend fun ApplicationTestBuilder.setsRole(
        actor: AuthResponse,
        target: AuthResponse,
        group: String,
        role: String,
    ): HttpStatusCode = client.post("/groups/$group/members/${target.user.guid}/role") {
        bearerAuth(actor.tokens.accessToken)
        contentType(ContentType.Application.Json)
        setBody("""{"role":"$role"}""")
    }.status

    private suspend fun ApplicationTestBuilder.members(
        who: AuthResponse,
        group: String,
    ): List<GroupMember> {
        val response = client.get("/groups/$group/members") { bearerAuth(who.tokens.accessToken) }
        assertEquals(HttpStatusCode.OK, response.status)
        return Json.decodeFromString(response.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.register(name: String, email: String): AuthResponse {
        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(RegisterRequest(name, "User", email, "password123")))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        confirmAddress(email)
        return Json.decodeFromString(response.bodyAsText())
    }

}
