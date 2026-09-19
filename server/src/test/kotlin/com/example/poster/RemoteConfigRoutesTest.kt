package com.example.poster

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import com.example.poster.model.RemoteConfig
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What the app is allowed to do today.
 *
 * Payments are off until somebody is legally able to receive the money, and the
 * switch lives here rather than in a build so the day it changes is a restart
 * rather than a release, a review and a wait.
 */
class RemoteConfigRoutesTest {

    /**
     * The default, and the one that matters.
     *
     * Every way of not knowing — no variable set, a value that is not "true", a
     * server too old to answer — has to read as payments being off. Offering to
     * take money we may not be allowed to take is the failure worth avoiding;
     * a tip jar that is briefly unavailable is not.
     */
    @Test
    fun paymentsAreOffUnlessSwitchedOn() = withServer {
        val response = client.get("/config")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            RemoteConfig(paymentsEnabled = false),
            Json.decodeFromString<RemoteConfig>(response.bodyAsText()),
        )
    }

    /** Asked before there is a session, so it cannot need one. */
    @Test
    fun theConfigIsReadableWithoutSigningIn() = withServer {
        assertEquals(HttpStatusCode.OK, client.get("/config").status)
    }

    /**
     * A tap on a tier while payments are off is recorded and accepted.
     *
     * Signed in or not: somebody looking at the paywall before they have an
     * account is exactly the person worth hearing from, and refusing them would
     * mean the only evidence about the tiers came from people already
     * committed.
     */
    @Test
    fun interestIsAcceptedFromAnybody() = withServer {
        val response = client.post("/support/interest") {
            contentType(ContentType.Application.Json)
            setBody("""{"tier":"coffee"}""")
        }

        assertEquals(HttpStatusCode.Accepted, response.status)
    }

    /** A body with no tier is still accepted: there is nothing to fail on. */
    @Test
    fun aTaplessBodyIsNotAnError() = withServer {
        val response = client.post("/support/interest") {
            contentType(ContentType.Application.Json)
            setBody("""{}""")
        }

        assertEquals(HttpStatusCode.Accepted, response.status)
    }

    private fun withServer(block: suspend ApplicationTestBuilder.() -> Unit) {
        val databasePath = Files.createTempDirectory("poster-config").resolve("test.db")
        val previousDatabase = System.getProperty("poster.database")
        val previousDevelopment = System.getProperty("io.ktor.development")
        System.setProperty("poster.database", databasePath.toString())
        System.setProperty("io.ktor.development", "true")
        try {
            testApplication {
                application { module() }
                block()
            }
        } finally {
            if (previousDatabase == null) System.clearProperty("poster.database")
            else System.setProperty("poster.database", previousDatabase)
            if (previousDevelopment == null) System.clearProperty("io.ktor.development")
            else System.setProperty("io.ktor.development", previousDevelopment)
            databasePath.toFile().parentFile.deleteRecursively()
        }
    }
}
