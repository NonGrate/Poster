package com.example.poster.invite

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.poster.config.AppInfo

/**
 * Invite links, in the form the app can honour today: `poster://join/CODE`.
 *
 * A custom scheme needs no domain and no signing fingerprint, unlike a verified
 * https App Link, so this works on a self-built app on someone's phone. The cost
 * is that some messengers will not turn it into a tappable link, which is why
 * the invite code is still shown and still typeable by hand.
 */
object InviteLink {
    const val SCHEME = AppInfo.SCHEME
    private const val JOIN_HOST = "join"

    /** Where the web page lives, and what an invitation is shared as. */
    const val WEB_ORIGIN = AppInfo.WEB_ORIGIN

    /**
     * A code that arrived from a link and has not been acted on yet.
     *
     * Global state, deliberately: the platform entry points that receive a link
     * — an Android Intent, an iOS URL callback — run outside the composition and
     * have no injected graph to write into.
     */
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    /**
     * Called by platform code with whatever URL the system handed it.
     *
     * Only invites land here now; verification and reset links go to
     * [PendingAppLink], which holds every kind. This stays because the invite
     * flow reads [pending] directly.
     */
    fun offer(url: String?) {
        parse(url)?.let { _pending.value = it }
        PendingAppLink.offer(url)
    }

    fun consume() {
        _pending.value = null
    }

    /**
     * The address to hand somebody, which is https rather than poster://.
     *
     * A custom scheme is not a link. Telegram, WhatsApp and every other
     * messenger linkify http and https and leave anything else as plain text —
     * so an invitation arrived as grey characters nobody could tap, and the
     * code had to be typed by hand. That is the bug this fixes; the scheme was
     * never reachable from a chat in the first place.
     *
     * https also degrades properly. Verified as an App Link it opens the app
     * directly; unverified, or on a desktop, it opens the page at /join/CODE
     * which offers the poster:// form. Either way somebody lands somewhere.
     */
    fun buildUrl(inviteCode: String): String = "$WEB_ORIGIN/$JOIN_HOST/${inviteCode.trim()}"

    /** The form that reaches the app directly, for the page's own button. */
    fun buildDeepLink(inviteCode: String): String = "$SCHEME://$JOIN_HOST/${inviteCode.trim()}"

    /**
     * Accepts both forms, and the query variant of each.
     *
     * `poster://join/CODE`, `poster://join?code=CODE`,
     * `https://poster.example.com/join/CODE` and its query variant — because a link
     * that has been through a chat app and back may arrive any of those ways,
     * and because the https one is what the manifest registers as a verified
     * App Link. Rejecting https here meant a verified link would open the app
     * and then do nothing at all, which is the failure that has no error
     * message. Anything else is not an invite and is ignored rather than
     * guessed at.
     */
    fun parse(url: String?): String? {
        val raw = url?.trim().orEmpty()
        val prefix = when {
            raw.startsWith("$SCHEME://", ignoreCase = true) -> "$SCHEME://"
            raw.startsWith("$WEB_ORIGIN/", ignoreCase = true) -> "$WEB_ORIGIN/"
            else -> return null
        }
        val rest = raw.removeRange(0, prefix.length)
        val path = rest.substringBefore('?')
        val query = rest.substringAfter('?', "")
        if (!path.startsWith(JOIN_HOST, ignoreCase = true)) return null

        val fromPath = path.removePrefix(JOIN_HOST).trim('/')
        val fromQuery = query.split('&')
            .firstOrNull { it.startsWith("code=", ignoreCase = true) }
            ?.removePrefix("code=")
            ?.removePrefix("CODE=")

        return listOf(fromPath, fromQuery)
            .firstOrNull { !it.isNullOrBlank() }
            ?.trim()
            ?.uppercase()
    }
}
