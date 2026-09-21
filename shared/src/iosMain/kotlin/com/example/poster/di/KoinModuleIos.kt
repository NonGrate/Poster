package com.example.poster.di

import com.example.poster.util.PlatformDataStore
import com.example.poster.util.PlatformDataStoreImpl
import com.example.poster.auth.AuthTokenStorage
import com.example.poster.auth.IosAuthTokenStorage
import com.example.poster.crash.CrashStore
import com.example.poster.crash.FileCrashStore
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single<PlatformDataStore> { PlatformDataStoreImpl() }
    single<AuthTokenStorage> { IosAuthTokenStorage() }
    single<CrashStore> { FileCrashStore() }
}
