package com.example.poster.domain.validation

object TagRules {
    private const val MAX_LENGTH = 50

    /**
     * How many tags one post carries.
     *
     * A card is read at a glance, and past a handful the tags stop narrowing
     * anything — they are the whole card. Here rather than in either screen so
     * the picker and the card cannot drift: a picker that allows six and a card
     * that draws five is how tags went missing without anyone being told.
     */
    const val MAX_PER_POST = 5

    /**
     * How many tags the feed filter takes at once.
     *
     * The same number as [MAX_PER_POST] today, and deliberately its own
     * constant: one is about how much a card can show, the other about how wide
     * a question stays useful. Moving one should not move the other by accident.
     */
    const val MAX_IN_FILTER = 5

    /**
     * Letters in any script, digits, hyphen, underscore and space.
     *
     * Written as character predicates rather than a regex on purpose. The
     * obvious pattern is `\p{L}`, and Kotlin/Native's regex engine does not
     * support Unicode property classes — it throws while initialising the
     * object, so every tag operation crashed the iOS app while the JVM was
     * perfectly happy. `Char.isLetter()` means the same thing on both.
     *
     * Spaces are allowed and kept: people write "за маму", and "за_маму" reads
     * like a machine wrote it. Punctuation and symbols stay out, because `@` in
     * a tag is a typo rather than an intention.
     */
    fun isValid(name: String): Boolean =
        name.length in 1..MAX_LENGTH && name.all { it.isLetter() || it.isDigit() || it in "-_ " }

    /** Runs of whitespace collapse to one space, so "за  маму" and "за маму" are one tag. */
    fun normalize(raw: String): String =
        raw.trim().lowercase().split(' ', '\t', '\n').filter { it.isNotEmpty() }.joinToString(" ")

    sealed class Error(val reason: String) {
        data object TooLong : Error("too_long")
        data object InvalidCharacters : Error("invalid_characters")
        data object Empty : Error("empty")
    }

    fun validate(raw: String): Result<String> {
        val normalized = normalize(raw)
        if (normalized.isBlank()) return Result.failure(IllegalArgumentException(Error.Empty.reason))
        if (normalized.length > MAX_LENGTH) return Result.failure(IllegalArgumentException(Error.TooLong.reason))
        if (!isValid(normalized)) return Result.failure(IllegalArgumentException(Error.InvalidCharacters.reason))
        return Result.success(normalized)
    }
}
