package com.example.poster.di

import com.example.poster.util.PlatformDataStore
import com.example.poster.util.PlatformDataStoreImpl
import com.example.poster.auth.AuthTokenStorage
import com.example.poster.auth.InMemoryAuthTokenStorage
import com.example.poster.crash.CrashStore
import com.example.poster.model.CrashReport
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single<PlatformDataStore> { PlatformDataStoreImpl() }
    single<AuthTokenStorage> { InMemoryAuthTokenStorage() }
    // Nothing to store: the JVM installs no crash handler (CrashReporter.jvm.kt),
    // so nothing ever writes one. Bound anyway because CrashUploader is in the
    // shared graph and would otherwise fail to resolve on desktop.
    single<CrashStore> {
        object : CrashStore {
            override fun save(report: CrashReport) = Unit
            override fun pending(): CrashReport? = null
            override fun clear() = Unit
        }
    }
}
