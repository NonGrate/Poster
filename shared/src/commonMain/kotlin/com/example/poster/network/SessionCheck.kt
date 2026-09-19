package com.example.poster.network

import com.example.poster.model.User

/**
 * What the server said when asked whether a stored session is still good.
 *
 * The distinction is the point. Every call in this client used to collapse a
 * failure into `null`, so "your token is no longer valid" and "there is no
 * signal in this building" looked identical — and the app chose one meaning for
 * both. It chose sign-out, which is the wrong one to guess: it drops somebody
 * out of an app that works offline, and it loses nothing to be patient.
 */
sealed interface SessionCheck {
    /** The server answered, and this is who they are. */
    data class Valid(val user: User) : SessionCheck

    /**
     * The server answered, and the answer was no — the token is not accepted,
     * or the account is gone. This is the only case worth signing somebody out
     * for, because it is the only one where staying signed in is a lie.
     */
    data object Rejected : SessionCheck

    /**
     * No answer: no network, a timeout, or the server having a bad day. The
     * session is untouched, because nothing about it has been contradicted.
     */
    data class Unreachable(val cause: Throwable?) : SessionCheck
}
