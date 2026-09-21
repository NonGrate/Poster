package com.example.poster.di

import com.example.poster.auth.AuthTokenStorage
import com.example.poster.model.AuthTokens
import com.example.poster.crash.CrashStore
import com.example.poster.model.CrashReport
import com.example.poster.util.PlatformDataStore
import com.example.poster.util.PlatformDataStoreImpl
import kotlinx.browser.localStorage
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single<PlatformDataStore> { PlatformDataStoreImpl() }
    single<AuthTokenStorage> { InMemoryTokenStorage() }
    single<CrashStore> { NoCrashStore }
}

/**
 * The session in memory, and nowhere else.
 *
 * It used to live in `localStorage`, which any script on the origin can read —
 * so a single injected script walked away with a thirty-day refresh token
 * rather than the fifteen minutes an access token is worth. Now the refresh
 * token is a cookie the server sets with `HttpOnly`, which script cannot
 * reach at all, and the access token is held here for the life of the page.
 *
 * A reload therefore starts with nothing, the first request comes back 401,
 * and the client refreshes against the cookie — which is the same number of
 * round trips as before and leaves nothing on disk to steal. Any older
 * session left in `localStorage` by a previous build is cleared on the way
 * past, because it is exactly the thing being removed.
 */
private class InMemoryTokenStorage : AuthTokenStorage {
    private var tokens: AuthTokens? = null

    init {
        localStorage.removeItem("poster.session")
    }

    override suspend fun load(): AuthTokens? = tokens
    override suspend fun save(tokens: AuthTokens) { this.tokens = tokens }
    override suspend fun clear() { tokens = null }
}

private object NoCrashStore : CrashStore {
    override fun save(report: CrashReport) = Unit
    override fun pending(): CrashReport? = null
    override fun clear() = Unit
}
