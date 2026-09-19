package com.example.poster.auth

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import com.example.poster.model.AuthTokens
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.CoreFoundation.CFAutorelease
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosAuthTokenStorage : AuthTokenStorage {
    override suspend fun load(): AuthTokens? = read()?.let { encoded ->
        runCatching { Json.decodeFromString<AuthTokens>(encoded) }
            .getOrElse {
                clear()
                null
            }
    }

    override suspend fun save(tokens: AuthTokens) {
        val data = NSString.create(string = Json.encodeToString(tokens))
            .dataUsingEncoding(NSUTF8StringEncoding)!!
        clear()
        val dataRef = CFBridgingRetain(data)
        try {
            val status = withQuery(
                kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                kSecValueData to dataRef,
            ) { SecItemAdd(it, null) }
            check(status == errSecSuccess) { "Unable to save authentication tokens in Keychain ($status)" }
        } finally {
            CFBridgingRelease(dataRef)
        }
    }

    override suspend fun clear() {
        val status = withQuery { SecItemDelete(it) }
        check(status == errSecSuccess || status == errSecItemNotFound) {
            "Unable to remove authentication tokens from Keychain ($status)"
        }
    }

    private fun read(): String? = withQuery(
        kSecReturnData to kCFBooleanTrue,
        kSecMatchLimit to kSecMatchLimitOne,
    ) { query ->
        memScoped {
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query, result.ptr)
            if (status == errSecItemNotFound) return@memScoped null
            check(status == errSecSuccess) { "Unable to read authentication tokens from Keychain ($status)" }
            val data = CFBridgingRelease(result.value) as NSData
            NSString.create(data, NSUTF8StringEncoding) as String?
        }
    }

    private fun <T> withQuery(
        vararg additionalValues: Pair<CFStringRef?, CFTypeRef?>,
        block: (CFDictionaryRef?) -> T,
    ): T {
        val service = CFBridgingRetain(SERVICE)
        val account = CFBridgingRetain(ACCOUNT)
        val values = mapOf(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to service,
            kSecAttrAccount to account,
            *additionalValues,
        )
        val query = CFDictionaryCreateMutable(
            null,
            values.size.convert(),
            null,
            null,
        ).apply {
            values.forEach { (key, value) -> CFDictionaryAddValue(this, key, value) }
            CFAutorelease(this)
        }
        return try {
            block(query)
        } finally {
            CFBridgingRelease(service)
            CFBridgingRelease(account)
        }
    }

    private companion object {
        const val SERVICE = "com.example.poster.auth"
        const val ACCOUNT = "current-session"
    }
}
