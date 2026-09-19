package com.example.poster.model

/**
 * The languages a post can be written in, and a person can read.
 *
 * Two, because that is what this app is for. The code is what is stored and
 * matched; the name is what a person picks from a list, written in that
 * language — somebody who only reads Russian has to be able to find "Русский"
 * without reading the English word for it.
 *
 * A list of languages is stored as one comma-separated column rather than a
 * table of its own. There are two of them and a person picks from a fixed list,
 * so a join table would be machinery around a field that will never hold more
 * than a handful of values.
 */
object Language {
    const val ENGLISH = "en"
    const val RUSSIAN = "ru"

    /** In the order they are offered. */
    val ALL = listOf(ENGLISH, RUSSIAN)

    /** What existing posts and existing people are treated as. */
    const val DEFAULT = ENGLISH

    /** Written in the language it names, so it can be recognised by its reader. */
    fun nameOf(code: String): String = when (code) {
        ENGLISH -> "English"
        RUSSIAN -> "Русский"
        else -> code
    }

    fun isKnown(code: String): Boolean = code in ALL

    /** Anything unrecognised is dropped rather than stored. */
    fun parse(stored: String): List<String> =
        stored.split(",").map { it.trim() }.filter(::isKnown).distinct()

    /**
     * Never empty: somebody who has turned every language off would have an
     * empty feed and no way to understand why, so the default stands in.
     */
    fun store(codes: List<String>): String =
        codes.filter(::isKnown).distinct().ifEmpty { listOf(DEFAULT) }.joinToString(",")

}
