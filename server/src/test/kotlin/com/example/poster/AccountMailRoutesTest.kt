package com.example.poster

import com.example.poster.config.Features
import com.example.poster.config.AppInfo
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import com.example.poster.model.AuthResponse
import com.example.poster.model.RegisterRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifying an address, and getting back into an account you are locked out of.
 *
 * The tests that matter here are about what these endpoints *refuse to say*.
 * Both take an address from anybody, so an answer that differs by whether the
 * address is registered turns them into a way of asking which of somebody's
 * family uses this app.
 */
class AccountMailRoutesTest {

    @Test
    fun registeringSendsAVerificationLinkThatWorksOnce() = withServer { (mail) ->
        val user = register("member@example.com")

        val link = mail.linkFor("member@example.com")
        assertTrue(link != null, "registering sent no verification email")

        assertEquals(HttpStatusCode.NoContent, verify(link!!))
        assertTrue(me(user).verifiedAt != null, "following the link verified nothing")

        assertEquals(HttpStatusCode.BadRequest, verify(link), "the link worked a second time")
    }

    /**
     * Both messages have to carry an address a mail client will linkify, and
     * one the app will be offered for.
     *
     * The manifest registers /verify and /reset on poster.example.com as verified
     * App Links; a custom scheme in an email would neither linkify nor route.
     * Nothing checked this until an invitation went out as poster:// and
     * arrived as untappable grey text.
     */
    @Test
    fun bothMessagesCarryAnHttpsLink() = withServer { (mail) ->
        register("member@example.com")
        val verification = mail.messageFor("member@example.com")?.second
        assertTrue(
            verification?.contains("https://poster.example.com/verify?token=") == true,
            "the verification email did not carry an https link:\n$verification",
        )

        forgot("member@example.com")
        val reset = mail.messageFor("member@example.com", index = 1)?.second
        assertTrue(
            reset?.contains("https://poster.example.com/reset?token=") == true,
            "the reset email did not carry an https link:\n$reset",
        )
    }

    @Test
    fun anInventedVerificationLinkIsRefused() = withServer {
        register("member@example.com")

        assertEquals(HttpStatusCode.BadRequest, verify("not-a-real-token"))
    }

