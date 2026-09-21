package com.example.poster.domain.validation

/**
 * What a name, email and password may be.
 *
 * Here, in shared code, rather than in either end: the form and the server have
 * to agree, or someone types a name the client accepts and the server rejects
 * (or worse, the reverse — the client trusts what the server stored, and an
 * unbounded name pasted past the form turns up in a settings header and an
 * invite email). Both ends call these, and the server call is the one that
 * counts, since a client can be bypassed.
 *
 * Names are trimmed before checking: trailing spaces are not a name, and a field
 * of only spaces is empty. Emails are trimmed and lowercased for the same reason
 * and so two spellings of one address do not become two accounts.
 */
object AccountRules {
    const val NAME_LIMIT = 50
    const val SURNAME_LIMIT = 50
    const val PASSWORD_MIN = 8
    const val PASSWORD_MAX = 128
    const val EMAIL_MAX = 254

    fun nameValid(name: String): Boolean = name.trim().length in 1..NAME_LIMIT
    fun surnameValid(surname: String): Boolean = surname.trim().length in 1..SURNAME_LIMIT

    /** A minimum only: long enough to be worth having, capped so it cannot be a payload. */
    fun passwordValid(password: String): Boolean = password.length in PASSWORD_MIN..PASSWORD_MAX

    /**
     * Not RFC-complete — that grammar accepts things no mail server does. This is
     * the shape a person's address takes: something, an @, a domain with a dot.
     */
    fun emailValid(email: String): Boolean {
        val trimmed = email.trim()
        if (trimmed.length !in 3..EMAIL_MAX) return false
        val at = trimmed.indexOf('@')
        if (at <= 0 || at != trimmed.lastIndexOf('@')) return false
        val domain = trimmed.substring(at + 1)
        return domain.length >= 3 && domain.contains('.') &&
            !domain.startsWith('.') && !domain.endsWith('.') &&
            !trimmed.contains(' ')
    }
}
