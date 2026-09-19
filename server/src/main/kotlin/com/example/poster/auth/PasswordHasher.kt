package com.example.poster.auth

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

interface PasswordHasher {
    fun hash(password: String): String
    fun verify(password: String, encodedHash: String): Boolean
}

class Argon2PasswordHasher(
    private val secureRandom: SecureRandom = SecureRandom(),
) : PasswordHasher {
    override fun hash(password: String): String {
        val salt = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)
        val hash = generate(password, salt)
        val encoder = Base64.getEncoder().withoutPadding()
        return "${PREFIX}\$${encoder.encodeToString(salt)}\$${encoder.encodeToString(hash)}"
    }

    override fun verify(password: String, encodedHash: String): Boolean {
        val parts = encodedHash.split('$')
        if (parts.size != 6 || parts[1] != "argon2id" || parts[2] != "v=19" || parts[3] != PARAMETERS) {
            return false
        }
        return runCatching {
            val decoder = Base64.getDecoder()
            val salt = decoder.decode(parts[4])
            val expected = decoder.decode(parts[5])
            MessageDigest.isEqual(expected, generate(password, salt, expected.size))
        }.getOrDefault(false)
    }

    private fun generate(password: String, salt: ByteArray, outputLength: Int = HASH_BYTES): ByteArray {
        val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withMemoryAsKB(MEMORY_KIB)
            .withIterations(ITERATIONS)
            .withParallelism(PARALLELISM)
            .withSalt(salt)
            .build()
        val generator = Argon2BytesGenerator().apply { init(parameters) }
        return ByteArray(outputLength).also { generator.generateBytes(password.toCharArray(), it) }
    }

    private companion object {
        const val MEMORY_KIB = 19_456
        const val ITERATIONS = 2
        const val PARALLELISM = 1
        const val SALT_BYTES = 16
        const val HASH_BYTES = 32
        const val PARAMETERS = "m=$MEMORY_KIB,t=$ITERATIONS,p=$PARALLELISM"
        const val PREFIX = "\$argon2id\$v=19\$$PARAMETERS"
    }
}
