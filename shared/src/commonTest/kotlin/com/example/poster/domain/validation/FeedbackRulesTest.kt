package com.example.poster.domain.validation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeedbackRulesTest {

    @Test
    fun messageMustBeNonBlankAndWithinTheLimit() {
        assertTrue(FeedbackRules.messageValid("Please add dark mode"))
        assertTrue(FeedbackRules.messageValid("  trimmed  "))
        assertFalse(FeedbackRules.messageValid(""))
        assertFalse(FeedbackRules.messageValid("   "))
        assertTrue(FeedbackRules.messageValid("x".repeat(FeedbackRules.MESSAGE_LIMIT)))
        assertFalse(FeedbackRules.messageValid("x".repeat(FeedbackRules.MESSAGE_LIMIT + 1)))
    }

    @Test
    fun tooLongIgnoresSurroundingWhitespace() {
        assertFalse(FeedbackRules.messageTooLong("  ${"x".repeat(FeedbackRules.MESSAGE_LIMIT)}  "))
        assertTrue(FeedbackRules.messageTooLong("x".repeat(FeedbackRules.MESSAGE_LIMIT + 1)))
    }
}
