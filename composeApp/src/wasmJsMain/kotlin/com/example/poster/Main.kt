package com.example.poster

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.example.poster.auth.AppleCredentials
import com.example.poster.auth.GoogleCredentials
import com.example.poster.auth.NoAppleCredentials
import com.example.poster.auth.NoGoogleCredentials
import com.example.poster.di.AppConfig
import com.example.poster.di.platformModule
import com.example.poster.di.sharedModule
import com.example.poster.di.viewModelModule
import com.example.poster.notification.DailyReminders
import com.example.poster.notification.NoDailyReminders
import com.example.poster.repository.PostRepository
import com.example.poster.util.appVersionLabel
import kotlinx.browser.document
import kotlinx.browser.window
import org.koin.core.context.startKoin
import org.koin.dsl.module

/**
 * The web app (feature.web). Same Koin graph as the phones, minus the
 * database: there is no SQLite in the browser, so `PostRepository` runs
 * without a `PostLocalStore` and the app is online only (docs/Web.md).
 *
 * The server is `<meta name="poster-server" content="https://host">` when the
 * page sets it, the local backend when served by the dev server on 8081, and
 * the page's own origin otherwise (the Ktor server can host the bundle).
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val server = serverOrigin()
    startKoin {
        modules(
            platformModule(),
            sharedModule(
                AppConfig(
                    serverHost = server.host,
                    serverPort = server.port,
                    serverScheme = server.scheme,
                    // Only when the API is this page's own origin: the cookie
                    // is SameSite=Strict and a browser would not send it to
                    // anywhere else, so asking for one there would sign the
                    // person out on every reload.
                    cookieSession = server.isSameOriginAsPage(),
                    appVersion = appVersionLabel(),
                ),
            ),
            module {
                single { PostRepository(postApi = get(), cache = get(), dispatchers = get(), localStore = null) }
                single<GoogleCredentials> { NoGoogleCredentials }
                single<AppleCredentials> { NoAppleCredentials }
                single<DailyReminders> { NoDailyReminders }
            },
            viewModelModule(),
        )
    }
    ComposeViewport(document.body!!) { App() }
}

private data class ServerOrigin(val scheme: String, val host: String, val port: Int) {
    /** Whether the API lives on the origin this page was served from. */
    fun isSameOriginAsPage(): Boolean {
        val location = window.location
        val pageScheme = location.protocol.removeSuffix(":")
        val pagePort = location.port.toIntOrNull() ?: if (pageScheme == "https") 443 else 80
        return scheme == pageScheme && host == location.hostname && port == pagePort
    }
}

private fun serverOrigin(): ServerOrigin {
    val meta = document.querySelector("meta[name=poster-server]")?.getAttribute("content")?.trim().orEmpty()
    val location = window.location
    val scheme = location.protocol.removeSuffix(":")
    val defaultPort = if (scheme == "https") 443 else 80
    if (meta.isNotEmpty()) {
        val withoutScheme = meta.substringAfter("://")
        val metaScheme = meta.substringBefore("://", scheme)
        val host = withoutScheme.substringBefore('/').substringBefore(':')
        val port = withoutScheme.substringBefore('/').substringAfter(':', "").toIntOrNull() ?: if (metaScheme == "https") 443 else 80
        return ServerOrigin(metaScheme, host, port)
    }
    // The Kotlin dev server: the backend is the one beside it.
    if (location.port == "8081") return ServerOrigin("http", location.hostname, 8080)
    return ServerOrigin(scheme, location.hostname, location.port.toIntOrNull() ?: defaultPort)
}
