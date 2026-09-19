package com.example.poster

import com.example.poster.config.Features
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.ComposeUIViewController
import com.example.poster.crash.CrashStore
import com.example.poster.crash.CrashUploader
import com.example.poster.crash.DeviceDescription
import com.example.poster.crash.installCrashHandler
import org.koin.mp.KoinPlatform
import platform.Foundation.NSBundle
import platform.UIKit.UIDevice
import com.example.poster.db.DatabaseDriverFactory
import com.example.poster.di.AppConfig
import com.example.poster.util.appVersionLabel
import com.example.poster.di.databaseModule
import com.example.poster.di.platformModule
import com.example.poster.notification.DailyReminders
import com.example.poster.notification.IosDailyReminders
import com.example.poster.di.sharedModule
import com.example.poster.di.viewModelModule
import com.example.poster.auth.GoogleCredentials
import com.example.poster.auth.GoogleSignInLauncher
import com.example.poster.auth.IosGoogleCredentials
import com.example.poster.auth.AppleCredentials
import com.example.poster.auth.AppleSignInLauncher
import com.example.poster.auth.IosAppleCredentials
import org.koin.dsl.module
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatformTools

fun MainViewController(
    serverHost: String,
    serverPort: Int,
    serverScheme: String,
    // Defaulted so the Swift side keeps compiling until somebody passes a key.
    // Blank means no in-app support on iOS, which is where it stands until
    // there is an Apple developer account to configure products under.
    revenueCatApiKey: String = "",
    // The native Google Sign-In, implemented in Swift (GoogleSignInBridge).
    // Null keeps the Google button hidden, the same way a blank RevenueCat key
    // hides support — Swift passes null when no client id is configured.
    googleSignIn: GoogleSignInLauncher? = null,
    // The native Sign in with Apple, implemented in Swift (AppleSignInBridge).
    // Null keeps the Apple button hidden; Swift passes a bridge once the
    // capability is wired.
    appleSignIn: AppleSignInLauncher? = null,
    // Screenshots only: show the support paywall with placeholder tiers when
    // there is no billing key yet. Never true in a shipped build.
    demoPaywall: Boolean = false,
) = ComposeUIViewController {
    initializeKoin(
        serverHost = serverHost,
        serverPort = serverPort,
        serverScheme = serverScheme,
        revenueCatApiKey = revenueCatApiKey,
        googleSignIn = googleSignIn,
        appleSignIn = appleSignIn,
        demoPaywall = demoPaywall,
    )
    // After Koin, because the handler needs somewhere to write.
    LaunchedEffect(Unit) {
        if (!Features.CRASH_REPORTS) return@LaunchedEffect
        val koin = KoinPlatform.getKoin()
        installCrashHandler(koin.get<CrashStore>()) {
            DeviceDescription(
                platform = "ios",
                osVersion = UIDevice.currentDevice.systemName + " " +
                    UIDevice.currentDevice.systemVersion,
                device = UIDevice.currentDevice.model,
                appVersion = (
                    NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString")
                        as? String
                    ) ?: "unknown",
            )
        }
        // Whatever the last run could not send, because it was busy crashing.
        koin.get<CrashUploader>().sendPending()
    }
    App()
}

/**
 * Starts Koin without building a view: the tab shell (IosTabs.kt) needs the
 * graph before it can ask whether somebody is signed in. Idempotent.
 */
fun prepareApp(
    serverHost: String,
    serverPort: Int,
    serverScheme: String,
    revenueCatApiKey: String = "",
    googleSignIn: GoogleSignInLauncher? = null,
    appleSignIn: AppleSignInLauncher? = null,
    demoPaywall: Boolean = false,
) = initializeKoin(serverHost, serverPort, serverScheme, revenueCatApiKey, googleSignIn, appleSignIn, demoPaywall)

private fun initializeKoin(
    serverHost: String,
    serverPort: Int,
    serverScheme: String,
    revenueCatApiKey: String,
    googleSignIn: GoogleSignInLauncher?,
    appleSignIn: AppleSignInLauncher?,
    demoPaywall: Boolean,
) {
    if (KoinPlatformTools.defaultContext().getOrNull() != null) return

    startKoin {
        modules(
            platformModule(),
            databaseModule(DatabaseDriverFactory()),
            sharedModule(
                AppConfig(
                    serverHost = serverHost,
                    serverPort = serverPort,
                    serverScheme = serverScheme,
                    revenueCatApiKey = revenueCatApiKey,
                    demoPaywall = demoPaywall,
                    appVersion = appVersionLabel(),
                )
            ),
            // Google sign-in through the native SDK (see IosGoogleCredentials and
            // GoogleSignInBridge.swift). With no launcher it reports unavailable
            // and no button shows.
            module {
                single<GoogleCredentials> { IosGoogleCredentials(googleSignIn) }
                single<AppleCredentials> { IosAppleCredentials(appleSignIn) }
                single<DailyReminders> { IosDailyReminders() }
            },
            viewModelModule(),
        )
    }
}
