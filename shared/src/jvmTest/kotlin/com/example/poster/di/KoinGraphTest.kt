package com.example.poster.di

import com.example.poster.network.GroupApi
import com.example.poster.network.PostApi
import com.example.poster.network.TagApi
import com.example.poster.network.UserApi
import com.example.poster.db.DatabaseDriverFactory
import com.example.poster.repository.PostLocalStore
import com.example.poster.repository.PostRepository
import com.example.poster.repository.SessionRepository
import com.example.poster.repository.TagRepository
import com.example.poster.util.AppPreferences
import com.example.poster.util.DispatcherProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.core.context.stopKoin
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * A missing Koin binding is invisible until something asks for it, which today
 * means a crash on someone's phone rather than a failure here. Resolving the
 * graph in a test moves that to build time.
 *
 * Written against Koin directly rather than pulling in koin-test: it is a dozen
 * lines and one fewer dependency.
 */
class KoinGraphTest {

    @AfterTest
    fun tearDown() {
        stopKoin()
        System.clearProperty("poster.database")
    }

    @Test
    fun everySharedDependencyResolves() {
        // The feed is served from the database now, so the graph needs one.
        // Its own file, deleted afterwards: resolving a graph must not touch
        // whatever database the developer happens to have lying around.
        val databaseFile = Files.createTempDirectory("poster-koin").resolve("test.db")
        System.setProperty("poster.database", databaseFile.toString())
        // Two bindings cannot be built off-device and are replaced here; every
        // other binding, and every dependency between them, is the real one.
        //  - the Ktor client picks its engine from the classpath, and a plain
        //    JVM test has none;
        //  - DispatcherProvider binds Dispatchers.Main, which exists only where
        //    there is a main looper. That is every platform the app ships on,
        //    and not this test.
        val offDevice = module {
            single { HttpClient(MockEngine { respond("", HttpStatusCode.OK) }) }
            single { DispatcherProvider(main = Dispatchers.Unconfined, io = Dispatchers.Unconfined) }
        }

        val koin = startKoin {
            modules(
                platformModule(),
                databaseModule(DatabaseDriverFactory()),
                sharedModule(
                    AppConfig(serverHost = "127.0.0.1", serverPort = 8080, serverScheme = "http"),
                ),
                offDevice,
            )
        }.koin

        // Everything a screen or repository asks for, by the type it asks for.
        assertNotNull(koin.get<AppConfig>())
        assertNotNull(koin.get<AppPreferences>())
        assertNotNull(koin.get<DispatcherProvider>())
        assertNotNull(koin.get<PostApi>())
        assertNotNull(koin.get<UserApi>())
        assertNotNull(koin.get<TagApi>())
        assertNotNull(koin.get<GroupApi>())
        assertNotNull(koin.get<PostRepository>())
        assertNotNull(koin.get<TagRepository>())
        assertNotNull(koin.get<SessionRepository>())
        assertNotNull(koin.get<PostLocalStore>())

        databaseFile.toFile().parentFile.deleteRecursively()
    }
}
