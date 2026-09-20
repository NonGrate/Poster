package com.example.poster

import com.example.poster.config.Features
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The published child safety standards.
 *
 * Google Play requires the link to be live and reachable by anybody, anywhere.
 * "Anybody" is the part worth a test: a page behind a session satisfies nothing.
 */
class ChildSafetyPageTest {

    @Test
    fun anybodyCanReadItWithoutAnAccount() = withServer {
        val response = client.get("/child-safety")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("Child safety standards"))
    }

    /**
     * The reporting routes are the point of the page. Somebody who has found
     * something must not have to take our word for what happens next.
     */
    @Test
    fun itNamesTheHotlinesAnybodyCanReportTo() = withServer {
        val html = client.get("/child-safety").bodyAsText()

        assertTrue(html.contains("report.cybertip.org"), "no CyberTipline link")
        assertTrue(html.contains("inhope.org"), "no INHOPE link")
        assertTrue(html.contains("stopline.cz"), "no Czech hotline link")
    }

    @Test
    fun itPublishesAContactAddress() = withServer {
        val html = client.get("/child-safety").bodyAsText()

        assertTrue(html.contains("mailto:"), "no contact address to write to")
    }

    /**
     * The two structural facts are what make these standards worth more than
     * intent: no imagery can pass through, and there is no private channel.
     * If either ever stops being true, this page becomes a false statement —
     * so it is asserted here, where adding an upload would break the build.
     */
    @Test
    fun itStatesTheLimitsThatDoTheRealWork() = withServer {
        val html = client.get("/child-safety").bodyAsText()

        // The limit stated must be the one this build has.
        if (Features.IMAGES) {
            assertTrue(html.contains("one still image and nothing else"), "the one-image limit is missing")
        } else {
            assertTrue(html.contains("no way to attach or send an image"), "the no-imagery limit is missing")
        }
        assertTrue(html.contains("no private messaging"), "the no-messaging limit is missing")
        // The rest of the page moves with the flags too, so the claims follow them.
        if (Features.COMMENTS) {
            assertTrue(html.contains("Comments under a post are text only"), "the comment limit is missing")
        }
        if (Features.AUTHORS) {
            assertTrue(html.contains("shows the name its writer gave the account"), "the naming claim is missing")
        } else {
            assertTrue(html.contains("Nobody's name is shown beside a post"), "the page claims names it does not show")
        }
    }

    @Test
    fun itSaysWhoTheServiceIsNotFor() = withServer {
        assertTrue(client.get("/child-safety").bodyAsText().contains("under 13"))
    }

    /**
     * Honesty about what is not done. Claiming an age check the app does not
     * perform would be worse than claiming nothing.
     */
    @Test
    fun itDoesNotClaimAnAgeCheckThatDoesNotExist() = withServer {
        val html = client.get("/child-safety").bodyAsText()

        assertTrue(html.contains("do not currently verify age"), "the page implies a check we do not run")
        assertFalse(html.contains("verified age"), "the page claims verification")
    }

    @Test
    fun theRussianPageIsInRussian() = withServer {
        val html = client.get("/child-safety") {
            header(HttpHeaders.AcceptLanguage, "ru-RU,ru;q=0.9")
        }.bodyAsText()

        assertTrue(html.contains("Стандарты защиты детей"), "the Russian page came back in English")
        assertTrue(html.contains("report.cybertip.org"), "the Russian page dropped the hotlines")
    }

}
