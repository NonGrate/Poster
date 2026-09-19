package com.example.poster.util

/**
 * Platform DataStore abstraction for key-value storage.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class PlatformDataStoreImpl: PlatformDataStore {
    override suspend fun putBoolean(key: String, value: Boolean)
    override suspend fun getBoolean(key: String, default: Boolean): Boolean

    override suspend fun putString(key: String, value: String?)
    override suspend fun getString(key: String, default: String?): String?

    override suspend fun clear()
}

