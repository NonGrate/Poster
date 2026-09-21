package com.example.poster.preview

import com.example.poster.util.PlatformDataStore

class FakePlatformDataStore : PlatformDataStore {
    private val bools = mutableMapOf<String, Boolean>()
    private val strings = mutableMapOf<String, String?>()

    override suspend fun putBoolean(key: String, value: Boolean) {
        bools[key] = value
    }

    override suspend fun getBoolean(key: String, default: Boolean): Boolean {
        return bools[key] ?: default
    }

    override suspend fun putString(key: String, value: String?) {
        strings[key] = value
    }

    override suspend fun getString(key: String, default: String?): String? {
        return strings[key] ?: default
    }

    override suspend fun clear() {
        bools.clear()
        strings.clear()
    }
}