package com.example.poster.di

import android.content.Context
import com.example.poster.BuildConfig
import com.example.poster.util.appVersionLabel

/**
 * Creates an AppConfig instance based on the application context.
 *
 * Uses BuildConfig fields directly to configure the app:
 * - BuildConfig.SERVER_SCHEME for HTTP or HTTPS
 * - BuildConfig.SERVER_HOST for server host
 * - BuildConfig.SERVER_PORT for server port
 * - BuildConfig.REVENUECAT_API_KEY for in-app support, blank when there is none
 *
 * @param context The application context (not used but kept for API compatibility)
 * @return AppConfig instance configured based on the current build variant
 */
fun createAppConfig(context: Context): AppConfig {
    return AppConfig(
        serverScheme = BuildConfig.SERVER_SCHEME,
        serverHost = BuildConfig.SERVER_HOST,
        serverPort = BuildConfig.SERVER_PORT,
        revenueCatApiKey = BuildConfig.REVENUECAT_API_KEY,
        googleClientId = BuildConfig.GOOGLE_CLIENT_ID,
        appleServiceId = BuildConfig.APPLE_SERVICE_ID,
        demoPaywall = BuildConfig.DEMO_PAYWALL,
        appVersion = appVersionLabel(),
        firebaseProjectId = BuildConfig.FIREBASE_PROJECT_ID,
        firebaseAppId = BuildConfig.FIREBASE_APP_ID,
        firebaseApiKey = BuildConfig.FIREBASE_API_KEY,
        firebaseSenderId = BuildConfig.FIREBASE_SENDER_ID,
    )
}
