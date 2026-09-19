package com.example.poster.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.JWTVerifier
import java.time.Clock
import java.time.Instant
import java.util.Date

class TokenService(
    private val config: AuthConfig,
    private val clock: Clock = Clock.systemUTC(),
) {
    val verifier: JWTVerifier = JWT.require(Algorithm.HMAC256(config.secret))
        .withIssuer(config.issuer)
        .withAudience(config.audience)
        .build()

    fun createAccessToken(userId: String): String {
        val now = Instant.now(clock)
        return JWT.create()
            .withIssuer(config.issuer)
            .withAudience(config.audience)
            .withSubject(userId)
            .withClaim("type", "access")
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date.from(now.plusSeconds(config.accessTokenTtlSeconds)))
            .sign(Algorithm.HMAC256(config.secret))
    }
}
