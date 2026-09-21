package com.example.poster.util

/**
 * A comparison key for deciding whether two spellings are the same inbox.
 *
 * Always lowercased. For Gmail (gmail.com / googlemail.com) the dots in the
 * local part are dropped and a `+tag` is ignored, because Gmail treats
 * `a.b@gmail.com`, `ab@gmail.com` and `ab+x@gmail.com` as one address — so an
 * invite emailed to one spelling must accept the account signed up under
 * another. Used only for *matching*, never for storage or login, where a
 * looser rule could merge addresses that other providers keep distinct.
 */
fun emailMatchKey(email: String): String {
    val trimmed = email.trim().lowercase()
    val at = trimmed.lastIndexOf('@')
    if (at <= 0 || at == trimmed.length - 1) return trimmed
    val local = trimmed.substring(0, at)
    val domain = trimmed.substring(at + 1)
    if (domain == "gmail.com" || domain == "googlemail.com") {
        val bare = local.substringBefore('+').replace(".", "")
        return "$bare@gmail.com"
    }
    return "$local@$domain"
}
