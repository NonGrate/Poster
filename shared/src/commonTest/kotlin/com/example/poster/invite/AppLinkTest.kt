package com.example.poster.invite

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The one parser that decides what a tapped link means. Post share links are
 * the newest kind; the token is a path segment, not a `?token=` query.
 */
class AppLinkTest {

    @Test
    fun parsesAPostDeepLink() {
        assertEquals(AppLink.Post("abc123"), AppLink.parse("poster://post/abc123"))
    }

    @Test
    fun parsesAPostWebLink() {
        assertEquals(AppLink.Post("abc123"), AppLink.parse("https://poster.example.com/p/abc123"))
    }

    @Test
    fun buildsMatchingUrls() {
        val token = "abc123"
        assertEquals("https://poster.example.com/p/abc123", AppLink.postWebUrl(token))
        assertEquals("poster://post/abc123", AppLink.postDeepLink(token))
        // What is built parses back to what it came from.
        assertEquals(AppLink.Post(token), AppLink.parse(AppLink.postWebUrl(token)))
        assertEquals(AppLink.Post(token), AppLink.parse(AppLink.postDeepLink(token)))
    }

    @Test
    fun aPostLinkWithoutATokenIsNotParsed() {
        assertNull(AppLink.parse("poster://post"))
        assertNull(AppLink.parse("poster://post/"))
    }

    @Test
    fun stillParsesTheOtherKinds() {
        assertEquals(AppLink.Join("CODE"), AppLink.parse("poster://join/CODE"))
        assertEquals(AppLink.Verify("t"), AppLink.parse("poster://verify?token=t"))
        assertEquals(AppLink.Verified, AppLink.parse("poster://verified"))
        assertEquals(AppLink.Reset("t"), AppLink.parse("poster://reset?token=t"))
    }

    @Test
    fun rejectsJunk() {
        assertNull(AppLink.parse(null))
        assertNull(AppLink.parse(""))
        assertNull(AppLink.parse("https://example.com/p/abc"))
        assertNull(AppLink.parse("poster://unknown/x"))
    }
}
