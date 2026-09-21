package com.example.poster

import com.example.poster.invite.InviteLink
import com.example.poster.util.AppPreferences

/**
 * Swift's way in. The shared module is not exported through the ComposeApp
 * framework, so its types are invisible to Swift; forwarding one call is
 * smaller than exporting the module's whole surface.
 */
fun offerInviteUrl(url: String) {
    InviteLink.offer(url)
}

/**
 * The preference keys a UI test must clear to start from nobody signed in.
 *
 * Swift cannot see the shared module, and hardcoding the names there means they
 * drift the moment one is added — which is exactly what happened.
 */
fun sessionPreferenceKeys(): List<String> = AppPreferences.SESSION_KEYS
