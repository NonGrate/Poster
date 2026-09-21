package com.example.poster.domain.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class TagRulesTest {

    @Test
    fun normalize_keepsSpacesAndLowercases() {
        assertEquals("family post", TagRules.normalize(" Family Post "))
        assertEquals("family post", TagRules.normalize("Family   Post"))
    }

    @Test
    fun validate_acceptsValidTags() {
        val result = TagRules.validate("wellbeing-2025")
        assertTrue(result.isSuccess)
        assertEquals("wellbeing-2025", result.getOrThrow())
    }

    @Test
    fun validate_rejectsTooLong() {
        val long = "a".repeat(51)
        val result = TagRules.validate(long)
        assertTrue(result.isFailure)
    }

    @Test
    fun validate_rejectsInvalidCharacters() {
        val result = TagRules.validate("invalid@tag!")
        assertTrue(result.isFailure)
    }

    /** The app ships in Russian; a Russian tag is not an invalid tag. */
    @Test
    fun validate_acceptsLettersFromAnyScript() {
        assertEquals("исцеление", TagRules.validate("Исцеление").getOrThrow())
        assertEquals("семья", TagRules.validate(" Семья ").getOrThrow())
        assertEquals("за маму", TagRules.validate("За маму").getOrThrow())
        assertEquals("үй-бүлө", TagRules.validate("Үй-бүлө").getOrThrow())
        assertEquals("صلاة", TagRules.validate("صلاة").getOrThrow())
        assertEquals("祈り", TagRules.validate("祈り").getOrThrow())
    }

    @Test
    fun validate_stillRejectsPunctuationAndSymbols() {
        assertTrue(TagRules.validate("tag!").isFailure)
        assertTrue(TagRules.validate("a@b").isFailure)
        assertTrue(TagRules.validate("#hash").isFailure)
        assertTrue(TagRules.validate("emoji🙏").isFailure)
    }

    /**
     * [TagRules.isValid] is a question about characters, not about case: case is
     * [TagRules.normalize]'s job. The pattern cannot exclude capitals anyway
     * without also excluding every script that has no capitals.
     */
    @Test
    fun isValid_checksCharactersNotCase() {
        assertTrue(TagRules.isValid("ok_tag-123"))
        assertTrue(TagRules.isValid("good tag"))
        assertTrue(TagRules.isValid("Bad Tag"))
        assertFalse(TagRules.isValid("bad!tag"))
    }
}
