package com.example.poster

import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import com.example.poster.auth.SocialAccount
import com.example.poster.auth.SocialVerifier
import com.example.poster.model.AccountLocalRepository
import com.example.poster.model.AuthResponse
import com.example.poster.model.RegisterRequest
import com.example.poster.model.User
import com.example.poster.model.SocialSignInRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

/**
 * Which account a provider sign-in lands on, when the email is not dependable.
 *
 * Accounts used to be found by the address the provider reported, which is
 * fine until the address moves. Two ways it moves, and both end with somebody
 * holding a second account and believing their posts are gone:
 *
 * - Apple's Hide My Email reports an address at privaterelay.appleid.com that
 *   matches nothing this app has ever seen.
 * - Any provider can report a new address for the same person, because people
 *   change their email.
 *
 * The provider's own subject does neither, so that is what is matched on.
 */
class SocialIdentityLinkingTest {

    /**
     * The same person, twice, with a different address the second time.
     *
     * This is the one that used to make two accounts. It is written with Google
     * because the stub is simpler, but it is the shape of every Apple sign-in
     * where somebody has changed their relay.
     */
    @Test
    fun theSamePersonWithANewAddressIsStillTheSamePerson() = withStub { stub ->
        val first = social("google").second
        assertNotNull(first)

        stub.email = "somebody-else@example.com"
        val second = social("google").second

        assertNotNull(second)
        assertEquals(
            first.user.guid,
            second.user.guid,
            "a changed address created a second account for one person",
        )
    }

    /** Two genuinely different people are still two accounts. */
    @Test
    fun adifferentSubjectIsADifferentPerson() = withStub { stub ->
        val first = social("google").second
        assertNotNull(first)

        stub.subject = "google-subject-2"
        stub.email = "another@example.com"
        val second = social("google").second

        assertNotNull(second)
        assertNotEquals(first.user.guid, second.user.guid, "two people were merged into one account")
    }

    /**
     * Registered the ordinary way, then signed in with the provider using the
     * same address. Still one account — and from now on it is matched by the
     * subject, so it survives the address changing afterwards.
     */
    @Test
    fun aFirstProviderSignInAttachesToTheAccountAlreadyThere() = withStub { stub ->
        val registered = register("somebody@example.com")

        val throughProvider = social("google").second
        assertNotNull(throughProvider)
        assertEquals(registered.user.guid, throughProvider.user.guid)

        // The address moves afterwards; the account does not.
        stub.email = "moved@example.com"
        val later = social("google").second
        assertEquals(registered.user.guid, later?.user?.guid, "the link did not survive the address changing")
    }

    /**
     * Somebody using Hide My Email, arriving for the first time.
     *
     * Their name would otherwise be taken from the address, and the address is
     * `a1b2c3d4@privaterelay.appleid.com` — so their family would see a serial
     * number beside a post. Empty is better: the app can ask.
     */
    @Test
    fun aHiddenAddressDoesNotBecomeSomebodysName() = withStub { stub ->
        stub.provider = "apple"
        stub.subject = "apple-subject-1"
        stub.email = "a1b2c3d4@privaterelay.appleid.com"
        // Apple never sends a name in a token, which is the whole reason the
        // address gets used as one.
        stub.name = null

        val session = social("apple").second

        assertNotNull(session)
        assertEquals("", session.user.name, "the relay address was used as a name")
    }

    /** An ordinary address still names them, which is the reason that rule exists. */
    @Test
    fun anOrdinaryAddressStillNamesSomebody() = withStub { stub ->
        stub.name = null
        val session = social("google").second

        assertEquals("somebody", session?.user?.name)
    }

    /**
     * The gap this does not close, written down so it is a decision.
     *
     * Somebody with an account, signing in with Apple and choosing Hide My
     * Email for the first time, is unrecognisable: the subject is new and the
     * address matches nothing. They get a second account. The answer is to ask
     * them at that moment whether they already have one — not to guess here.
     */
    @Test
    fun aFirstHiddenSignInStillCannotBeRecognised() = withStub { stub ->
        val registered = register("somebody@example.com")

        stub.provider = "apple"
        stub.subject = "apple-subject-1"
        stub.email = "a1b2c3d4@privaterelay.appleid.com"
        val throughApple = social("apple").second

        assertNotNull(throughApple)
        assertNotEquals(
            registered.user.guid,
            throughApple.user.guid,
            "if this now finds the account, the prompt that asks is no longer needed",
        )
    }

    /**
     * "I already have an account", answered.
     *
     * Signed in the ordinary way, holding a provider token that matches
     * nothing — which is exactly the hidden-address case. After linking, the
     * provider signs them into the account they kept, not a new one.
     */
    @Test
    fun linkingAnUnrecognisedSignInAttachesItToTheAccountYouKept() = withStub { stub ->
        val kept = register("somebody@example.com")
        stub.provider = "apple"
        stub.subject = "apple-subject-1"
        stub.email = "a1b2c3d4@privaterelay.appleid.com"
        stub.name = null

        assertEquals(HttpStatusCode.NoContent, link(kept.tokens.accessToken, "apple"))

        val throughApple = social("apple").second
        assertEquals(kept.user.guid, throughApple?.user?.guid, "linking did not take")
    }

