package com.example.poster.domain.validation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountRulesTest {

    @Test
    fun nameMustBeNonBlankAndWithinTheLimit() {
        assertTrue(AccountRules.nameValid("Ada"))
        assertTrue(AccountRules.nameValid("  Ada  ")) // trimmed
        assertFalse(AccountRules.nameValid(""))
        assertFalse(AccountRules.nameValid("   "))
        assertFalse(AccountRules.nameValid("x".repeat(AccountRules.NAME_LIMIT + 1)))
        assertTrue(AccountRules.nameValid("x".repeat(AccountRules.NAME_LIMIT)))
    }

    @Test
    fun passwordHasAFloorAndACeiling() {
        assertFalse(AccountRules.passwordValid("short"))
        assertTrue(AccountRules.passwordValid("longenough"))
        assertFalse(AccountRules.passwordValid("x".repeat(AccountRules.PASSWORD_MAX + 1)))
    }

    @Test
    fun emailIsTheShapeAnAddressTakes() {
        assertTrue(AccountRules.emailValid("someone@example.com"))
        assertTrue(AccountRules.emailValid("  Someone@Example.com "))
        assertFalse(AccountRules.emailValid("no-at-sign"))
        assertFalse(AccountRules.emailValid("two@@example.com"))
        assertFalse(AccountRules.emailValid("nodot@example"))
        assertFalse(AccountRules.emailValid("has space@example.com"))
        assertFalse(AccountRules.emailValid("@example.com"))
    }
}
