package com.example.poster

import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.acceptLanguageItems

/**
 * Which of the two languages this app speaks to answer a browser in.
 *
 * The browser's list is ordered by preference, so the first language this app
 * knows wins. Anything else — including no header at all, which is what a
 * scanner or a curl request sends — gets English, the app's own default.
 *
 * One function because three pages need it and a fourth will: the landing page,
 * the privacy policy, and the pages the links in the emails land on. They had
 * begun to carry a copy each.
 *
 * Note this asks the *browser*, not the account, even on pages reached from an
 * email where the token could name the person. That is deliberate: reading the
 * account would mean resolving the token before the page is allowed to spend
 * it, and the browser's own preference is both honest and free.
 */
fun ApplicationCall.prefersRussian(): Boolean {
    // An explicit choice beats what the browser guessed. Somebody reading
    // English on a Russian laptop is not an edge case in a family spread
    // across countries — and the alternative is a page they cannot switch.
    when (request.queryParameters["lang"]?.lowercase()) {
        "ru" -> return true
        "en" -> return false
    }
    val ordered = runCatching { request.acceptLanguageItems() }.getOrDefault(emptyList())
    val first = ordered.firstOrNull { item ->
        val tag = item.value.lowercase()
        tag.startsWith("ru") || tag.startsWith("en")
    }
    return first?.value?.lowercase()?.startsWith("ru") == true
}
