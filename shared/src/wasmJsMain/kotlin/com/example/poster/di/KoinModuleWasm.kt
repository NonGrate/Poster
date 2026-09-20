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
    single<AuthTokenStorage> { LocalStorageTokenStorage() }
    single<CrashStore> { NoCrashStore }
}

/**
 * The session in `localStorage`. Readable by any script on the origin, which is
 * the browser's model: serve the app from its own origin and it is as safe as
 * a cookie without the CSRF surface.
 */
private class LocalStorageTokenStorage : AuthTokenStorage {
    private val key = "poster.session"
    override suspend fun load(): AuthTokens? =
        localStorage.getItem(key)?.let { runCatching { Json.decodeFromString(AuthTokens.serializer(), it) }.getOrNull() }
    override suspend fun save(tokens: AuthTokens) = localStorage.setItem(key, Json.encodeToString(AuthTokens.serializer(), tokens))
    override suspend fun clear() = localStorage.removeItem(key)
}

private object NoCrashStore : CrashStore {
    override fun save(report: CrashReport) = Unit
    override fun pending(): CrashReport? = null
    override fun clear() = Unit
}
