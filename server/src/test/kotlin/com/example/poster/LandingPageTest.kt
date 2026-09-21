package com.example.poster

import com.example.poster.config.AppInfo
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The page somebody lands on when a friend sends them the address.
 *
 * Mostly about the two things that can be wrong without anybody noticing: the
 * language it answers in, and whether it promises a download that does not
 * exist yet.
 */
class LandingPageTest {

    @Test
    fun thereIsSomethingAtTheRoot() = withPage {
        val response = client.get("/")

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), AppInfo.NAME)
    }

    @Test
    fun aRussianBrowserGetsRussian() = withPage {
        val page = client.get("/") { header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8") }.bodyAsText()

        assertContains(page, "Как это работает")
        assertFalse("How it works" in page, "an English heading survived into the Russian page")
    }

    @Test
    fun anEnglishBrowserGetsEnglish() = withPage {
        val page = client.get("/") { header("Accept-Language", "en-GB,en;q=0.9") }.bodyAsText()

        assertContains(page, "How it works")
    }

    /** Whichever the browser puts first wins, not whichever is mentioned. */
    @Test
    fun theBrowsersFirstChoiceWins() = withPage {
        val ruFirst = client.get("/") { header("Accept-Language", "ru;q=0.9,en;q=0.8") }.bodyAsText()
        val enFirst = client.get("/") { header("Accept-Language", "en;q=0.9,ru;q=0.8") }.bodyAsText()

        assertContains(ruFirst, "Как это работает")
        assertContains(enFirst, "How it works")
    }

    /** A language this app does not speak gets the app's own default. */
    @Test
    fun anotherLanguageGetsEnglish() = withPage {
        val page = client.get("/") { header("Accept-Language", "de-DE,de;q=0.9") }.bodyAsText()

        assertContains(page, "How it works")
    }

    @Test
    fun noHeaderAtAllIsStillAPage() = withPage {
        val response = client.get("/")

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "How it works")
    }

    /**
     * Until the listing exists, the page says "coming" rather than offering a
     * link to nowhere — which is the state it will be in for weeks.
     */
    @Test
    fun withoutAStoreLinkNothingIsPromised() = withPage(StoreLinks(play = null, appStore = null)) {
        val page = client.get("/").bodyAsText()

        assertContains(page, "Coming to Google Play")
        assertFalse("href=\"https://play.google.com" in page, "linked to a listing that does not exist")
    }

    @Test
    fun withAStoreLinkThereIsAButton() = withPage(
        StoreLinks(play = "https://play.google.com/store/apps/details?id=com.example.poster"),
    ) {
        val page = client.get("/").bodyAsText()

        assertContains(page, "play.google.com/store/apps/details?id=com.example.poster")
        assertFalse("Coming to Google Play" in page, "still said it was coming while linking to it")
    }

    /**
     * Opened once, often on somebody else's data: nothing else to fetch.
     *
     * Not "no script" — the theme toggle needs a dozen lines to remember a
     * choice, and inline script costs no request. What must never appear is
     * anything the browser has to go and get: a src, a stylesheet, an image.
     * The illustration is drawn into the page for the same reason.
     */
    @Test
    fun thePageFetchesNothingElse() = withPage {
        val page = client.get("/").bodyAsText()

        assertFalse("src=" in page, "the landing page pulled in something external")
        assertFalse("<link" in page, "the landing page pulled in a stylesheet")
        assertFalse("<img" in page, "the landing page pulled in an image")
        assertTrue("<style" in page, "expected the styles to be inline")
        assertTrue("<svg" in page, "expected the illustration to be drawn inline")
    }

    /** The corner control, in whichever language the page is answering in. */
    @Test
    fun thereIsAWayToSwitchLanguageAndTheme() = withPage {
        val page = client.get("/").bodyAsText()

        assertContains(page, "?lang=ru")
        assertContains(page, "?lang=en")
        assertContains(page, "data-theme-choice=\"dark\"")
        assertContains(page, "data-theme-choice=\"light\"")
        assertContains(page, "data-theme-choice=\"system\"", )
    }

    /**
     * A chosen language beats the browser's guess.
     *
     * The people this is for read both, often on a device set to the other
     * one — a Russian speaker on an English work laptop is the ordinary case,
     * not the exception.
     */
    @Test
    fun anExplicitChoiceBeatsTheBrowser() = withPage {
        val russianOnAnEnglishBrowser = client.get("/?lang=ru") {
            header("Accept-Language", "en-GB,en;q=0.9")
        }.bodyAsText()
        val englishOnARussianBrowser = client.get("/?lang=en") {
            header("Accept-Language", "ru-RU,ru;q=0.9")
        }.bodyAsText()

        assertContains(russianOnAnEnglishBrowser, "Как это работает")
        assertContains(englishOnARussianBrowser, "How it works")
    }

    /** The switch carries the page you are on, rather than dropping you at the top. */
    @Test
    fun switchingLanguageOnThePrivacyPageStaysOnIt() = withPage {
        val page = client.get("/privacy").bodyAsText()

        assertContains(page, "/privacy?lang=ru")
    }

    /** Required by the stores, and the first thing a careful person looks for. */
    @Test
    fun thereIsAWayToReachThePolicyAndAHuman() = withPage {
        val page = client.get("/").bodyAsText()

        assertContains(page, "/privacy")
        assertContains(page, "mailto:")
    }

    @Test
    fun theYearInTheCopyrightIsThisYear() = withPage {
        val page = client.get("/").bodyAsText()

        assertContains(page, "© ${java.time.Year.now().value}")
    }

    /** Decorative, so it must not be announced to somebody using a screen reader. */
    @Test
    fun theIllustrationIsHiddenFromScreenReaders() = withPage {
        val page = client.get("/").bodyAsText()

        assertContains(page, "aria-hidden=\"true\"")
    }

    /** Small enough that the illustration cannot have been smuggled in as data. */
    @Test
    fun thePageStaysSmall() = withPage {
        val bytes = client.get("/").bodyAsText().toByteArray().size

        assertTrue(bytes < 12_000, "the landing page grew to ${'$'}bytes bytes")
    }

    private fun withPage(
        links: StoreLinks = StoreLinks(play = null, appStore = null),
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        application { routing { landingPage(links); privacyPage() } }
        block()
    }
}
