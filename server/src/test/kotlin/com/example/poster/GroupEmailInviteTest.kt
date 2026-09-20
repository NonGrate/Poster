package com.example.poster

import com.example.poster.config.Features
import com.example.poster.config.AppInfo
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
import com.example.poster.model.Group
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Inviting somebody to a group by email.
 *
 * Two rules carry this feature and both fail quietly when broken. An emailed
 * invitation is locked to the address it was sent to, so a forwarded message
 * does not let a stranger in — and a wrong check there looks like a working
 * invite. And the reply is the same whether or not the address has an account,
 * so the app cannot be asked whether a given person uses a post app — a wrong
 * check there looks like helpfulness.
 */
class GroupEmailInviteTest {

    @Test
    fun theInvitedPersonCanJoinWithTheCodeTheyWereSent() = withServer { (mail) ->
        if (!Features.GROUPS) return@withServer
        val owner = confirmed("owner@example.com")
        val group = create(owner, "Family")
        val guest = confirmed("guest@example.com")

        assertEquals(HttpStatusCode.Accepted, inviteByEmail(owner, group.id, "guest@example.com"))

        val code = mail.codeFor("guest@example.com")
        assertNotNull(code, "no invitation was sent")
        assertEquals(HttpStatusCode.NoContent, join(guest, code))
    }

    /**
     * The reason emailed invitations are bound at all.
     *
     * A mailbox can be forwarded, and an invitation that travels without the
     * owner should not admit whoever the message reaches. A hand-made code is
     * different — see [aHandMadeInviteStaysUnbound] — because the owner passes
     * that one over themselves.
     */
    @Test
    fun somebodyElseCannotSpendAnInviteSentToAnotherAddress() = withServer { (mail) ->
        if (!Features.GROUPS) return@withServer
        val owner = confirmed("owner@example.com")
        val group = create(owner, "Family")
        confirmed("guest@example.com")
        val stranger = confirmed("stranger@example.com")

        inviteByEmail(owner, group.id, "guest@example.com")
        val code = assertNotNull(mail.codeFor("guest@example.com"))

        // Forbidden, not NotFound: a bound invite presented from the wrong
        // address is answered WrongAddress (403), distinct from the unified
        // Invalid (404) that unknown/spent/withdrawn codes share. Reachable only
        // by someone already holding a live code, so it tells the holder "wrong
        // account" without telling a guesser a code is real. See JoinOutcome and
        // the /groups/join route.
        assertEquals(
            HttpStatusCode.Forbidden,
            join(stranger, code),
            "a stranger with a live invite should be told the address is wrong, not that the code is invalid",
        )
        // And it is still there for the person it was meant for — WrongAddress
        // returns before the invite is spent.
        val guestAgain = signIn("guest@example.com")
        assertEquals(HttpStatusCode.NoContent, join(guestAgain, code))
    }

    /** Case is not part of an address, and nobody types their own the same way twice. */
    @Test
    fun theAddressIsMatchedWithoutCase() = withServer { (mail) ->
        if (!Features.GROUPS) return@withServer
        val owner = confirmed("owner@example.com")
        val group = create(owner, "Family")
        val guest = confirmed("guest@example.com")

        inviteByEmail(owner, group.id, "GUEST@Example.COM")
        val code = assertNotNull(mail.codeFor("GUEST@Example.COM"))

        assertEquals(HttpStatusCode.NoContent, join(guest, code))
    }

    /** The choice made when invites became single-use: a code handed over by hand is unbound. */
    @Test
    fun aHandMadeInviteStaysUnbound() = withServer {
        if (!Features.GROUPS) return@withServer
        val owner = confirmed("owner@example.com")
        val group = create(owner, "Family")
        val code = handMadeInvite(owner, group.id)

        val anybody = confirmed("anybody@example.com")
        assertEquals(HttpStatusCode.NoContent, join(anybody, code))
    }

    /**
     * The privacy rule. An account that answers differently for a registered
     * address is a way to ask whether a given person uses a post app.
     */
    @Test
    fun theAnswerIsTheSameWhetherOrNotTheAddressHasAnAccount() = withServer {
        if (!Features.GROUPS) return@withServer
        val owner = confirmed("owner@example.com")
        val group = create(owner, "Family")
        confirmed("known@example.com")

        val known = inviteByEmailRaw(owner, group.id, "known@example.com")
        val unknown = inviteByEmailRaw(owner, group.id, "stranger@example.com")

        assertEquals(known.first, unknown.first, "the status told them apart")
        assertEquals(known.second, unknown.second, "the body told them apart")
    }

    /** Somebody with no account is written to as well, and told what the app is. */
    @Test
    fun anAddressWithNoAccountIsStillInvited() = withServer { (mail) ->
        if (!Features.GROUPS) return@withServer
        val owner = confirmed("owner@example.com")
        val group = create(owner, "Family")

        inviteByEmail(owner, group.id, "nobody@example.com")

        assertEquals(1, mail.countFor("nobody@example.com"))
        assertTrue(
            mail.bodyFor("nobody@example.com")!!.contains("${AppInfo.NAME} is a quiet place"),
            "somebody with no account was not told what they are being invited to",
        )
    }

