package com.example.poster

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import com.example.poster.auth.GoogleVerifier
import com.example.poster.model.AuthResponse
import com.example.poster.model.RegisterRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Deleting an account that has no password.
 *
 * `/delete-account` exists because Google Play requires deletion to be
 * reachable without the app. It asked for a password — and an account made
 * through a provider never has one a person could type, because
 * `signInWithProvider` stores a hash of a random UUID so the column is not
 * empty. Everybody who signed in with Google was told their details did not
 * match, which was true and useless, and the requirement was not actually met
 * for them.
 */
class DeleteAccountSocialTest {

    private val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val publicKey = keys.public as RSAPublicKey
    private val privateKey = keys.private as RSAPrivateKey
    private val otherKeys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    private val now: Instant = Instant.now()

    private fun token(
        email: String = "member@example.com",
        subject: String = "google-subject-1",
        audience: String = CLIENT_ID,
        emailVerified: Boolean = true,
        expiresAt: Instant = now.plusSeconds(600),
        signWith: RSAPrivateKey = privateKey,
    ): String = JWT.create()
        .withKeyId("key-1")
        .withAudience(audience)
        .withIssuer("https://accounts.google.com")
        .withSubject(subject)
        .withClaim("email", email)
        .withClaim("email_verified", emailVerified)
        .withExpiresAt(Date.from(expiresAt))
        .sign(Algorithm.RSA256(null, signWith))

    @Test
    fun aGoogleTokenDeletesTheAccountItBelongsTo() = withServer {
        val user = register("member@example.com")

        val response = deleteWith(credential = token())

        assertEquals(HttpStatusCode.OK, response.first, response.second)
        assertNull(
            accountsRepository().userById(user.user.guid),
            "the account is still there",
        )
    }

    /**
     * The tick is the "are you sure", and it guards the token as it guards the
     * password. A page that deletes on a button press because the credential
     * happened to be valid has skipped the only question it asks.
     */
    @Test
    fun anUntickedBoxDeletesNothing() = withServer {
        val user = register("member@example.com")

        val response = deleteWith(credential = token(), confirm = false)

        assertEquals(HttpStatusCode.BadRequest, response.first)
        assertTrue(
            accountsRepository().userById(user.user.guid) != null,
            "an unconfirmed request deleted the account anyway",
        )
    }

    /**
     * A token for somebody with no account here deletes nothing and says the
     * same thing a wrong password says.
     *
     * Anything else would make this page a way of asking whether an address has
     * an account — which is the same reason the password path has one message
     * for a wrong address and a wrong password alike.
     */
    @Test
    fun aTokenForAStrangerIsRefusedLikeAWrongPassword() = withServer {
        register("member@example.com")

        val stranger = deleteWith(credential = token(email = "nobody@example.com", subject = "other"))
        val wrongPassword = client.post("/delete-account") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("email=member@example.com&password=wrong&confirm=on")
        }

        assertEquals(HttpStatusCode.Unauthorized, stranger.first)
        assertEquals(HttpStatusCode.Unauthorized, wrongPassword.status)
        assertEquals(
            wrongPassword.bodyAsText(),
            stranger.second,
            "a stranger's token was distinguishable from a wrong password",
        )
    }

    /** Every rule the verifier enforces for signing in still holds here. */
    @Test
    fun aTokenThatFailsVerificationDeletesNothing() = withServer {
        val user = register("member@example.com")

        val forged = deleteWith(credential = token(signWith = otherKeys.private as RSAPrivateKey))
        val expired = deleteWith(credential = token(expiresAt = now.minusSeconds(60)))
        val unverified = deleteWith(credential = token(emailVerified = false))
        val someoneElsesApp = deleteWith(credential = token(audience = "another-app.apps.googleusercontent.com"))

        listOf(forged, expired, unverified, someoneElsesApp).forEach {
            assertEquals(HttpStatusCode.Unauthorized, it.first)
        }
        assertTrue(
            accountsRepository().userById(user.user.guid) != null,
            "a token that should not verify deleted the account",
        )
    }

    /** The button is drawn, and the note about resetting a password is there. */
    @Test
    fun thePageOffersTheWayInForAnAccountWithNoPassword() = withServer {
        val page = client.get("/delete-account").bodyAsText()

        assertTrue(page.contains("accounts.google.com/gsi/client"), "no Google button on the page")
        assertTrue(page.contains(CLIENT_ID), "the button was drawn without a client id")
        assertTrue(page.contains("Continue with Apple"), "no Apple button on the page")
        assertTrue(page.contains("Reset your password"), "no way out for somebody stuck")
    }

    /** Russian gets the same page. */
    @Test
    fun theRussianPageSaysTheSameThings() = withServer {
        val page = client.get("/delete-account") { header("Accept-Language", "ru") }.bodyAsText()

        assertTrue(page.contains("Google"), "no Google button on the Russian page")
        assertTrue(page.contains("Продолжить с Apple"), "the Apple button was left in English")
    }

    // — helpers —

    private fun accountsRepository() = com.example.poster.model.AccountLocalRepository()

    private suspend fun ApplicationTestBuilder.deleteWith(
        credential: String,
        confirm: Boolean = true,
    ): Pair<HttpStatusCode, String> {
        val body = buildString {
            append("credential=").append(credential)
            if (confirm) append("&confirm=on")
        }
        val response = client.post("/delete-account") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(body)
        }
        return response.status to response.bodyAsText()
    }

    private suspend fun ApplicationTestBuilder.register(email: String): AuthResponse {
        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(RegisterRequest("Some", "Body", email, "password123")))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        return Json.decodeFromString(response.bodyAsText())
    }

    private companion object {
        const val CLIENT_ID = "poster.apps.googleusercontent.com"
    }

    private fun withServer(block: suspend ApplicationTestBuilder.() -> Unit) {
        val databasePath = Files.createTempDirectory("poster-delete-social").resolve("test.db")
        val previousDatabase = System.getProperty("poster.database")
        val previousDevelopment = System.getProperty("io.ktor.development")
        val previousClientId = System.getProperty("poster.google.clientId")
        System.setProperty("poster.database", databasePath.toString())
        System.setProperty("io.ktor.development", "true")
        try {
            testApplication {
                application {
                    module(
                        verifiers = listOf(
                            GoogleVerifier(audience = CLIENT_ID, keyFor = { publicKey }),
                        ),
                        googleClientIdOverride = CLIENT_ID,
                    )
                }
                block()
            }
        } finally {
            if (previousDatabase == null) System.clearProperty("poster.database")
            else System.setProperty("poster.database", previousDatabase)
            if (previousDevelopment == null) System.clearProperty("io.ktor.development")
            else System.setProperty("io.ktor.development", previousDevelopment)
            if (previousClientId == null) System.clearProperty("poster.google.clientId")
            else System.setProperty("poster.google.clientId", previousClientId)
            databasePath.toFile().parentFile.deleteRecursively()
        }
    }
}
