package com.example.poster.util

import kotlinx.browser.localStorage

/** Preferences in `localStorage`, one key each, under a prefix so they never collide with another app on the origin. */
actual class PlatformDataStoreImpl : PlatformDataStore {
    private fun key(name: String) = "poster.$name"

    actual override suspend fun putBoolean(key: String, value: Boolean) = localStorage.setItem(key(key), value.toString())
    actual override suspend fun getBoolean(key: String, default: Boolean): Boolean =
        localStorage.getItem(key(key))?.toBooleanStrictOrNull() ?: default
    actual override suspend fun putString(key: String, value: String?) {
        if (value == null) localStorage.removeItem(key(key)) else localStorage.setItem(key(key), value)
    }
    actual override suspend fun getString(key: String, default: String?): String? = localStorage.getItem(key(key)) ?: default
    actual override suspend fun clear() {
        val mine = (0 until localStorage.length).mapNotNull { localStorage.key(it) }.filter { it.startsWith("poster.") }
        mine.forEach(localStorage::removeItem)
    }
}