    /**
     * The address in the message has to be one a mail client will linkify.
     *
     * Nothing asserted this, and the share button next to it was handing out
     * poster://join/CODE — which no messenger turns into a link at all. The
     * emails were right by accident of using baseUrl; this is what keeps them
     * right.
     */
    @Test
    fun theInvitationCarriesAnHttpsLink() = withServer { (mail) ->
        if (!Features.GROUPS) return@withServer
        val owner = confirmed("owner@example.com")
        val group = create(owner, "Family")

        inviteByEmail(owner, group.id, "guest@example.com")
        val body = assertNotNull(mail.bodyFor("guest@example.com"))

        assertTrue(
            body.contains("https://poster.example.com/join/"),
            "the invitation did not carry an https link:\n$body",
        )
        assertTrue(
            !body.contains("poster://"),
            "the invitation carried a custom scheme a mail client will not linkify:\n$body",
        )
    }

    /** Only the owner may invite into a room. */
    @Test
    fun aMemberCannotInvite() = withServer { (mail) ->
        if (!Features.GROUPS) return@withServer
        val owner = confirmed("owner@example.com")
        val group = create(owner, "Family")
        val member = confirmed("member@example.com")
        join(member, handMadeInvite(owner, group.id))

        assertEquals(
            HttpStatusCode.Forbidden,
            inviteByEmail(member, group.id, "friend@example.com"),
        )
        assertEquals(0, mail.countFor("friend@example.com"))
    }

    /**
     * The limit that pays for answering the same either way.
     *
     * Any account can cause mail to be sent to an address of their choosing, so
     * the number one account may send in a day is capped. A refusal reads the
     * same as a send, because "too many" tells the sender their earlier ones
     * landed.
     */
    @Test
    fun oneAccountCannotMailTheWorld() = withServer { (mail) ->
        if (!Features.GROUPS) return@withServer
        val owner = confirmed("owner@example.com")
        val group = create(owner, "Family")

        repeat(25) { inviteByEmail(owner, group.id, "person$it@example.com") }

        // Counting only the invitations: registering an account sends its own
        // confirmation, and that is not what this limit is about.
        assertEquals(
            20,
            mail.sent.count { it.first.startsWith("person") },
            "the daily cap did not hold",
        )
        assertEquals(
            HttpStatusCode.Accepted,
            inviteByEmail(owner, group.id, "onemore@example.com"),
            "a refused send answered differently from an accepted one",
        )
    }

    @Test
    fun anAddressThatIsNotAnAddressIsRefused() = withServer { (mail) ->
        if (!Features.GROUPS) return@withServer
        val owner = confirmed("owner@example.com")
        val group = create(owner, "Family")

        assertEquals(
            HttpStatusCode.BadRequest,
            inviteByEmail(owner, group.id, "not an address"),
        )
        assertEquals(0, mail.countFor("not an address"))
    }

    /** The page the emailed link lands on names the room, and spends nothing. */
    @Test
    fun theLinkLandsOnAPageThatNamesTheGroup() = withServer { (mail) ->
        if (!Features.GROUPS) return@withServer
        val owner = confirmed("owner@example.com")
        val group = create(owner, "Family")
        val guest = confirmed("guest@example.com")
        inviteByEmail(owner, group.id, "guest@example.com")
        val code = assertNotNull(mail.codeFor("guest@example.com"))

        val page = client.get("/join/$code")
        assertEquals(HttpStatusCode.OK, page.status)
        assertTrue(page.bodyAsText().contains("Family"), "the page did not name the group")

        // Visiting must not have consumed it — a mail scanner opens links
        // before the person does.
        assertEquals(HttpStatusCode.NoContent, join(guest, code))
    }

    @Test
    fun aSpentCodeGivesTheSamePageAsAnInventedOne() = withServer { (mail) ->
        if (!Features.GROUPS) return@withServer
        val owner = confirmed("owner@example.com")
        val group = create(owner, "Family")
        val guest = confirmed("guest@example.com")
        inviteByEmail(owner, group.id, "guest@example.com")
        val code = assertNotNull(mail.codeFor("guest@example.com"))
        join(guest, code)

        val spent = client.get("/join/$code").bodyAsText()
        val invented = client.get("/join/ZZZZZZZZ").bodyAsText()
        assertEquals(spent, invented, "a spent code was distinguishable from an invented one")
    }

    // — helpers —

    private suspend fun ApplicationTestBuilder.inviteByEmail(
        user: AuthResponse,
        groupId: String,
        email: String,
    ) = inviteByEmailRaw(user, groupId, email).first

    private suspend fun ApplicationTestBuilder.inviteByEmailRaw(
        user: AuthResponse,
        groupId: String,
        email: String,
    ): Pair<HttpStatusCode, String> {
        val response = client.post("/groups/$groupId/invites/email") {
            bearerAuth(user.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email"}""")
        }
        return response.status to response.bodyAsText()
    }

    private suspend fun ApplicationTestBuilder.handMadeInvite(
        user: AuthResponse,
        groupId: String,
    ): String {
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

    private suspend fun ApplicationTestBuilder.create(user: AuthResponse, name: String): Group {
        val response = client.post("/groups/create") {
            bearerAuth(user.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody("""{"name":"$name"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
        return Json.decodeFromString(response.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.signIn(email: String): AuthResponse {
        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"password123"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return Json.decodeFromString(response.bodyAsText())
    }

}
