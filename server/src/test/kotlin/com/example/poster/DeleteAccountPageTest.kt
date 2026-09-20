package com.example.poster

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import com.example.poster.model.RegisterRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Deleting an account from the web.
 *
 * Google Play requires the way out to be reachable without installing the app,
 * which means a page anybody can open and nothing behind a session.
 */
class DeleteAccountPageTest {

    @Test
    fun thePageIsReachableWithoutSigningIn() = withServer {
        val response = client.get("/delete-account")

        assertEquals(HttpStatusCode.OK, response.status)
        val html = response.bodyAsText()
        assertTrue(html.contains("name=\"email\""), "no email field")
        assertTrue(html.contains("name=\"password\""), "no password field")
        assertTrue(html.contains("cannot be undone"), "the page does not say it is irreversible")
    }

    @Test
    fun theRightDetailsDeleteTheAccount() = withServer {
        register("goodbye@example.com")

        val response = submit("goodbye@example.com", "password123", confirmed = true)

        assertEquals(HttpStatusCode.OK, response.first)
        assertTrue(response.second.contains("deleted"), "no confirmation shown")
        assertEquals(
            HttpStatusCode.Unauthorized,
            login("goodbye@example.com", "password123"),
            "the account still signs in",
        )
    }

    /** The password is the authorisation. Without it this page is a delete button on the internet. */
    @Test
    fun aWrongPasswordDeletesNothing() = withServer {
        register("safe@example.com")

        val response = submit("safe@example.com", "not-the-password", confirmed = true)

        assertEquals(HttpStatusCode.Unauthorized, response.first)
        assertEquals(HttpStatusCode.OK, login("safe@example.com", "password123"), "the account was deleted")
    }

    /**
     * The same answer for an address that is not here as for a wrong password.
     * Anything else makes this page a way of asking whether somebody has an
     * account — on an app about what people like.
     */
    @Test
    fun anUnknownAddressLooksExactlyLikeAWrongPassword() = withServer {
        register("real@example.com")

        val wrongPassword = submit("real@example.com", "nope", confirmed = true)
        val noSuchAccount = submit("ghost@example.com", "nope", confirmed = true)

        assertEquals(wrongPassword.first, noSuchAccount.first)
        assertEquals(wrongPassword.second, noSuchAccount.second)
    }

    /** The tick is the "are you sure", and it is checked before any credential is tried. */
    @Test
    fun withoutTheConfirmationNothingIsDeleted() = withServer {
        register("careful@example.com")

        val response = submit("careful@example.com", "password123", confirmed = false)

        assertEquals(HttpStatusCode.BadRequest, response.first)
        assertTrue(response.second.contains("Tick the box"), "the page does not say what is missing")
        assertEquals(HttpStatusCode.OK, login("careful@example.com", "password123"))
    }

    @Test
    fun theRussianPageIsInRussian() = withServer {
        val html = client.get("/delete-account") {
            header(HttpHeaders.AcceptLanguage, "ru-RU,ru;q=0.9")
        }.bodyAsText()

        assertTrue(html.contains("Удаление аккаунта"), "the Russian page came back in English")
    }

    /**
     * The page checks a password, so it can be used to guess one. Five tries
     * for an address, then it stops.
     */
    @Test
    fun guessingIsStoppedAfterEnoughFailures() = withServer {
        register("target@example.com")

        repeat(5) { submit("target@example.com", "guess$it", confirmed = true) }
        val stopped = submit("target@example.com", "guess-again", confirmed = true)

        assertEquals(HttpStatusCode.TooManyRequests, stopped.first)
        assertTrue(stopped.second.contains("Too many attempts"), "no explanation shown")
    }

    /**
     * The throttle is shared with /auth/login, because both check the same
     * password. A separate allowance here would just be the way around that one.
     */
    @Test
    fun failuresHereCountAgainstSigningInToo() = withServer {
        register("shared@example.com")

        repeat(5) { submit("shared@example.com", "guess$it", confirmed = true) }

        assertEquals(
            HttpStatusCode.TooManyRequests,
            login("shared@example.com", "password123"),
            "the API let guessing continue where the page had stopped it",
        )
    }

    /** Being throttled must not delete anything either. */
    @Test
    fun aThrottledRequestDeletesNothing() = withServer {
        register("survivor@example.com")
        repeat(5) { submit("survivor@example.com", "guess$it", confirmed = true) }

        submit("survivor@example.com", "password123", confirmed = true)

        // Still throttled, so the correct password does not get through either
        // — which is the point, and it means the account is still there.
        assertEquals(
            HttpStatusCode.TooManyRequests,
            login("survivor@example.com", "password123"),
        )
    }

    // — helpers —

    private suspend fun ApplicationTestBuilder.submit(
        email: String,
        password: String,
        confirmed: Boolean,
    ): Pair<HttpStatusCode, String> {
        val body = buildString {
            append("email=").append(email.replace("@", "%40"))
            append("&password=").append(password)
            if (confirmed) append("&confirm=on")
        }
        val response = client.post("/delete-account") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(body)
        }
        return response.status to response.bodyAsText()
    }

    private suspend fun ApplicationTestBuilder.register(email: String) {
        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(RegisterRequest("Some", "Body", email, "password123")))
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    private suspend fun ApplicationTestBuilder.login(email: String, password: String): HttpStatusCode =
        client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"$password"}""")
        }.status

}
