package com.example.poster

import com.example.poster.model.AccountLocalRepository

/**
 * Marks an address confirmed, for tests about something other than confirming.
 *
 * Sharing a post requires a confirmed address. Most of these suites are about
 * visibility, moderation or languages and only post a post to have something
 * to look at, so they would otherwise all have to walk through an email they do
 * not care about. The ones that *are* about confirmation do it the real way.
 */
internal fun confirmAddress(email: String, at: String = "2026-08-18T12:00:00Z") {
    val accounts = AccountLocalRepository(testDatabase())
    val user = accounts.userByEmail(email) ?: error("no account for $email")
    accounts.addOrUpdateUser(user.copy(verifiedAt = at))
}
