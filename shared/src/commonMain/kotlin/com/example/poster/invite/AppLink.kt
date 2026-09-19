package com.example.poster.invite

import com.example.poster.config.AppInfo

/**
 * Everything the app can be opened with.
 *
 * One parser rather than three, because the platform entry points hand over a
 * URL without knowing what it is: an Android Intent and an iOS URL callback
 * both say "somebody tapped this", and what it means is decided here.
 *
 * The links in emails point at `https://poster.example.com/...` so they work in any
 * browser on any device. Those pages then offer the `poster://` form, which
 * is what reaches the app. When Stage 12's verified App Links land, the https
 * address will reach the app directly and this parser will accept both.
 */
sealed interface AppLink {
    /** `poster://join/CODE` */
    data class Join(val inviteCode: String) : AppLink

    /** `poster://verify?token=…` */
    data class Verify(val token: String) : AppLink

    /** `poster://reset?token=…` */
    data class Reset(val token: String) : AppLink
    /** An emailed sign-in link (feature.magicLink). */
    data class Magic(val token: String) : AppLink

    /**
     * The address is already confirmed; this only asks the app to open and
     * catch up.
     *
     * Sent by the page *after* it has done the confirming, which is why it
     * carries no token — the one it had is spent, and offering it again would
     * be a link that fails by design. The app still has an account record
     * saying "unverified", so acting on this means refreshing that rather than
     * showing a dialog about work already done.
     */
    data object Verified : AppLink

    /** `poster://post/TOKEN` or `https://poster.example.com/p/TOKEN` */
    data class Post(val token: String) : AppLink

    companion object {
        const val SCHEME = AppInfo.SCHEME

        /** The link that gets shared: opens the public web page. */
        fun postWebUrl(token: String): String = "${InviteLink.WEB_ORIGIN}/p/$token"

        /** The custom-scheme form the web page offers to open the app. */
        fun postDeepLink(token: String): String = "$SCHEME://post/$token"

        fun parse(url: String?): AppLink? {
            val raw = url?.trim().orEmpty()
            // Both forms. The manifest registers poster.example.com/verify, /reset,
            // /verified and /join as verified App Links, so those arrive as
            // https and used to be dropped on the floor here — the app opened
            // and did nothing, which is the shape of failure that reports
            // itself as "the link is broken".
            val prefix = when {
                raw.startsWith("$SCHEME://", ignoreCase = true) -> "$SCHEME://"
                raw.startsWith("${InviteLink.WEB_ORIGIN}/", ignoreCase = true) ->
                    "${InviteLink.WEB_ORIGIN}/"
                else -> return null
            }
            val rest = raw.removeRange(0, prefix.length)
            val host = rest.substringBefore('?').substringBefore('/').lowercase()
            val token = queryValue(rest, "token")

            return when (host) {
                "join" -> InviteLink.parse(raw)?.let(::Join)
                "verify" -> token?.let(::Verify)
                "verified" -> Verified
                "reset" -> token?.let(::Reset)
                "magic" -> token?.let(::Magic)
                // poster://post/TOKEN and the web https://…/p/TOKEN — the
                // token is the path segment, not a ?token= query.
                "post", "p" -> pathSegment(rest)?.let(::Post)
                else -> null
            }
        }

        /** The first path segment after the host, e.g. TOKEN in `post/TOKEN`. */
        private fun pathSegment(rest: String): String? =
            rest.substringBefore('?')
                .substringAfter('/', "")
                .substringBefore('/')
                .trim()
                .takeIf { it.isNotEmpty() }

        private fun queryValue(rest: String, name: String): String? =
            rest.substringAfter('?', "")
                .split('&')
                .firstOrNull { it.startsWith("$name=", ignoreCase = true) }
                ?.substringAfter('=')
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
    }
}
