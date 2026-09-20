package com.example.poster.admin

import com.example.poster.config.AppInfo
import com.example.poster.config.BrandPalette
import com.example.poster.config.Features
import com.example.poster.css
import com.example.poster.model.User
import kotlinx.html.*

/**
 * The frame every admin page is drawn in: the sign-in page, the layout with
 * its nav and heading, the stat card, the one stylesheet, and the two row
 * widgets the tables share.
 *
 * Here rather than beside any one table because all of them use it — a second
 * copy of the style block is two panels that stop looking like one product.
 */
internal fun HTML.loginPage(error: String?) {
    head {
        title { +"${AppInfo.NAME} — admin" }
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        styleBlock()
    }
    body {
        h1 { +"${AppInfo.NAME} admin" }
        if (error != null) p(classes = "error") { +error }
        form(action = "/admin/login", method = FormMethod.post) {
            p { emailInput(name = "email") { placeholder = "email"; required = true } }
            p { passwordInput(name = "password") { placeholder = "password"; required = true } }
            p { submitInput { value = "Sign in" } }
        }
    }
}

internal fun HTML.page(heading: String, admin: User, waiting: Int = 0, content: BODY.() -> Unit) {
    head {
        title { +"${AppInfo.NAME} — $heading" }
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        styleBlock()
    }
    body {
        nav {
            a(href = "/admin") { +"Overview" }
            a(href = "/admin/users") { +"Users" }
            a(href = "/admin/posts") { +"Posts" }
            if (Features.COMMENTS) a(href = "/admin/comments") { +"Comments" }
            if (Features.GROUPS) a(href = "/admin/groups") { +"Groups" }
            a(href = "/admin/audit") { +"Audit" }
            if (Features.CRASH_REPORTS) a(href = "/admin/crashes") { +"Crashes" }
            if (Features.REPORTS) a(href = "/admin/reports") {
                +"Reports"
                if (waiting > 0) span("badge count") { +waiting.toString() }
            }
            if (Features.FEEDBACK) a(href = "/admin/feedback") { +"Feedback" }
            if (Features.TELEMETRY) a(href = "/admin/events") { +"Events" }
            div("spacer") {}
            span("who") { +admin.email }
            form(action = "/admin/logout", method = FormMethod.post) { submitInput { value = "Sign out" } }
        }
        h1 { +heading }
        content()
    }
}

/**
 * One number, what it counts, and where to go about it.
 *
 * A link rather than a tile with a link in it: the whole card is the target,
 * because a number nobody can click is a number somebody has to go and find.
 */
internal fun FlowContent.statCard(label: String, value: Int, href: String, urgent: Boolean = false) {
    a(href = href, classes = if (urgent) "card urgent" else "card") {
        span("value") { +value.toString() }
        span("label") { +label }
    }
}

