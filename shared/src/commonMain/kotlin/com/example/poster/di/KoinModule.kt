package com.example.poster.di

import com.example.poster.ktor.KtorFollowApi
import com.example.poster.network.FollowApi
import com.example.poster.repository.NotificationRepository
import com.example.poster.ktor.KtorNotificationApi
import com.example.poster.network.NotificationApi
import com.example.poster.repository.CommentRepository
import com.example.poster.ktor.KtorCommentApi
import com.example.poster.network.CommentApi
import com.example.poster.cache.PostCache
import com.example.poster.db.DatabaseDriverFactory
import com.example.poster.db.DatabaseManager
import com.example.poster.ktor.KtorGroupApi
import com.example.poster.ktor.KtorEventApi
import com.example.poster.ktor.KtorFeedbackApi
import com.example.poster.ktor.KtorPostApi
import com.example.poster.ktor.KtorConfigApi
import com.example.poster.ktor.KtorTagsApi
import com.example.poster.ktor.KtorUserApi
import com.example.poster.ktor.createHttpClient
import com.example.poster.network.GroupApi
import com.example.poster.network.EventApi
import com.example.poster.network.FeedbackApi
import com.example.poster.network.PostApi
import com.example.poster.crash.CrashUploader
import com.example.poster.network.ConfigApi
import com.example.poster.network.TagApi
import com.example.poster.network.UserApi
import com.example.poster.repository.FeedbackRepository
import com.example.poster.repository.PostRepository
import com.example.poster.telemetry.EventReporter
import com.example.poster.repository.TagRepository
import com.example.poster.repository.PostLocalStore
import com.example.poster.repository.SessionRepository
import com.example.poster.util.AppPreferences
import com.example.poster.util.DispatcherProvider
import com.example.poster.auth.AuthTokenStorage
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.Module
import org.koin.dsl.module

// Configuration class to hold build variant specific settings
data class AppConfig(
    val serverHost: String,
    val serverPort: Int,
    val serverScheme: String = "http",
    /**
     * The RevenueCat public SDK key, or blank for a build with no billing.
     *
     * Blank is an ordinary state, not a misconfiguration: the test flavor ships
     * without one on purpose, and support is simply unavailable there.
     */
    val revenueCatApiKey: String = "",
    /**
     * The Google Web OAuth client id, or blank for a build without Google
     * sign-in. Blank hides the button rather than offering one that cannot work.
     */
    val googleClientId: String = "",
    /**
     * The Apple Services ID, the OAuth client id for the Android browser flow.
     * Blank hides the Apple button on Android. iOS signs in natively and needs
     * nothing here — its token audience is the bundle id.
     */
    val appleServiceId: String = "",
    /**
     * Show the support paywall with placeholder tiers even without a billing key,
     * for screenshots before the store account exists. Never set in a shipped
     * build: taps buy nothing, the tiers are illustrative, and the switch is only
     * turned on by the screenshot capture.
     */
    val demoPaywall: Boolean = false,
    /**
     * The build label ("1.0 (321)"), attached to diagnostic events so a
     * misbehaviour can be tied to a build. Blank when a platform did not supply
     * one; harmless, just an empty column on those events.
     */
    val appVersion: String = "",
    /** Firebase project values for FCM on Android (`posterFirebase*` Gradle properties); blank = no push. */
    val firebaseProjectId: String = "",
    val firebaseAppId: String = "",
    val firebaseApiKey: String = "",
    val firebaseSenderId: String = "",
)

// Platform-specific module
expect fun platformModule(): Module

// Database module
fun databaseModule(databaseDriverFactory: DatabaseDriverFactory) = module {
    single { databaseDriverFactory }
    single { DatabaseManager(get()) }
}

// Shared module with common dependencies
fun sharedModule(appConfig: AppConfig) = module {
    // Make AppConfig available for injection
    single { appConfig }

    // API clients - pass server configuration
    single {
        createHttpClient(
            serverHost = appConfig.serverHost,
            serverPort = appConfig.serverPort,
            serverScheme = appConfig.serverScheme,
            authTokenStorage = get(),
        )
    }

    // Cache
    single { PostCache() }

    // Session: who is signed in, shared by the account and favorites screens
    single { SessionRepository(userApi = get(), appPreferences = get(), dispatchers = get()) }

    // Preferences
    // PlatformDataStore is provided in platform-specific modules
    single { AppPreferences(get()) }

    // Coroutine dispatchers shared across repositories and view models
    single {
        DispatcherProvider(
            main = Dispatchers.Main,
            io = Dispatchers.Default
        )
    }

    // APIs
    // Original API (non-cached)
    single<PostApi> { KtorPostApi(get()) }
    single<UserApi> {
        KtorUserApi(get(), get())
    }

    single<TagApi> { KtorTagsApi(get()) }

    single<ConfigApi> { KtorConfigApi(get()) }

    single { CrashUploader(store = get(), httpClient = get()) }

    single<GroupApi> {
        KtorGroupApi(get())
    }

    single<FeedbackApi> { KtorFeedbackApi(get()) }
    single<CommentApi> { KtorCommentApi(get()) }
    single<FollowApi> { KtorFollowApi(get()) }
    single { CommentRepository(api = get(), dispatchers = get()) }
    single<NotificationApi> { KtorNotificationApi(get()) }
    single { NotificationRepository(api = get(), preferences = get(), dispatchers = get()) }

    single<EventApi> { KtorEventApi(get()) }
    single { EventReporter(api = get(), preferences = get(), appVersion = appConfig.appVersion, dispatchers = get()) }

    // Repositories (singletons)
    single { PostLocalStore(databaseManager = get(), dispatchers = get()) }

    single { FeedbackRepository(feedbackApi = get(), dispatchers = get()) }

    single {
        PostRepository(
            postApi = get(),
            userApi = get(),
            cache = get(),
            dispatchers = get(),
            localStore = get(),
        )
    }

    single {
        TagRepository(
            tagApi = get()
        )
    }
}
