package com.example.poster

import com.example.poster.config.AppInfo
import io.ktor.server.html.respondHtml
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.head
import kotlinx.html.li
import kotlinx.html.meta
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.style
import kotlinx.html.title
import kotlinx.html.ul
import kotlinx.html.unsafe

/**
 * Where the store links point, or nothing yet.
 *
 * Read from the environment rather than written here, so the day the listing
 * goes live is a change to one variable and a restart — not a code change, a
 * review and a deploy for a URL somebody already has.
 */
data class StoreLinks(
    val play: String? = System.getenv("POSTER_PLAY_URL")?.takeIf { it.isNotBlank() },
    val appStore: String? = System.getenv("POSTER_APPSTORE_URL")?.takeIf { it.isNotBlank() },
)

/**
 * The page somebody lands on when they type poster.example.com.
 *
 * Almost everyone arriving here was sent by a person, not a search: a link in
 * a message, from somebody who has already explained what it is. So the page's
 * job is to confirm they are in the right place and get them to the app —
 * which is why the download is near the top and the explanation is short.
 *
 * Both languages the app speaks, chosen by the browser's own preference. The
 * people this is for are as likely to read Russian as English, and sending
 * them to an English page from a Russian app would be an odd first impression.
 */
fun Route.landingPage(links: StoreLinks = StoreLinks()) {
    get("/") {
        val russian = call.prefersRussian()
        call.respondHtml { landing(russian, links) }
    }
}

private fun HTML.landing(russian: Boolean, links: StoreLinks) {
    val copy = if (russian) Russian else English
    head {
        title { +copy.title }
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        meta(name = "description", content = copy.tagline)
        siteVerification()
        style { unsafe { +SITE_STYLE } }
        // Before the first paint, so a dark page does not flash white first.
        script { unsafe { +THEME_SCRIPT } }
    }
    body {
        siteChrome(russian, "/")
        div("art-wrap") { unsafe { +ART } }
        h1 { +copy.name }
        p("tagline") { +copy.tagline }

        div("downloads") {
            when (val play = links.play) {
                null -> p("soon") { +copy.playSoon }
                else -> a(href = play, classes = "button") { +copy.play }
            }
            links.appStore?.let { a(href = it, classes = "button") { +copy.appStore } }
        }

        h2 { +copy.howHeading }
        ul {
            copy.how.forEach { li { +it } }
        }

        p("quiet") { +copy.privacy }

        div("footer") {
            a(href = if (russian) "/privacy?lang=ru" else "/privacy?lang=en") { +copy.privacyLink }
            a(href = if (russian) "/terms?lang=ru" else "/terms?lang=en") { +copy.termsLink }
            // Google Play requires these standards to be published and
            // reachable; a link nobody can find from the front page satisfies
            // the letter of that and not the point of it.
            a(href = if (russian) "/child-safety?lang=ru" else "/child-safety?lang=en") {
                +copy.childSafetyLink
            }
            a(href = "mailto:$CONTACT_EMAIL") { +copy.contact }
            p { +copy.copyright }
            p { +copy.footer }
        }
    }
}

/**
 * The mark from the app's own login screen, read from assets/poster-mark.svg.
 *
 * One file rather than a copy here: it is wanted for the store listing and the
 * app besides this page, and a drawing kept in two places is a drawing that
 * stops matching itself. Read once at startup and written straight into the
 * page, so there is still nothing for a browser to fetch.
 */
private val ART: String by lazy {
    LandingMarker::class.java.getResource("/poster-mark.svg")
        ?.readText()
        // Failing loudly beats a page with a hole in it: the file is packaged
        // with the server, so missing means the build is wrong.
        ?: error("poster-mark.svg is missing from the server resources")
}

/** Only here to hand the classloader something to resolve the resource against. */
private class LandingMarker


private class Copy(
    val name: String,
    val title: String,
    val tagline: String,
    val play: String,
    val playSoon: String,
    val appStore: String,
    val howHeading: String,
    val how: List<String>,
    val privacy: String,
    val privacyLink: String,
    val termsLink: String,
    val childSafetyLink: String,
    val contact: String,
    val copyright: String,
    val footer: String,
)

/**
 * The year in the copyright line.
 *
 * Read from the clock rather than typed, because a page that says 2026 for
 * years is a page nobody is looking after — and this one is meant to look
 * looked after.
 */
private val YEAR: Int get() = java.time.Year.now().value

/**
 * Whose name goes after the ©. From the environment, because a template cannot
 * know who deployed it; unset, it says the app's name.
 */
private val OWNER: String
    get() = System.getenv("POSTER_OWNER_NAME")?.takeIf { it.isNotBlank() } ?: AppInfo.NAME

private val English = Copy(
    name = AppInfo.NAME,
    title = "${AppInfo.NAME} — share what matters with the people who matter",
    tagline = "A small, quiet place to post what is on your mind — to everyone, or only to the people you choose.",
    play = "Get it on Google Play",
    playSoon = "Coming to Google Play.",
    appStore = "Download on the App Store",
    howHeading = "How it works",
    how = listOf(
        "Write a post. Keep it to a group you belong to, or share it with everyone.",
        "See what the people around you are posting, and let them know you liked it.",
        "Mark a post resolved when it is, so the people following it hear how it went.",
    ),
    privacy = "Your posts are yours. Private ones stay private, and nothing is shown to anybody you have not shared it with.",
    privacyLink = "Privacy",
    termsLink = "Terms",
    childSafetyLink = "Child safety",
    contact = "Contact",
    copyright = "© $YEAR $OWNER",
    footer = "Made for small groups of people who know each other.",
)

