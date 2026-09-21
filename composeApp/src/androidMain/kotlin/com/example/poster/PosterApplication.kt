package com.example.poster

import com.example.poster.notification.initializeFirebaseIfConfigured
import com.example.poster.config.Features
import android.app.Application
import android.os.Build
import com.example.poster.crash.CrashStore
import com.example.poster.crash.CrashUploader
import com.example.poster.crash.DeviceDescription
import com.example.poster.crash.installCrashHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform
import com.example.poster.di.viewModelModule
import com.example.poster.db.DatabaseDriverFactory
import com.example.poster.di.createAppConfig
import com.example.poster.di.databaseModule
import com.example.poster.di.platformModule
import com.example.poster.di.sharedModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import com.example.poster.auth.AndroidGoogleCredentials
import com.example.poster.auth.GoogleCredentials
import com.example.poster.auth.AndroidAppleCredentials
import com.example.poster.auth.AppleCredentials
import com.example.poster.network.UserApi
import com.example.poster.notification.AndroidDailyReminders
import com.example.poster.notification.DailyReminders
import com.example.poster.util.CurrentActivityHolder
import org.koin.dsl.module
import org.koin.core.context.startKoin

class PosterApplication : Application() {

    // Tracks the foreground Activity for anything that needs one — Google
    // sign-in launches its sheet from it.
    private val currentActivity = CurrentActivityHolder()

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(currentActivity)

        // Create AppConfig using functional approach
        val appConfig = createAppConfig(this)
        initializeFirebaseIfConfigured(this, appConfig)

        // Create database driver factory
        val databaseDriverFactory = DatabaseDriverFactory(this)

        startKoin {
            androidLogger()
            androidContext(this@PosterApplication)
            modules(
                platformModule(),
                databaseModule(databaseDriverFactory),
                sharedModule(appConfig),
                // Bound here rather than in the shared platform module: this
                // one needs a Context and lives in the app module, and Koin is
                // started per platform anyway.
                module {
                    single<GoogleCredentials> {
                        AndroidGoogleCredentials(
                            activityProvider = { currentActivity.activity },
                            serverClientId = appConfig.googleClientId,
                        )
                    }
                    single<AppleCredentials> {
                        AndroidAppleCredentials(
                            activityProvider = { currentActivity.activity },
                            serviceId = appConfig.appleServiceId,
                            // Must match the return URL on the Apple Services ID.
                            // The port is dropped for the standard 443/80 so it
                            // reads as the plain https URL Apple was given.
                            redirectUri = buildString {
                                append(appConfig.serverScheme).append("://").append(appConfig.serverHost)
                                if (appConfig.serverPort != 443 && appConfig.serverPort != 80) {
                                    append(":").append(appConfig.serverPort)
                                }
                                append("/auth/apple/callback")
                            },
                            // Over HTTPS to this server, carrying the verifier
                            // the callback's code is worthless without.
                            exchange = { code, verifier ->
                                get<UserApi>().exchangeAppleCode(code, verifier)
                            },
                        )
                    }
                    single<DailyReminders> {
                        AndroidDailyReminders(this@PosterApplication)
                    }
                },
                viewModelModule()
            )
        }

        // After Koin, because the handler needs somewhere to write; before
        // anything else, so a crash while the app is starting is still caught.
        if (!Features.CRASH_REPORTS) return
        val store: CrashStore = KoinPlatform.getKoin().get()
        installCrashHandler(store) {
            DeviceDescription(
                platform = "android",
                osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                device = "${Build.MANUFACTURER} ${Build.MODEL}",
                appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE})",
            )
        }
        // Whatever the last run could not send, because it was busy crashing.
        val uploader: CrashUploader = KoinPlatform.getKoin().get()
        CoroutineScope(Dispatchers.IO).launch { uploader.sendPending() }
    }
}
