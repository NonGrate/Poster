package com.example.poster.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * A fresh nonce, and the hash that goes to the provider.
 *
 * The provider is given the hash and puts it in the token it mints; the value
 * goes to this app's own server, which hashes it again and compares. A token
 * on its own therefore proves nothing: whoever holds it has the hash, and the
 * value behind it never left the device that asked.
 */
internal object AndroidNonce {
    private val base64: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()

    fun random(): String =
        base64.encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))

    fun hash(nonce: String): String =
        base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(nonce.toByteArray()))
}
