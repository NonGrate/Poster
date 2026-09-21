package com.example.poster

import com.example.poster.config.BrandPalette
import kotlinx.html.BODY
import kotlinx.html.HEAD
import kotlinx.html.meta
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.span
import kotlinx.html.summary

/**
 * The control in the corner: which language, and light or dark.
 *
 * A `<details>` rather than a scripted menu, so it opens, closes, and takes
 * keyboard focus with nothing loaded and nothing running. The language half is
 * two ordinary links — the server already answers in either language, so
 * switching is a page it can render rather than something to do in the browser.
 *
 * The theme half needs script, because a preference has to outlive the page.
 * It is the only script here, it is inline, and it is about fifteen lines: the
 * page still asks the network for nothing at all.
 */
fun BODY.siteChrome(russian: Boolean, path: String) {
    div("chrome") {
        details {
            summary { +(if (russian) "Настройки" else "Options") }
            div("chrome-menu") {
                span("chrome-label") { +(if (russian) "Язык" else "Language") }
                div("chrome-row") {
                    // Carries the current page, so switching language on the
                    // privacy policy does not land somebody back at the top.
                    a(href = "$path?lang=en", classes = if (russian) "chrome-choice" else "chrome-choice on") {
                        +"English"
                    }
                    a(href = "$path?lang=ru", classes = if (russian) "chrome-choice on" else "chrome-choice") {
                        +"Русский"
                    }
                }
                span("chrome-label") { +(if (russian) "Оформление" else "Appearance") }
                div("chrome-row") {
                    button(classes = "chrome-choice") {
                        attributes["data-theme-choice"] = "light"
                        +(if (russian) "Светлое" else "Light")
                    }
                    button(classes = "chrome-choice") {
                        attributes["data-theme-choice"] = "dark"
                        +(if (russian) "Тёмное" else "Dark")
                    }
                    button(classes = "chrome-choice") {
                        attributes["data-theme-choice"] = "system"
                        +(if (russian) "Как в системе" else "System")
                    }
                }
            }
        }
    }
}

/**
 * Applies a remembered theme, and remembers a chosen one.
 *
 * Runs in the head, before anything is painted: setting the attribute after
 * the body renders means a light page flashes white in front of somebody who
 * asked for dark, which is worse than not offering the choice.
 *
 * Every access to localStorage is wrapped, because a browser set to refuse
 * site data throws on the read rather than returning nothing — and a page that
 * dies there would render unstyled instead of merely unremembered.
 */
const val THEME_SCRIPT = """
(function () {
  var root = document.documentElement;
  function apply(choice) {
    if (choice === 'light' || choice === 'dark') root.setAttribute('data-theme', choice);
    else root.removeAttribute('data-theme');
  }
  try { apply(localStorage.getItem('poster-theme')); } catch (e) {}
  document.addEventListener('click', function (event) {
    var button = event.target.closest('[data-theme-choice]');
    if (!button) return;
    var choice = button.getAttribute('data-theme-choice');
    apply(choice);
    try {
      if (choice === 'system') localStorage.removeItem('poster-theme');
      else localStorage.setItem('poster-theme', choice);
    } catch (e) {}
    var open = button.closest('details');
    if (open) open.open = false;
  });
})();
"""

/**
 * The token Google Search Console issues to prove somebody owns this domain.
 *
 * Set POSTER_SITE_VERIFICATION to the `content` value from the "HTML tag"
 * method and it appears in the front page's head. Unset, nothing is emitted:
 * an empty verification tag is worse than none, because it looks like a failed
 * attempt rather than a deliberate absence.
 *
 * The DNS method needs none of this and is the better one where the registrar
 * is to hand — it covers every subdomain and survives the site being rebuilt.
 */
val SITE_VERIFICATION: String?
    get() = System.getenv("POSTER_SITE_VERIFICATION")?.takeIf { it.isNotBlank() }

/**
 * Emits the verification tag, on every page rather than only the front one.
 *
 * Search Console verifies whichever URL it was given, and the consent screen
 * names more than one of ours — home page, privacy policy, terms. Putting the
 * tag in one head and not the others makes verification depend on which URL
 * somebody happened to type, which is not a thing worth debugging later.
 */
fun HEAD.siteVerification() {
    SITE_VERIFICATION?.let { meta(name = "google-site-verification", content = it) }
}

/** A palette colour as CSS (`#rrggbb`), so the web pages wear poster.properties too. */
internal fun css(argb: Long): String = "#%06x".format(argb and 0xFFFFFF)
