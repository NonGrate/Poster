package com.example.poster.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class EmailMatchTest {

    @Test
    fun gmailIgnoresDotsAndTagsAndCase() {
        // The bug that started this: an invite to one spelling, the account under
        // another spelling of the same Gmail inbox.
        val key = emailMatchKey("dei.m.o.scz@gmail.com")
        assertEquals(key, emailMatchKey("deim.os.cz@gmail.com"))
        assertEquals(key, emailMatchKey("DEIM.OS.CZ@Gmail.com"))
        assertEquals(key, emailMatchKey("deimoscz+shipaton@googlemail.com"))
    }

    @Test
    fun nonGmailKeepsDots() {
        // Other providers treat dots as significant, so they must stay distinct.
        assertNotEquals(
            emailMatchKey("a.b@fastmail.com"),
            emailMatchKey("ab@fastmail.com"),
        )
        assertEquals("a.b@fastmail.com", emailMatchKey("  A.B@Fastmail.com "))
    }

    @Test
    fun garbageIsLeftAloneApartFromCase() {
        assertEquals("not-an-email", emailMatchKey("Not-An-Email"))
        assertEquals("trailing@", emailMatchKey("trailing@"))
    }
}
