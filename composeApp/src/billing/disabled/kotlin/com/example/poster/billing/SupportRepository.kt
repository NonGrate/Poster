package com.example.poster.billing

import com.example.poster.util.DispatcherProvider

/**
 * The stand-in compiled when `feature.support=false` in poster.properties.
 *
 * Same constructor as the real one in src/billing/enabled, so the Koin module
 * does not change; it simply never reports itself enabled, and the Settings
 * screen hides the section. The RevenueCat SDK is not on the classpath at all.
 */
class SupportRepository(
    @Suppress("UNUSED_PARAMETER") apiKey: String,
    @Suppress("UNUSED_PARAMETER") dispatchers: DispatcherProvider,
) {
    val enabled: Boolean get() = false
}
