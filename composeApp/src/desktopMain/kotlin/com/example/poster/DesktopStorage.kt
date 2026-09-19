package com.example.poster

import com.example.poster.auth.AuthTokenStorage
import com.example.poster.model.AuthTokens
import com.example.poster.util.PlatformDataStore
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Properties

/** Preferences in a properties file. Written through on every change; small and rare. */
class FileDataStore(private val file: File) : PlatformDataStore {
    private val properties = Properties().apply { if (file.exists()) file.reader().use { load(it) } }

    @Synchronized private fun flush() = file.writer().use { properties.store(it, null) }

    override suspend fun putBoolean(key: String, value: Boolean) { properties.setProperty(key, value.toString()); flush() }
    override suspend fun getBoolean(key: String, default: Boolean): Boolean = properties.getProperty(key)?.toBooleanStrictOrNull() ?: default
    override suspend fun putString(key: String, value: String?) { if (value == null) properties.remove(key) else properties.setProperty(key, value); flush() }
    override suspend fun getString(key: String, default: String?): String? = properties.getProperty(key) ?: default
    override suspend fun clear() { properties.clear(); flush() }
}

/** The session tokens as one JSON file, readable only by the owner where the file system allows it. */
class FileAuthTokenStorage(private val file: File) : AuthTokenStorage {
    override suspend fun load(): AuthTokens? =
        file.takeIf { it.exists() }?.let { runCatching { Json.decodeFromString(AuthTokens.serializer(), it.readText()) }.getOrNull() }

    override suspend fun save(tokens: AuthTokens) {
        file.writeText(Json.encodeToString(AuthTokens.serializer(), tokens))
        runCatching { file.setReadable(false, false); file.setReadable(true, true) }
    }

    override suspend fun clear() { file.delete() }
}
