package com.example.poster.auth

data class AuthConfig(
    val secret: String,
    val issuer: String = "poster-server",
    val audience: String = "poster-app",
    val realm: String = "poster-api",
    val accessTokenTtlSeconds: Long = 15 * 60,
    val refreshTokenTtlSeconds: Long = 30L * 24 * 60 * 60,
    /**
     * How long a sign-in may last however much it is used.
     *
     * The refresh window above slides: every rotation buys another thirty
     * days, so a session that is never idle for a month never ends, and a
     * copied token is good for as long as the thief keeps it warm. This is
     * measured from the sign-in itself and nothing moves it. Six months is
     * long enough that an everyday phone is never signed out by it and short
     * enough that a forgotten session does not last for ever.
     */
    val sessionMaxAgeSeconds: Long = 180L * 24 * 60 * 60,
) {
    init {
        require(secret.length >= 32) { "POSTER_JWT_SECRET must contain at least 32 characters" }
    }
}
