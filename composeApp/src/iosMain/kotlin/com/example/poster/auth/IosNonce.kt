package com.example.poster.auth

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.Foundation.NSData
import platform.Foundation.NSDataBase64Encoding64CharacterLineLength
import platform.Foundation.base64EncodedStringWithOptions
import platform.Foundation.create
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault

/**
 * A fresh nonce, and the hash that goes to the provider.
 *
 * The provider is given the hash and puts it in the token it mints; the value
 * goes to this app's own server, which hashes it again and compares. A token
 * on its own therefore proves nothing: whoever holds it has the hash, and the
 * value behind it never left the device that asked.
 */
@OptIn(ExperimentalForeignApi::class)
internal object IosNonce {

    fun random(): String {
        val bytes = ByteArray(32)
        bytes.usePinned { pinned ->
            SecRandomCopyBytes(kSecRandomDefault, bytes.size.toULong(), pinned.addressOf(0))
        }
        return bytes.base64Url()
    }

    fun hash(nonce: String): String {
        val input = nonce.encodeToByteArray()
        val digest = ByteArray(CC_SHA256_DIGEST_LENGTH)
        input.usePinned { inputPin ->
            digest.usePinned { digestPin ->
                CC_SHA256(
                    inputPin.addressOf(0),
                    input.size.toUInt(),
                    digestPin.addressOf(0).reinterpret(),
                )
            }
        }
        return digest.base64Url()
    }

    /** Base64url without padding, matching what the server hashes against. */
    private fun ByteArray.base64Url(): String {
        val data = usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
        return data.base64EncodedStringWithOptions(0uL)
            .replace('+', '-')
            .replace('/', '_')
            .trimEnd('=')
    }
}