private fun HEAD.styleBlock() {
    style {
        unsafe {
            +"""
            /*
               Same warm palette as the app and the site, and the same three
               theme states: light, dark, and whatever the machine says. This is
               read at odd hours on whatever is to hand.
            */
            :root {
              --bg: ${css(BrandPalette.Light.surface)}; --panel: ${css(BrandPalette.Light.surfaceContainerLowest)}; --fg: ${css(BrandPalette.Light.onSurface)}; --quiet: ${css(BrandPalette.Light.onSurfaceVariant)};
              --line: ${css(BrandPalette.Light.outlineVariant)}; --accent: ${css(BrandPalette.Light.primary)}; --danger: ${css(BrandPalette.Light.error)};
              --live: ${css(BrandPalette.Light.tertiary)}; --badge: ${css(BrandPalette.Light.surfaceContainerHigh)};
            }
            @media (prefers-color-scheme: dark) {
              :root {
                --bg: ${css(BrandPalette.Dark.surface)}; --panel: ${css(BrandPalette.Dark.surfaceContainer)}; --fg: ${css(BrandPalette.Dark.onSurface)}; --quiet: ${css(BrandPalette.Dark.onSurfaceVariant)};
                --line: ${css(BrandPalette.Dark.outlineVariant)}; --accent: ${css(BrandPalette.Dark.primary)}; --danger: ${css(BrandPalette.Dark.error)};
                --live: ${css(BrandPalette.Dark.tertiary)}; --badge: ${css(BrandPalette.Dark.surfaceContainerHigh)};
              }
            }
            * { box-sizing: border-box; }
            body { font: 15px/1.5 system-ui, -apple-system, sans-serif; margin: 0;
                   padding: 0 0 4rem; color: var(--fg); background: var(--bg); }
            h1 { font-size: 1.5rem; margin: 1.5rem 2rem .25rem; }
            h2 { font-size: .8rem; text-transform: uppercase; letter-spacing: .06em;
                 color: var(--quiet); margin: 2rem 2rem .5rem; font-weight: 600; }
            /*
               Everything in the body sits on the same left edge. Listing the
               selectors that should be indented meant every element nobody
               thought of — a heading, a form — sat flush against the window
               while the table beside it did not.
            */
            body > *:not(nav) { margin-left: 2rem; margin-right: 2rem; }
            body > table { width: calc(100% - 4rem); }

            /*
               Links inside the page, which had no colour of their own: the
               browser's blue and visited purple on a dark brown background,
               which read as broken rather than as links.
            */
            a { color: var(--accent); }
            a:hover { text-decoration: none; }

            /* Sticky, because these tables are long and the way out should not be. */
            nav { position: sticky; top: 0; z-index: 2; display: flex; align-items: center;
                  gap: 1.25rem; padding: .75rem 2rem; background: var(--panel);
                  border-bottom: 1px solid var(--line); font-size: .9rem; }
            nav a { color: var(--fg); text-decoration: none; padding: .25rem 0;
                    border-bottom: 2px solid transparent; }
            nav a:hover { border-bottom-color: var(--accent); }
            nav .spacer { flex: 1; }
            nav .who { color: var(--quiet); font-size: .85rem; }

            table { border-collapse: collapse; width: calc(100% - 4rem); background: var(--panel);
                    border: 1px solid var(--line); border-radius: 10px; overflow: hidden; margin-top: 1rem; }
            th { text-align: left; padding: .6rem .75rem; background: var(--badge);
                 font-size: .78rem; text-transform: uppercase; letter-spacing: .04em;
                 color: var(--quiet); border-bottom: 1px solid var(--line); }
            td { text-align: left; padding: .7rem .75rem; border-bottom: 1px solid var(--line);
                 vertical-align: top; }
            tr:last-child td { border-bottom: 0; }
            tr:hover td { background: color-mix(in srgb, var(--badge) 40%, transparent); }
            tr.muted td { opacity: .55; }

            .badge { display: inline-block; padding: .1rem .5rem; border-radius: 999px;
                     background: var(--badge); font-size: .78rem; }
            nav .badge { margin-left: .35rem; }
            .badge.count { background: var(--danger); color: #fff; }
            .badge.hidden { background: var(--badge); color: var(--quiet); }
            .badge.live { color: var(--live); border: 1px solid currentColor; background: none; }
            .excerpt { color: var(--quiet); font-size: .85rem; margin-top: .2rem;
                       max-width: 32rem; white-space: pre-wrap; }
            .reason { font-size: .85rem; margin-bottom: .2rem; }
            .quiet, .hint { color: var(--quiet); }
            .hint { max-width: 44rem; }
            .empty { margin-top: 2rem; color: var(--quiet); }
            .actions { white-space: nowrap; }
            .actions form { display: inline; }

            /* Cards: one number each, the whole thing clickable. */
            .cards { display: flex; flex-wrap: wrap; gap: .75rem; margin-top: .5rem; }
            .card { display: flex; flex-direction: column; gap: .15rem; min-width: 11rem;
                    padding: .9rem 1.1rem; border-radius: 12px; text-decoration: none;
                    background: var(--panel); border: 1px solid var(--line); color: var(--fg); }
            .card:hover { border-color: var(--accent); }
            .card .value { font-size: 1.9rem; font-weight: 600; line-height: 1.1; }
            .card .label { font-size: .82rem; color: var(--quiet); }
            /* Colour only where there is something to do, so it keeps meaning something. */
            .card.urgent { border-color: var(--danger); }
            .card.urgent .value { color: var(--danger); }
            .links { display: flex; flex-direction: column; gap: .4rem; margin-top: .5rem; }

            .error { color: var(--danger); }
            input[type=submit], button { cursor: pointer; font: inherit; font-size: .85rem;
                   padding: .35rem .7rem; border-radius: 7px; border: 1px solid var(--line);
                   background: var(--panel); color: var(--fg); }
            input[type=submit]:hover { border-color: var(--accent); }
            input[type=submit].danger { border-color: var(--danger); color: var(--danger); }
            input[type=text], input[type=email], input[type=password], select {
                   font: inherit; padding: .35rem .5rem; border-radius: 7px;
                   border: 1px solid var(--line); background: var(--panel); color: var(--fg); }
            textarea { font: inherit; width: 100%; max-width: 30rem; padding: .4rem .5rem;
                   border-radius: 7px; border: 1px solid var(--line);
                   background: var(--panel); color: var(--fg); }

            /*
               Read on a phone as often as a laptop. Without a viewport meta a
               mobile browser renders at desktop width and zooms out; with it,
               this tightens the 2rem gutters to 1rem, lets the nav wrap, stacks
               the cards, and lets a wide table scroll inside itself rather than
               pushing the whole page sideways.
            */
            @media (max-width: 640px) {
              h1 { margin: 1rem 1rem .25rem; font-size: 1.3rem; }
              h2 { margin: 1.25rem 1rem .5rem; }
              body > *:not(nav) { margin-left: 1rem; margin-right: 1rem; }
              body > table { width: calc(100% - 2rem); }
              table { width: 100%; display: block; overflow-x: auto; }
              nav { flex-wrap: wrap; gap: .6rem 1rem; padding: .6rem 1rem; }
              nav .spacer { display: none; }
              nav .who { width: 100%; order: 99; }
              .card { min-width: 100%; }
              .excerpt, .hint { max-width: 100%; }
              textarea { max-width: 100%; }
            }
            """
        }
    }
}

/** A row cell whose summary opens the whole record inline — native <details>, no script. */
internal fun kotlinx.html.FlowContent.expandableCell(summaryText: String, body: kotlinx.html.DIV.() -> Unit) {
    details {
        summary { strong { +summaryText } }
        div("detail", body)
    }
}

/** A labelled line inside an expandable body; skipped when the value is blank. */
internal fun kotlinx.html.DL.field(label: String, value: String?) {
    if (!value.isNullOrBlank()) { dt { +label }; dd { +value } }
}
