package com.example.poster

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.poster.auth.AppleCredentials
import com.example.poster.auth.AuthTokenStorage
import com.example.poster.auth.GoogleCredentials
import com.example.poster.auth.NoAppleCredentials
import com.example.poster.auth.NoGoogleCredentials
import com.example.poster.db.DatabaseDriverFactory
import com.example.poster.di.AppConfig
import com.example.poster.di.databaseModule
import com.example.poster.di.platformModule
import com.example.poster.di.sharedModule
import com.example.poster.di.viewModelModule
import com.example.poster.notification.DailyReminders
import com.example.poster.notification.NoDailyReminders
import com.example.poster.util.PlatformDataStore
import com.example.poster.util.appVersionLabel
import org.koin.core.context.startKoin
import org.koin.dsl.module
import java.io.File

/**
 * The desktop app (feature.desktop). Same Koin graph as the phones; the
 * server is `POSTER_SERVER_HOST` / `POSTER_SERVER_PORT` / `POSTER_SERVER_SCHEME`
 * or the local backend, and everything the app keeps lives under `~/.poster`.
 */
fun main() {
    val home = File(System.getProperty("user.home"), ".poster").apply { mkdirs() }
    if (System.getProperty("poster.database") == null) {
        System.setProperty("poster.database", File(home, "poster.db").path)
    }
    startKoin {
        modules(
            platformModule(),
            databaseModule(DatabaseDriverFactory()),
            sharedModule(
                AppConfig(
                    serverHost = setting("POSTER_SERVER_HOST", "localhost"),
                    serverPort = setting("POSTER_SERVER_PORT", "8080").toInt(),
                    serverScheme = setting("POSTER_SERVER_SCHEME", "http"),
                    appVersion = appVersionLabel(),
                ),
            ),
            module {
                // The JVM defaults are in-memory (they serve the server's tests); a
                // desktop app has to remember who is signed in across launches.
                single<PlatformDataStore> { FileDataStore(File(home, "preferences.properties")) }
                single<AuthTokenStorage> { FileAuthTokenStorage(File(home, "session.json")) }
                single<GoogleCredentials> { NoGoogleCredentials }
                single<AppleCredentials> { NoAppleCredentials }
                single<DailyReminders> { NoDailyReminders }
            },
            viewModelModule(),
        )
    }
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Poster",
            state = rememberWindowState(width = 480.dp, height = 900.dp),
        ) {
            App()
        }
    }
}

private fun setting(name: String, default: String): String =
    System.getenv(name) ?: System.getProperty(name.lowercase().replace('_', '.')) ?: default
