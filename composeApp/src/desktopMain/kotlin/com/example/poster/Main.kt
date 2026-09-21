package com.example.poster

import org.jetbrains.skia.EncodedImageFormat
import androidx.compose.ui.unit.Density
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.ExperimentalComposeUiApi
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
    // POSTER_HOME moves everything the app keeps (tests, screenshots, a second profile).
    val home = File(System.getenv("POSTER_HOME") ?: (System.getProperty("user.home") + "/.poster")).apply { mkdirs() }
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
    val window = System.getenv("POSTER_WINDOW")?.split("x")?.takeIf { it.size == 2 }
        ?.let { (w, h) -> (w.toIntOrNull() ?: 480) to (h.toIntOrNull() ?: 900) } ?: (480 to 900)
    // Headless render for screenshots and CI: the same App(), drawn into a PNG
    // instead of a window, after the feed has had time to load.
    System.getenv("POSTER_RENDER_TO")?.let { path ->
        renderToPng(File(path), window.first, window.second, System.getenv("POSTER_RENDER_WAIT_MS")?.toLongOrNull() ?: 10_000L)
        return
    }
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Poster",
            // POSTER_WINDOW=1200x800 for a wide window (the two-pane layout starts at 840).
            state = rememberWindowState(width = window.first.dp, height = window.second.dp),
        ) {
            App()
        }
    }
}

private fun setting(name: String, default: String): String =
    System.getenv(name) ?: System.getProperty(name.lowercase().replace('_', '.')) ?: default

@OptIn(ExperimentalComposeUiApi::class)
private fun renderToPng(file: File, width: Int, height: Int, waitMs: Long) {
    val scene = ImageComposeScene(width = width * 2, height = height * 2, density = Density(2f)) { App() }
    val start = System.nanoTime()
    // Frames while the view models fetch: each render applies what arrived.
    while ((System.nanoTime() - start) / 1_000_000 < waitMs) {
        scene.render(System.nanoTime() - start)
        Thread.sleep(50)
    }
    val image = scene.render(System.nanoTime() - start)
    file.parentFile?.mkdirs()
    file.writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
    scene.close()
    println("rendered ${file.path}")
}
