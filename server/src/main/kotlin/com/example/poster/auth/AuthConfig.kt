package com.example.poster.auth

data class AuthConfig(
    val secret: String,
    val issuer: String = "poster-server",
    val audience: String = "poster-app",
    val realm: String = "poster-api",
    val accessTokenTtlSeconds: Long = 15 * 60,
    val refreshTokenTtlSeconds: Long = 30L * 24 * 60 * 60,
) {
    init {
        require(secret.length >= 32) { "POSTER_JWT_SECRET must contain at least 32 characters" }
    }
}
