package com.example.poster.invite

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A link arrives from a chat app, so it is parsed rather than trusted. */
class InviteLinkTest {

    @Test
    fun readsTheCodeFromEitherShape() {
        assertEquals("HOME", InviteLink.parse("poster://join/HOME"))
        assertEquals("HOME", InviteLink.parse("poster://join?code=HOME"))
        assertEquals("HOME", InviteLink.parse("poster://join/HOME/"))
        assertEquals("HOME", InviteLink.parse("  poster://join/home  "))
    }

    @Test
    fun ignoresAnythingThatIsNotAnInvite() {
        assertNull(InviteLink.parse(null))
        assertNull(InviteLink.parse(""))
        assertNull(InviteLink.parse("poster://post/123"))
        assertNull(InviteLink.parse("poster://join"))
        assertNull(InviteLink.parse("poster://join/"))
    }

    /**
     * What gets shared has to be a link, not a scheme.
     *
     * Messengers linkify http and https and leave everything else as plain
     * text, so an invitation shared as poster:// arrived as grey characters
     * nobody could tap and the code had to be typed by hand.
     */
    @Test
    fun theSharedLinkIsOneAMessengerWillLinkify() {
        val url = InviteLink.buildUrl("HBWWBJ93")

        assertEquals("https://poster.example.com/join/HBWWBJ93", url)
        assertEquals(true, url.startsWith("https://"), "a messenger will not linkify $url")
    }

    /**
     * The manifest registers poster.example.com/join as a verified App Link, so a
     * tapped invitation arrives as https. Rejecting it here opened the app and
     * did nothing — a failure with no error message anywhere.
     */
    @Test
    fun anHttpsInvitationIsReadTheSameAsTheScheme() {
        assertEquals("HBWWBJ93", InviteLink.parse("https://poster.example.com/join/HBWWBJ93"))
        assertEquals("HBWWBJ93", InviteLink.parse("https://poster.example.com/join?code=HBWWBJ93"))
        assertEquals("HBWWBJ93", InviteLink.parse("poster://join/HBWWBJ93"))
    }

    /** Somebody else's domain is not an invitation, whatever the path says. */
    @Test
    fun anotherSiteIsNotAnInvitation() {
        assertNull(InviteLink.parse("https://example.com/join/HBWWBJ93"))
        assertNull(InviteLink.parse("https://poster.example.com.evil.com/join/HBWWBJ93"))
    }

    /** The page's own button still needs the form that reaches the app. */
    @Test
    fun theDeepLinkFormIsStillAvailableAndStillParses() {
        val deep = InviteLink.buildDeepLink("HBWWBJ93")

        assertEquals("poster://join/HBWWBJ93", deep)
        assertEquals("HBWWBJ93", InviteLink.parse(deep))
    }

    @Test
    fun buildsALinkItCanReadBack() {
        val url = InviteLink.buildUrl("GROUP_A_INVITE")

        assertEquals("GROUP_A_INVITE", InviteLink.parse(url))
    }

    @Test
    fun offeringALinkMakesItPendingUntilConsumed() {
        InviteLink.consume()

        InviteLink.offer("poster://join/HOME")
        assertEquals("HOME", InviteLink.pending.value)

        // A URL that is not an invite must not clear one already waiting.
        InviteLink.offer("https://example.com")
        assertEquals("HOME", InviteLink.pending.value)

        InviteLink.consume()
        assertNull(InviteLink.pending.value)
    }
}
