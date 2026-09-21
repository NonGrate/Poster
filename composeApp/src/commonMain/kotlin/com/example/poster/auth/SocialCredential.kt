package com.example.poster.auth

/**
 * What a provider hands back, and the value that proves it was asked for here.
 *
 * [nonce] is a random value this device generated. Only its SHA-256 went to
 * Google or Apple, and the token they minted carries that hash — so sending
 * the value alongside the token is how the server tells a sign-in somebody
 * started from a token somebody found. Without it an identity token is a
 * bearer credential: anything holding one minted for this app's client id
 * could present it and be signed in as its subject.
 */
data class SocialCredential(val idToken: String, val nonce: String)
