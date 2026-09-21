package com.example.poster.admin

import java.security.SecureRandom

/**
 * A password somebody has to read down a phone line and type on a keyboard the
 * size of a thumb.
 *
 * So: no `l`/`I`/`1`, no `O`/`0`, lower case throughout, and grouped in fours —
 * `hn4p-r7qk-2mtw`. Long enough that the groups do the remembering, not the
 * person. Twelve characters of this alphabet is about 59 bits, which is far
 * more than a password that lives until its owner picks a new one needs.
 */
object TemporaryPassword {
    private const val ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"
    private const val GROUPS = 3
    private const val GROUP_SIZE = 4

    private val random = SecureRandom()

    fun generate(): String = (0 until GROUPS).joinToString("-") {
        (0 until GROUP_SIZE)
            .map { ALPHABET[random.nextInt(ALPHABET.length)] }
            .joinToString("")
    }
}
