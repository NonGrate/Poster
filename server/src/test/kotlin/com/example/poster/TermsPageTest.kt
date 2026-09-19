package com.example.poster

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The terms, and that the pages a listing points at can actually be found.
 */
class TermsPageTest {

    @Test
    fun anybodyCanReadThem() = withServer {
        val response = client.get("/terms")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("Terms of service"))
    }

    @Test
    fun theyAreAlsoInRussian() = withServer {
        val html = client.get("/terms") {
            header(HttpHeaders.AcceptLanguage, "ru-RU,ru;q=0.9")
        }.bodyAsText()

        assertTrue(html.contains("Условия использования"), "the Russian page came back in English")
    }

    /**
     * Terms that promise something the software does not do are worse than no
     * terms. These two are load-bearing claims and both are implemented:
     * deletion really removes the account, and a group outlives its owner.
     */
    @Test
    fun theyDescribeDeletionAsItActuallyBehaves() = withServer {
        val html = client.get("/terms").bodyAsText()

        assertTrue(html.contains("cannot be undone"), "deletion is not described as irreversible")
        assertTrue(
            html.contains("Groups you started stay"),
            "the terms do not mention that groups outlive their owner",
        )
    }

    @Test
    fun theyPointAtTheChildSafetyStandards() = withServer {
        assertTrue(client.get("/terms").bodyAsText().contains("child safety"))
    }

    /**
     * A page reachable only by typing its URL satisfies a form field and
     * nobody else. Google Play links these, and so should the front page.
     */
    @Test
    fun theFrontPageLinksToEveryPublishedPolicy() = withServer {
        val html = client.get("/").bodyAsText()

        assertTrue(html.contains("/privacy"), "no privacy link")
        assertTrue(html.contains("/terms"), "no terms link")
        assertTrue(html.contains("/child-safety"), "no child safety link")
    }

    @Test
    fun everyPolicyPageAnswersInBothLanguages() = withServer {
        for (path in listOf("/privacy", "/terms", "/child-safety")) {
            assertEquals(HttpStatusCode.OK, client.get(path).status, "$path is not reachable")
            assertEquals(
                HttpStatusCode.OK,
                client.get(path) { header(HttpHeaders.AcceptLanguage, "ru") }.status,
                "$path is not reachable in Russian",
            )
        }
    }

    /**
     * Google will not let an OAuth consent screen name a domain until somebody
     * has proved they own it. Unset, no tag: an empty verification meta tag
     * reads as a failed attempt rather than a deliberate absence.
     */
    @Test
    fun theVerificationTagIsAbsentUntilAskedFor() = withServer {
        val html = client.get("/").bodyAsText()

        assertFalse(
            html.contains("google-site-verification"),
            "a verification tag appeared with no token configured",
        )
    }

    /**
     * Every page, not just the front one. Search Console verifies whichever
     * URL it was given, and the consent screen names three of ours.
     */
    @Test
    fun noPageCarriesAVerificationTagUntilAskedFor() = withServer {
        for (path in listOf("/", "/privacy", "/terms", "/child-safety")) {
            assertFalse(
                client.get(path).bodyAsText().contains("google-site-verification"),
                "$path emitted a verification tag with no token configured",
            )
        }
    }

    private fun withServer(block: suspend ApplicationTestBuilder.() -> Unit) {
        val databasePath = Files.createTempDirectory("poster-terms").resolve("test.db")
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