private val Russian = Copy(
    name = AppInfo.NAME,
    title = "${AppInfo.NAME} — делитесь важным с теми, кто важен",
    tagline = "Небольшое спокойное место, где можно написать о том, что на уме — для всех или только для тех, кого вы выберете.",
    play = "Загрузить в Google Play",
    playSoon = "Скоро в Google Play.",
    appStore = "Загрузить в App Store",
    howHeading = "Как это работает",
    how = listOf(
        "Напишите пост. Только для своей группы или для всех.",
        "Смотрите, что пишут люди рядом, и отмечайте, что вам понравилось.",
        "Отметьте пост решённым, когда всё сложилось, чтобы те, кто следил, узнали, как было дело.",
    ),
    privacy = "Ваши посты — ваши. Личные остаются личными, и ничего не видно тем, с кем вы не поделились.",
    privacyLink = "Конфиденциальность",
    termsLink = "Условия",
    childSafetyLink = "Защита детей",
    contact = "Связаться",
    copyright = "© $YEAR $OWNER",
    footer = "Сделано для небольших групп людей, которые знают друг друга.",
)

/**
 * The same warm palette the account pages use, and no stylesheet to fetch —
 * this page is opened once, often on a phone, often on somebody else's data.
 */
internal val SITE_STYLE = """
    /*
       Colours are variables so the toggle in the corner can override what the
       browser asked for. Three states, not two: light, dark, and "whatever the
       system says" — which is the default and the only one that needs no
       attribute on the root.
    */
    :root {
      --bg: #fdf7f4; --fg: #2b211d; --quiet: #6d5a51;
      --edge: #c3ab9f; --field: #ffffff;
      --sp-outline: #a26a52; --sp-dot: #7d3f4c; --wash: 1;
    }
    @media (prefers-color-scheme: dark) {
      :root:not([data-theme="light"]) {
        --bg: #3a2e28; --fg: #f4ebe5; --quiet: #cbbcb2;
        --edge: #6f5c52; --field: #48392f;
        /*
           The drawing is ink on paper: a mid-brown outline and a dark red dot,
           both of which sink into a dark ground. Lifted here, and only the
           washes are dimmed — dimming the whole drawing took the ink with it.
        */
        --sp-outline: #eec3a6; --sp-dot: #f0b8c4; --wash: .72;
      }
    }
    :root[data-theme="dark"] {
      --bg: #3a2e28; --fg: #f4ebe5; --quiet: #cbbcb2;
      --edge: #6f5c52; --field: #48392f;
      --sp-outline: #eec3a6; --sp-dot: #f0b8c4; --wash: .72;
    }

    body { font: 17px -apple-system, system-ui, sans-serif; margin: 0 auto;
           padding: 56px 24px 72px; max-width: 34rem; color: var(--fg);
           background: var(--bg); line-height: 1.5; }
    a { color: inherit; }
    .art-wrap { margin: -24px 0 8px; }
    .art { display: block; width: 100%; height: auto; max-width: 27rem; margin: 0 auto; }
    .art .wash { opacity: var(--wash); }
    h1 { font-size: 30px; margin: 0 0 8px; }
    h2 { font-size: 19px; margin: 40px 0 8px; }
    .tagline { font-size: 19px; margin: 0 0 32px; }
    .downloads { margin: 0 0 8px; }
    .button { display: inline-block; margin: 0 8px 8px 0; padding: 14px 22px;
              border-radius: 12px; background: #8c4a32; color: #fff;
              text-decoration: none; font-size: 17px; }
    .soon { display: inline-block; padding: 14px 22px; border-radius: 12px;
            border: 1px dashed var(--edge); color: var(--quiet); margin: 0; }
    ul { padding-left: 20px; }
    li { margin-bottom: 10px; }
    .quiet { font-size: 15px; color: var(--quiet); margin-top: 28px; }
    .footer { font-size: 14px; color: var(--quiet); margin-top: 40px;
              border-top: 1px solid var(--edge); padding-top: 16px; }
    .footer a { margin-right: 14px; }
    input[type=password] { background: var(--field); color: var(--fg);
                           border: 1px solid var(--edge); }

    /* The control in the corner. Small, quiet, and out of the way of the page. */
    .chrome { position: absolute; top: 16px; right: 16px; font-size: 14px; }
    .chrome details { position: relative; }
    .chrome summary { cursor: pointer; list-style: none; color: var(--quiet);
                      padding: 6px 10px; border: 1px solid var(--edge);
                      border-radius: 999px; }
    .chrome summary::-webkit-details-marker { display: none; }
    .chrome-menu { position: absolute; right: 0; top: calc(100% + 6px);
                   background: var(--bg); border: 1px solid var(--edge);
                   border-radius: 12px; padding: 10px 12px; min-width: 11rem;
                   display: grid; gap: 6px; z-index: 2; }
    .chrome-label { color: var(--quiet); font-size: 12px; text-transform: uppercase;
                    letter-spacing: .04em; }
    .chrome-row { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 4px; }
    .chrome-choice { font: inherit; font-size: 14px; cursor: pointer;
                     background: none; color: var(--fg); text-decoration: none;
                     border: 1px solid var(--edge); border-radius: 999px;
                     padding: 4px 10px; }
    .chrome-choice.on { border-color: #8c4a32; color: #8c4a32; }
    /*
       The brown that marks the current choice is a paper colour and vanishes
       on a dark ground, so it follows the ink. Guarded the same way the
       palette is: bare :not([data-theme="light"]) matches a light page too.
    */
    @media (prefers-color-scheme: dark) {
      :root:not([data-theme="light"]) .chrome-choice.on {
        border-color: var(--sp-outline); color: var(--sp-outline);
      }
    }
    :root[data-theme="dark"] .chrome-choice.on {
      border-color: var(--sp-outline); color: var(--sp-outline);
    }
""".trimIndent()