    /**
     * A mail provider having a bad afternoon must not stop somebody joining.
     * The account is the point; the email is a nicety.
     */
    @Test
    fun registrationSucceedsEvenWhenTheEmailCannotBeSent() = withServer(mail = RecordedMail(works = false)) {
        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(RegisterRequest("Member", "User", "member@example.com", "password123")))
        }

        assertEquals(HttpStatusCode.OK, response.status, "a failed send took the registration with it")
    }

    @Test
    fun aResetLinkSetsANewPasswordAndTheOldOneStopsWorking() = withServer { (mail) ->
        register("member@example.com")
        assertEquals(HttpStatusCode.NoContent, forgot("member@example.com"))

        val link = mail.linkFor("member@example.com", index = 1)
        assertTrue(link != null, "no reset email was sent")
        assertEquals(HttpStatusCode.NoContent, reset(link!!, "a-brand-new-password"))

        assertEquals(HttpStatusCode.OK, login("member@example.com", "a-brand-new-password").status)
        assertEquals(
            HttpStatusCode.Unauthorized,
            login("member@example.com", "password123").status,
            "the old password still works",
        )
    }

    /** Otherwise this endpoint answers "does this person have an account?" */
    @Test
    fun askingToResetAnAddressNobodyHasLooksIdentical() = withServer { (mail) ->
        register("member@example.com")

        val known = forgot("member@example.com")
        val unknown = forgot("nobody@example.com")

        assertEquals(known, unknown, "the answer differed by whether the address exists")
        assertEquals(HttpStatusCode.NoContent, unknown)
        assertTrue(mail.linkFor("nobody@example.com") == null, "an unknown address was mailed")
    }

    /** Two minutes between messages, so this cannot be pointed at an inbox. */
    @Test
    fun theSameAddressCannotBeMailedRepeatedly() = withServer { (mail) ->
        register("member@example.com")
        val afterRegistration = mail.countFor("member@example.com")

        repeat(5) { forgot("member@example.com") }

        assertEquals(
            afterRegistration + 1,
            mail.countFor("member@example.com"),
            "five requests should send one reset email, not five",
        )
    }

    @Test
    fun aResetLinkCannotBeUsedTwice() = withServer { (mail) ->
        register("member@example.com")
        forgot("member@example.com")
        val link = mail.linkFor("member@example.com", index = 1)!!
        assertEquals(HttpStatusCode.NoContent, reset(link, "a-brand-new-password"))

        assertEquals(HttpStatusCode.BadRequest, reset(link, "another-password-again"))
    }

    @Test
    fun aResetWillNotSetAPasswordTooShortToBeOne() = withServer { (mail) ->
        register("member@example.com")
        forgot("member@example.com")
        val link = mail.linkFor("member@example.com", index = 1)!!

        val response = reset(link, "short")

        assertEquals(HttpStatusCode.BadRequest, response)
        assertEquals(HttpStatusCode.OK, login("member@example.com", "password123").status,
            "the old password should still work after a refused reset")
    }

    /**
     * Somebody resetting a password often believes another person has it.
     * Leaving that person signed in answers the wrong half of the problem.
     */
    @Test
    fun aResetSignsOutEveryDeviceThatWasSignedIn() = withServer { (mail) ->
        val user = register("member@example.com")
        forgot("member@example.com")
        val link = mail.linkFor("member@example.com", index = 1)!!

        reset(link, "a-brand-new-password")

        val refreshed = client.post("/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"${user.tokens.refreshToken}"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, refreshed.status, "an old session survived the reset")
    }

    /** Reaching the inbox is the proof; a reset is as good as following the link. */
    @Test
    fun resettingAPasswordAlsoVerifiesTheAddress() = withServer { (mail) ->
        val user = register("member@example.com")
        assertFalse(me(user).verifiedAt != null)
        forgot("member@example.com")

        reset(mail.linkFor("member@example.com", index = 1)!!, "a-brand-new-password")

        assertTrue(
            me(user, password = "a-brand-new-password").verifiedAt != null,
            "reaching the inbox did not count as proof",
        )
    }

    /**
     * The gate, enforced by the server rather than asked of the client.
     *
     * A modified app can ignore a field saying "you are not verified"; it can
     * ignore a status code just as easily. The only thing that holds is the
     * server declining to write, which is what this pins.
     */
    @Test
    fun anUnconfirmedAccountCannotShareAPost() = withServer { (mail) ->
        if (!Features.EMAIL_VERIFICATION_REQUIRED) return@withServer
        val user = register("member@example.com")

        val response = postPost(user, "p-1", title = "A post", message = "words", date = "2026-08-18T12:00", expect = null)

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue(
            response.bodyAsText().contains("email_not_verified"),
            "the app cannot tell this apart from any other refusal: ${response.bodyAsText()}",
        )
    }

    @Test
    fun confirmingTheAddressLetsThemShareOne() = withServer { (mail) ->
        val user = register("member@example.com")
        verify(mail.linkFor("member@example.com")!!)

        postPost(user, "p-1", title = "A post", message = "words", date = "2026-08-18T12:00")
    }

    /** Reading is not gated: an unconfirmed account can still see the feed. */
    @Test
    fun anUnconfirmedAccountCanStillRead() = withServer {
        val user = register("member@example.com")

        val response = client.get("/posts") {
            bearerAuth(user.tokens.accessToken)
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    /**
     * The link in the email is opened in a browser, so it has to land on a
     * page. It used to 404: the token was only spendable through a JSON call
     * the app makes, and nothing answered the address people were sent to.
     */
    @Test
    fun theLinkInTheEmailLandsOnAPage() = withServer { (mail) ->
        register("member@example.com")
        val token = mail.linkFor("member@example.com")!!

        val page = client.get("/verify?token=$token")

        assertEquals(HttpStatusCode.OK, page.status)
        assertTrue(page.bodyAsText().contains("Confirm my email"), "no way to confirm from the page")
    }

    /**
     * Looking at the page must not spend the token. Mail scanners follow links
     * before people do, and a token burned by Outlook is a person told their
     * link was already used.
     */
    @Test
    fun openingThePageDoesNotConfirmAnything() = withServer { (mail) ->
        val user = register("member@example.com")
        val token = mail.linkFor("member@example.com")!!

        client.get("/verify?token=$token")

        assertEquals(null, me(user).verifiedAt, "merely looking at the page confirmed the address")
    }

    @Test
    fun pressingTheButtonOnThePageConfirmsTheAddress() = withServer { (mail) ->
        val user = register("member@example.com")
        val token = mail.linkFor("member@example.com")!!

        val response = client.post("/verify") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("token=$token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(me(user).verifiedAt != null)
    }

    @Test
    fun aResetLinkLandsOnAFormThatSetsThePassword() = withServer { (mail) ->
        register("member@example.com")
        forgot("member@example.com")
        val token = mail.linkFor("member@example.com", index = 1)!!

        val page = client.get("/reset?token=$token")
        assertEquals(HttpStatusCode.OK, page.status)
        assertTrue(page.bodyAsText().contains("Set my password"))

        val submitted = client.post("/reset") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("token=$token&password=a-brand-new-password")
        }

        assertEquals(HttpStatusCode.OK, submitted.status)
        assertEquals(HttpStatusCode.OK, login("member@example.com", "a-brand-new-password").status)
    }

    /** Spent, expired and invented all read the same to whoever holds the link. */
    @Test
    fun aDeadLinkSaysTheSameThingWhateverKilledIt() = withServer { (mail) ->
        register("member@example.com")
        val token = mail.linkFor("member@example.com")!!
        client.post("/verify") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("token=$token")
        }

        val spent = client.post("/verify") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("token=$token")
        }
        val invented = client.post("/verify") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("token=never-issued")
        }

        assertEquals(spent.status, invented.status)
        assertEquals(spent.bodyAsText(), invented.bodyAsText(), "the pages differed by why the link was dead")
    }

    /**
     * A Russian speaker registers in a Russian app and the first thing the
     * server sends them is English. It arrives at the one moment they cannot
     * use the app yet, so it is also the moment it matters most.
     */
    @Test
    fun someoneWhoReadsRussianIsWrittenToInRussian() = withServer { (mail) ->
        register("member@example.com", language = "ru")

        val (subject, body) = mail.messageFor("member@example.com")!!
        assertTrue("Подтвердите" in subject, "the subject was not Russian: $subject")
        assertTrue("Здравствуйте" in body, "the body was not Russian: $body")
        assertFalse("Confirm this address" in body, "English survived into the Russian email")
    }

    @Test
    fun aRussianSpeakerGetsARussianPasswordReset() = withServer { (mail) ->
        register("member@example.com", language = "ru")
        forgot("member@example.com")

        val (subject, body) = mail.messageFor("member@example.com", index = 1)!!
        assertTrue("пароля" in subject, "the subject was not Russian: $subject")
        assertTrue("сбросить пароль" in body, "the body was not Russian: $body")
    }

    @Test
    fun someoneWhoReadsEnglishStillGetsEnglish() = withServer { (mail) ->
        register("member@example.com", language = "en")

        val (subject, _) = mail.messageFor("member@example.com")!!
        assertEquals("Confirm your email for ${AppInfo.NAME}", subject)
    }

    /**
     * An older client sends no language at all, and a language this app does
     * not speak is not a reason to send nothing. Both get English.
     */
    @Test
    fun anUnknownLanguageFallsBackToEnglish() = withServer { (mail) ->
        register("old@example.com")
        register("german@example.com", language = "de")

        assertEquals("Confirm your email for ${AppInfo.NAME}", mail.messageFor("old@example.com")!!.first)
        assertEquals("Confirm your email for ${AppInfo.NAME}", mail.messageFor("german@example.com")!!.first)
    }

    /** Whatever the language, the link still has to be in there and still work. */
    @Test
    fun theRussianEmailCarriesAWorkingLink() = withServer { (mail) ->
        val user = register("member@example.com", language = "ru")

        val link = mail.linkFor("member@example.com")
        assertTrue(link != null, "the Russian email had no link in it")
        assertEquals(HttpStatusCode.NoContent, verify(link!!))
        assertTrue(me(user).verifiedAt != null, "the link in the Russian email verified nothing")
    }

    /**
     * The Russian email leads somewhere, and where it leads was English.
     *
     * The page asks the browser rather than the account: the token could name
     * the person, but resolving it to read their language means touching a
     * single-use token on a GET, which is the one thing these pages exist not
     * to do.
     */
    @Test
    fun theVerificationPageAnswersInTheBrowsersLanguage() = withServer { (mail) ->
        register("member@example.com", language = "ru")
        val token = mail.linkFor("member@example.com")!!

        val page = client.get("/verify?token=$token") {
            header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8")
        }.bodyAsText()

        assertTrue("Подтвердить почту" in page, "the button was not in Russian")
        assertFalse("Confirm my email" in page, "English survived into the Russian page")
    }

    @Test
    fun theResetPageAnswersInTheBrowsersLanguage() = withServer { (mail) ->
        register("member@example.com")
        forgot("member@example.com")
        val token = mail.linkFor("member@example.com", index = 1)!!

        val page = client.get("/reset?token=$token") { header("Accept-Language", "ru") }.bodyAsText()

        assertTrue("Сохранить пароль" in page, "the button was not in Russian")
    }

    /** Pressing the button has to answer in the same language the form did. */
    @Test
    fun confirmingInRussianIsAnsweredInRussian() = withServer { (mail) ->
        register("member@example.com", language = "ru")
        val token = mail.linkFor("member@example.com")!!

        val page = client.post("/verify") {
            header("Accept-Language", "ru")
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("token=$token")
        }.bodyAsText()

        assertTrue("Адрес подтверждён" in page, "the confirmation was in English: $page")
    }

    /** A browser asking for neither, or for something else, still gets a page. */
    @Test
    fun aBrowserWithNoPreferenceGetsEnglish() = withServer { (mail) ->
        register("member@example.com", language = "ru")
        val token = mail.linkFor("member@example.com")!!

        val page = client.get("/verify?token=$token").bodyAsText()

        assertTrue("Confirm my email" in page)
    }

    /** The dead-link page must still say one thing, in either language. */
    @Test
    fun aDeadLinkInRussianAlsoSaysNothingAboutWhy() = withServer {
        val spent = client.post("/verify") {
            header("Accept-Language", "ru")
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("token=never-issued")
        }
        val invented = client.post("/verify") {
            header("Accept-Language", "ru")
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("token=also-never-issued")
        }

        assertTrue("больше не действует" in spent.bodyAsText(), "the dead-link page was not Russian")
        assertEquals(spent.bodyAsText(), invented.bodyAsText())
    }

    // — helpers —

    private suspend fun ApplicationTestBuilder.register(
        email: String,
        language: String? = null,
    ): AuthResponse {
        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    RegisterRequest(
                        "Member", "User", email, "password123",
                        languages = listOfNotNull(language),
                        defaultLanguage = language,
                    ),
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)
        return Json.decodeFromString(response.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.verify(token: String) = client.post("/auth/verify") {
        contentType(ContentType.Application.Json)
        setBody("""{"token":"$token"}""")
    }.status

    private suspend fun ApplicationTestBuilder.forgot(email: String) = client.post("/auth/password/forgot") {
        contentType(ContentType.Application.Json)
        setBody("""{"email":"$email"}""")
    }.status

    private suspend fun ApplicationTestBuilder.reset(token: String, password: String) =
        client.post("/auth/password/reset") {
            contentType(ContentType.Application.Json)
            setBody("""{"token":"$token","password":"$password"}""")
        }.status

    private suspend fun ApplicationTestBuilder.login(email: String, password: String) =
        client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"$password"}""")
        }

    /**
     * The stored account, read by signing in.
     *
     * The password is a parameter because half of these tests change it: an
     * earlier version assumed the original and silently returned the *stale*
     * object when the login failed, so a test asking "was this verified" was
     * answered by the copy from before it happened.
     */
    private suspend fun ApplicationTestBuilder.me(
        user: AuthResponse,
        password: String = "password123",
    ): com.example.poster.model.User {
        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"${user.user.email}","password":"$password"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status, "could not sign in to read the account back")
        return Json.decodeFromString<AuthResponse>(response.bodyAsText()).user
    }

    /**
     * Asking for the confirmation mail again straight away.
     *
     * The answer is 204 either way — whether it was sent, already confirmed or
     * asked for too soon is nothing to report back — so the only thing that
     * shows the throttle is there is the mail that did not go out.
     */
    @Test
    fun askingForTheVerificationMailAgainImmediatelySendsNothing() = withServer { (mail) ->
        val user = register("member@example.com")
        assertEquals(1, mail.countFor("member@example.com"), "registering did not send one")

        val again = client.post("/auth/verify/resend") { bearerAuth(user.tokens.accessToken) }

        assertEquals(HttpStatusCode.NoContent, again.status)
        assertEquals(1, mail.countFor("member@example.com"), "a second verification mail went out inside the window")
    }
}