    /** Linking twice is the same as linking once: somebody will press it again. */
    @Test
    fun linkingTheSameIdentityAgainIsHarmless() = withStub { stub ->
        val kept = register("somebody@example.com")
        stub.provider = "apple"
        stub.subject = "apple-subject-1"
        stub.email = "a1b2c3d4@privaterelay.appleid.com"

        assertEquals(HttpStatusCode.NoContent, link(kept.tokens.accessToken, "apple"))
        assertEquals(HttpStatusCode.NoContent, link(kept.tokens.accessToken, "apple"))

        assertEquals(kept.user.guid, social("apple").second?.user?.guid)
    }

    /**
     * An identity that is somebody else's is refused rather than taken.
     *
     * Two accounts both holding posts is a transfer, not a link, and doing
     * it quietly here would move somebody's posts under another name.
     */
    @Test
    fun anIdentityThatBelongsToSomebodyElseIsRefused() = withStub { stub ->
        stub.provider = "apple"
        stub.subject = "apple-subject-1"
        stub.email = "a1b2c3d4@privaterelay.appleid.com"
        stub.name = null
        val first = social("apple").second
        assertNotNull(first)

        val other = register("another@example.com")
        assertEquals(HttpStatusCode.Conflict, link(other.tokens.accessToken, "apple"))

        // And it still belongs to whoever had it.
        assertEquals(first.user.guid, social("apple").second?.user?.guid)
    }

    /** Without a session there is only one account being proved, which is not enough. */
    @Test
    fun linkingWithoutBeingSignedInIsRefused() = withServer {
        val response = client.post("/auth/social/link") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(SocialSignInRequest("apple", "any-token")))
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    /** A provider token that does not verify links nothing. */
    @Test
    fun linkingWithARefusedTokenIsRefused() = withStub { stub ->
        val kept = register("somebody@example.com")
        stub.rejectEverything = true

        assertEquals(HttpStatusCode.Unauthorized, link(kept.tokens.accessToken, "apple"))
    }

    /**
     * A banned account cannot pick up new ways to sign in.
     *
     * Banning does not reach into a phone and delete the token already on it,
     * so there is a window where somebody holds a valid session and is barred.
     * Letting them attach an identity in that window hands them a fresh route
     * back in that outlives the token.
     */
    @Test
    fun abannedAccountCannotLinkAnything() = withStub { stub ->
        val banned = register("somebody@example.com")
        AccountLocalRepository(testDatabase()).let { repository ->
            val user = repository.userById(banned.user.guid)
            assertNotNull(user)
            repository.addOrUpdateUser(user.copy(status = User.STATUS_BANNED))
        }

        stub.provider = "apple"
        stub.subject = "apple-subject-1"
        stub.email = "a1b2c3d4@privaterelay.appleid.com"

        assertEquals(HttpStatusCode.Unauthorized, link(banned.tokens.accessToken, "apple"))
    }

    // — helpers —

    /** Vouches for whoever it is told to. */
    private class ConfigurableVerifier(
        override var provider: String = "google",
        var subject: String = "google-subject-1",
        var email: String = "somebody@example.com",
        var name: String? = "Some",
        var rejectEverything: Boolean = false,
    ) : SocialVerifier {
        override val enabled = true
        override fun verify(idToken: String, nonce: String): SocialAccount {
            if (rejectEverything) throw com.example.poster.auth.SocialSignInException("refused for the test")
            return account()
        }
        private fun account() = SocialAccount(
            provider = provider,
            subject = subject,
            email = email,
            name = name,
            surname = if (name == null) null else "Body",
        )
    }

    private suspend fun ApplicationTestBuilder.social(
        provider: String,
    ): Pair<HttpStatusCode, AuthResponse?> {
        val response = client.post("/auth/social") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(SocialSignInRequest(provider, "any-token")))
        }
        val body = if (response.status == HttpStatusCode.OK) {
            Json.decodeFromString<AuthResponse>(response.bodyAsText())
        } else {
            null
        }
        return response.status to body
    }

    private suspend fun ApplicationTestBuilder.link(accessToken: String, provider: String): HttpStatusCode =
        client.post("/auth/social/link") {
            bearerAuth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(SocialSignInRequest(provider, "any-token")))
        }.status

    private suspend fun ApplicationTestBuilder.register(email: String): AuthResponse {
        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(RegisterRequest("Some", "Body", email, "password123")))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        return Json.decodeFromString(response.bodyAsText())
    }

    /**
     * The shared server with one stub reachable as either provider: the route picks
     * by name, and a test that changes the name mid-run needs the same object back.
     */
    private fun withStub(block: suspend ApplicationTestBuilder.(ConfigurableVerifier) -> Unit) {
        val stub = ConfigurableVerifier()
        val apple = object : SocialVerifier {
            override val provider = "apple"
            override val enabled = true
            override fun verify(idToken: String, nonce: String) = stub.verify(idToken, nonce)
        }
        withServer(verifiers = listOf(stub, apple)) { block(stub) }
    }
}
