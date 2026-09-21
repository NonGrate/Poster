package com.example.poster.util

import platform.Foundation.NSUserDefaults

actual class PlatformDataStoreImpl : PlatformDataStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual override suspend fun putBoolean(key: String, value: Boolean) {
        defaults.setBool(value, forKey = key)
    }

    actual override suspend fun getBoolean(key: String, default: Boolean): Boolean {
        return if (defaults.objectForKey(key) != null) defaults.boolForKey(key) else default
    }

    actual override suspend fun putString(key: String, value: String?) {
        if (value == null) {
            defaults.removeObjectForKey(key)
        } else {
            defaults.setObject(value, forKey = key)
        }
    }

    actual override suspend fun getString(key: String, default: String?): String? {
        return defaults.stringForKey(key) ?: default
    }

    actual override suspend fun clear() {
        // Not recommended to clear all NSUserDefaults, so do nothing or selectively clear keys in production
    }
}

