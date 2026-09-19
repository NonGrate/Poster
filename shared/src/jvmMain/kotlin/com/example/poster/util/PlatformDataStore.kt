package com.example.poster.util

import java.util.concurrent.ConcurrentHashMap

// The JVM implementation of PlatformDataStore temporarily using a ConcurrentHashMap for in-memory storage.
actual class PlatformDataStoreImpl : PlatformDataStore {
    private val map = ConcurrentHashMap<String, Any?>()

    actual override suspend fun putBoolean(key: String, value: Boolean) {
        map[key] = value
    }

    actual override suspend fun getBoolean(key: String, default: Boolean): Boolean {
        return map[key] as? Boolean ?: default
    }

    actual override suspend fun putString(key: String, value: String?) {
        map[key] = value
    }

    actual override suspend fun getString(key: String, default: String?): String? {
        return map[key] as? String? ?: default
    }

    actual override suspend fun clear() {
        map.clear()
    }
}
