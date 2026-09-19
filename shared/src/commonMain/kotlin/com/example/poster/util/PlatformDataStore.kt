package com.example.poster.util

/**
 * Platform DataStore abstraction for key-value storage.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
interface PlatformDataStore {
    suspend fun putBoolean(key: String, value: Boolean)
    suspend fun getBoolean(key: String, default: Boolean = false): Boolean

    suspend fun putString(key: String, value: String?)
    suspend fun getString(key: String, default: String? = null): String?

    suspend fun clear()
}

