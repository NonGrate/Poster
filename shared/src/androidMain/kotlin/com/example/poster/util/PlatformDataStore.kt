package com.example.poster.util

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "app_preferences")

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class PlatformDataStoreImpl(private val context: Context) : PlatformDataStore {
    private val ds get() = context.dataStore

    actual override suspend fun putBoolean(key: String, value: Boolean) {
        val prefKey = booleanPreferencesKey(key)
        ds.edit { it[prefKey] = value }
    }

    actual override suspend fun getBoolean(key: String, default: Boolean): Boolean {
        val prefKey = booleanPreferencesKey(key)
        return ds.data.first()[prefKey] ?: default
    }

    actual override suspend fun putString(key: String, value: String?) {
        val prefKey = stringPreferencesKey(key)
        ds.edit { if (value == null) it.remove(prefKey) else it[prefKey] = value }
    }

    actual override suspend fun getString(key: String, default: String?): String? {
        val prefKey = stringPreferencesKey(key)
        return ds.data.first()[prefKey] ?: default
    }

    actual override suspend fun clear() {
        ds.edit { it.clear() }
    }
}

