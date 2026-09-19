package com.example.poster.auth

import com.example.poster.model.AuthTokens

interface AuthTokenStorage {
    suspend fun load(): AuthTokens?
    suspend fun save(tokens: AuthTokens)
    suspend fun clear()
}

class InMemoryAuthTokenStorage : AuthTokenStorage {
    private var tokens: AuthTokens? = null

    override suspend fun load(): AuthTokens? = tokens

    override suspend fun save(tokens: AuthTokens) {
        this.tokens = tokens
    }

    override suspend fun clear() {
        tokens = null
    }
}
