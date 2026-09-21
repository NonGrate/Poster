package com.example.poster

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The files that let a link in an email open the app instead of a browser.
 *
 * Getting one wrong is silent: Android and iOS simply carry on opening the
 * browser, and nothing anywhere says why.
 */
class WellKnownRoutesTest {

    @Test
    fun androidGetsTheFingerprintsItWasGiven() = testApplication {
        application { routing { wellKnownRoutes(androidFingerprints = listOf("AA:BB", "CC:DD")) } }

        val response = client.get("/.well-known/assetlinks.json")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"AA:BB\"") && body.contains("\"CC:DD\""), body)
        assertTrue(body.contains("delegate_permission/common.handle_all_urls"))
        // The applicationId, which has no underscore. This asserted the
        // underscored spelling and so held the bug in place: an
        // assetlinks.json naming a package that does not exist fails App Links
        // verification silently, and the only symptom is that Android never
        // offers to open the app.
        assertTrue(body.contains("\"com.example.poster\""), body)
        assertFalse(
            Regex("\"package_name\":\\s*\"[^\"]*_").containsMatchIn(body),
            "an underscore in package_name means the Kotlin package leaked in instead of the applicationId",
        )
    }

    /**
     * Absent, not empty. A file saying "no app owns these links" is a thing
     * Android caches; a 404 is a question it asks again once there is an
     * answer.
     */
    @Test
    fun withNoFingerprintTheFileIsAbsentRatherThanEmpty() = testApplication {
        application { routing { wellKnownRoutes(androidFingerprints = emptyList()) } }

        assertEquals(HttpStatusCode.NotFound, client.get("/.well-known/assetlinks.json").status)
    }

    @Test
    fun appleGetsTheTeamAndBundleTogether() = testApplication {
        application {
            routing {
                wellKnownRoutes(
                    androidFingerprints = emptyList(),
                    appleTeamId = "ABCDE12345",
                    appleBundleId = "com.example.poster",
                )
            }
        }

        val response = client.get("/.well-known/apple-app-site-association")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(
            response.bodyAsText().contains("ABCDE12345.com.example.poster"),
            "the app id has to be team and bundle joined, or iOS ignores the file",
        )
    }

    @Test
    fun withNoTeamThereIsNoAppleFile() = testApplication {
        application { routing { wellKnownRoutes(androidFingerprints = emptyList(), appleTeamId = null) } }

        assertEquals(HttpStatusCode.NotFound, client.get("/.well-known/apple-app-site-association").status)
    }

    /** The paths the emails actually send people to. */
    @Test
    fun bothFilesCoverTheLinksThatAreSent() = testApplication {
        application {
            routing { wellKnownRoutes(androidFingerprints = listOf("AA:BB"), appleTeamId = "TEAM") }
        }

        val apple = client.get("/.well-known/apple-app-site-association").bodyAsText()
        listOf("/verify", "/reset", "/join").forEach {
            assertTrue(apple.contains(it), "iOS would not open $it")
        }
    }
}
