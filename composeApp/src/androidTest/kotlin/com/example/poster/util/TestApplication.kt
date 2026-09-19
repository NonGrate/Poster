package com.example.poster.util

import android.app.Application
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import com.example.poster.di.AppConfig
import com.example.poster.di.databaseModule
import com.example.poster.di.platformModule
import com.example.poster.di.sharedModule
import com.example.poster.di.viewModelModule
import com.example.poster.db.DatabaseDriverFactory
import com.example.poster.ktor.createHttpClient
import com.example.poster.auth.GoogleCredentials
import com.example.poster.auth.NoGoogleCredentials
import com.example.poster.auth.AppleCredentials
import com.example.poster.auth.NoAppleCredentials
import com.example.poster.notification.AndroidDailyReminders
import com.example.poster.notification.DailyReminders
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.dsl.module

/**
 * Test Application for Android Instrumented Tests
 * Configures Koin with test-specific dependencies and clean database setup
 */
class TestApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@TestApplication)

            modules(
                // Platform-specific module (provides PlatformDataStore, etc.)
                platformModule(),

                // Database module with fresh driver factory
                databaseModule(DatabaseDriverFactory(this@TestApplication)),

                // Shared module configured to point to local server
                sharedModule(
                    AppConfig(
                        serverHost = TestServer.host,
                        serverPort = TestServer.port,
                    )
                ),

                // Platform bindings that production wires up in PosterApplication
                // because they need a Context. Sign-in providers are no-ops in
                // tests: the suite has no browser and no Play Services account.
                module {
                    single<GoogleCredentials> { NoGoogleCredentials }
                    single<AppleCredentials> { NoAppleCredentials }
                    single<DailyReminders> { AndroidDailyReminders(this@TestApplication) }
                },

                // The real ViewModel graph, so tests exercise production wiring.
                viewModelModule()
            )
        }

    }
}

/**
 * Puts the backend into a known state before a class runs.
 *
 * A bad *response* already said so plainly. A backend that is not listening at
 * all did not: the connection failure escaped this function, out through the
 * runner, and the instrumentation reported "Process crashed before executing
 * the test(s)" with a stack trace of coroutine internals. That message is
 * indistinguishable from the run-voiding crash under load recorded in BUGS.md,
 * which is how an afternoon goes into looking for a race that was a stopped
 * server. Both cases now say the same short thing.
 */
internal fun applyServerFixtures() {
    kotlinx.coroutines.runBlocking {
        val response = try {
            createHttpClient(TestServer.host, TestServer.port).use { httpClient ->
                httpClient.post("debug/fixtures/integration")
            }
        } catch (cause: Exception) {
            error(
                "The local backend is not answering on ${TestServer.host}:${TestServer.port} " +
                    "(${cause::class.simpleName}: ${cause.message}). " +
                    "Start it with ./scripts/run-local-backend.sh. " +
                    "This is not a test failure and says nothing about the code.",
            )
        }
        check(response.status == HttpStatusCode.NoContent) {
            "Local server fixture endpoint returned ${response.status}. " +
                "Start it with ./scripts/run-local-backend.sh."
        }
    }
}
