package com.example.poster

import io.ktor.server.plugins.compression.*
import io.ktor.server.http.content.staticFiles
import io.ktor.http.HttpMethod
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.server.plugins.cors.routing.CORS
import com.example.poster.config.AppInfo
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.auth.authenticate
import com.example.poster.auth.Argon2PasswordHasher
import com.example.poster.auth.AttemptThrottle
import com.example.poster.auth.AuthConfig
import com.example.poster.auth.AuthService
import com.example.poster.auth.AppleVerifier
import com.example.poster.auth.GoogleVerifier
import com.example.poster.auth.SocialVerifier
import com.example.poster.auth.TokenService
import com.example.poster.auth.authRoutes
import com.example.poster.auth.accountPages
import com.example.poster.auth.groupPages
import com.example.poster.mail.GroupMail
import com.example.poster.notify.TelegramNotifier
import kotlinx.coroutines.launch
import com.example.poster.auth.AccountMail
import com.example.poster.auth.AccountTokens
import com.example.poster.mail.Mailer
import com.example.poster.mail.ResendMailer
import com.example.poster.admin.AdminConfig
import com.example.poster.admin.adminRoutes
import com.example.poster.admin.bootstrapAdmin
import com.example.poster.admin.configureAdminSessions
import com.example.poster.auth.configureBearerAuthentication
import com.example.poster.model.*
import com.example.poster.config.Features
import com.example.poster.uploads.UploadStore
import com.example.poster.comments.CommentsRepository
import com.example.poster.push.NotificationsRepository
import com.example.poster.push.Notifier
import com.example.poster.push.PushSender
import com.example.poster.push.PushSenders
import com.example.poster.push.deviceWithdrawalRoute
import com.example.poster.push.notificationRoutes
import com.example.poster.accounts.accountRoutes
import com.example.poster.diagnostics.diagnosticRoutes
import com.example.poster.favorites.favoriteRoutes
import com.example.poster.feedback.feedbackRoutes
import com.example.poster.groups.groupRoutes
import com.example.poster.posts.postRoutes
import com.example.poster.social.socialRoutes
import com.example.poster.tags.tagRoutes
import com.example.poster.uploads.uploadRoutes
import com.example.poster.db.DatabaseDriverFactory
import kotlinx.serialization.json.Json


fun main() {
    // The cascades in the schema only fire where foreign keys are on, and this
    // is the only process that wants them: the app stores posts from the feed
    // without their authors and could not write its own cache with this set.
    // Done here rather than in module() — a system property set as a side
    // effect of starting a module leaks into every test in the same JVM, and
    // then enforcement depends on which test ran first.
    if (System.getenv("POSTER_ENFORCE_FOREIGN_KEYS") == null) {
        System.setProperty("poster.enforceForeignKeys", "true")
    }
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

/**
 * [mailer] is a parameter so a test can watch what would have been sent. It
 * defaults to the real one, which prints when no provider is configured.
 */
fun Application.module(
    mailer: Mailer = ResendMailer.fromEnvironment(log = { println(it) }),
    /**
     * Null means "build them from the environment", which is what production
     * does. A test passes its own so it can mint tokens with a key it holds,
     * rather than needing Google to sign something.
     */
    verifiers: List<SocialVerifier>? = null,
    /** Push senders by platform. Null reads keys from the environment; a test passes recorders. */
    pushSenders: Map<String, PushSender>? = null,
    /**
     * The client id the deletion page's Google button is drawn with. Null reads
     * it from the environment, which is what production does; a test passes the
     * audience its own verifier expects, so the button and the check agree.
     */
    googleClientIdOverride: String? = null,
) {
    install(ContentNegotiation) {
        json(Json {
            encodeDefaults = true
            isLenient = true
            coerceInputValues = true
            ignoreUnknownKeys = true
        })
    }

    // The web app (feature.web) calls the API from a browser. Served by this
    // server it shares the origin and needs nothing; served from elsewhere (the
    // Kotlin dev server on 8081, a CDN) its origin must be listed here. Tokens
    // travel in the Authorization header, never in cookies, so allowing an
    // origin does not hand it a session.
    val webOrigins = (System.getenv("POSTER_WEB_ORIGINS")
        ?: if (System.getProperty("io.ktor.development").toBoolean()) "http://localhost:8081" else "")
        .split(",").map { it.trim() }.filter { it.isNotEmpty() }
    if (webOrigins.isNotEmpty()) install(CORS) {
        webOrigins.forEach { origin ->
            val url = Url(origin)
            val port = url.specifiedPort.takeIf { it != 0 && it != url.protocol.defaultPort }?.let { ":$it" } ?: ""
            allowHost(url.host + port, schemes = listOf(url.protocol.name))
        }
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
    }

    // So every request shows up: method, path, status. Without it a failed
    // sign-in is invisible and there is nothing to tell "the request never
    // arrived" from "the server refused it" — the whole point when debugging.
    // The web bundle is 17 MB of wasm and JS; browsers accept gzip, and the
    // JSON the API returns compresses just as well. Skipped for anything already
    // compressed (images) by the plugin's own content-type check.
    install(Compression) {
        gzip { priority = 1.0 }
        deflate { priority = 0.9 }
    }

    install(CallLogging) {
        format { call ->
            "${call.request.httpMethod.value} ${call.request.path()} -> ${call.response.status()}"
        }
    }

    // One database for the whole server: one connection, one migration run, and
    // no two repositories queueing behind each other for the write lock. Every
    // repository below is handed it rather than opening its own.
    val driver = DatabaseDriverFactory().createDriver()
    val database = PostDatabase(driver)
    val tagRepository = TagLocalRepository(database)
    // The starting set, once. Anything already there is left alone, so labels
    // corrected in the panel are not overwritten by a deploy — and a new tag
    // does not need a deploy at all.
    tagRepository.seedCuratedTags()
    val postsRepository = PostsLocalRepository(database, tagRepository)
    // Null when images are off: no directory is created and every image id
    // arriving in a post is dropped.
    val uploadStore = if (Features.IMAGES) UploadStore.fromEnvironment() else null
    if (uploadStore != null) {
        // Once per start. An orphan is a form somebody abandoned a day ago;
        // it does not need a scheduler to go away. ponytail: restart-driven,
        // add a timer if a deployment runs for months without one.
        // Avatars count as in use too; the account repository is built a few lines below, so read directly.
        val inUse = postsRepository.imagesInUse().toSet() + AccountLocalRepository(database).allUsers().mapNotNull { it.photo }
        uploadStore.sweepOrphans(UploadStore.PENDING_GRACE) { it in inUse }
    }
    val accountRepository = AccountLocalRepository(database)
    val favoritesRepository = FavoritesLocalRepository(database, tagRepository)
    val followsRepository = FollowsLocalRepository(database)
    val bookmarksRepository = BookmarksLocalRepository(database)
    val groupRepository = GroupLocalRepository(database)
    val userGroupRepository = UserGroupLocalRepository(database)
    val developmentMode = System.getProperty("io.ktor.development").toBoolean()
    val fixturesEnabled = System.getenv("POSTER_FIXTURES_ENABLED")?.toBooleanStrictOrNull()
        ?: developmentMode
    val authConfig = AuthConfig(
        secret = System.getenv("POSTER_JWT_SECRET")
            ?: if (developmentMode) {
                "development-only-poster-jwt-secret"
            } else {
                error("POSTER_JWT_SECRET is required outside development mode")
            },
    )
    val passwordHasher = Argon2PasswordHasher()
    val accountTokens = AccountTokens(driver)
    // Once per start, like the upload sweep above: nothing reads an expired
    // token, but until now nothing removed them either.
    accountTokens.forgetExpired()
    val accountMail = AccountMail(mailer = mailer, tokens = accountTokens)
    val groupMail = GroupMail(mailer = mailer)
    // Google sign-in, when this deployment was given a client id. Absent is an
    // ordinary state: the route then answers "not built with that" and the app
    // hides the button, the same way billing does without a key.
    val googleClientId = googleClientIdOverride ?: System.getenv("POSTER_GOOGLE_CLIENT_ID").orEmpty()
    val socialVerifiers = verifiers ?: buildList {
        if (Features.GOOGLE_SIGN_IN && googleClientId.isNotBlank()) {
            log.info("google sign-in: enabled")
            add(GoogleVerifier(googleClientId, GoogleVerifier.googleKeys()))
        } else {
            log.info("google sign-in: disabled (set POSTER_GOOGLE_CLIENT_ID to enable)")
        }

        // Apple's audiences: the app's bundle id for the native iOS sign-in, and
        // the Service ID for the Android/web flow. Either enables Apple; both are
        // accepted when both are set. The bundle id is known long before the
        // developer account exists, so enabling is one variable rather than a
        // deploy — the verifier and its tests are already here.
        val appleAudiences = listOf(
            System.getenv("POSTER_APPLE_BUNDLE_ID").orEmpty(),
            System.getenv("POSTER_APPLE_SERVICE_ID").orEmpty(),
        ).filter { it.isNotBlank() }
        if (Features.APPLE_SIGN_IN && appleAudiences.isNotEmpty()) {
            log.info("apple sign-in: enabled (${appleAudiences.size} audience(s))")
            add(AppleVerifier(appleAudiences.joinToString(","), AppleVerifier.appleKeys()))
        } else {
            log.info("apple sign-in: disabled (set POSTER_APPLE_BUNDLE_ID and/or POSTER_APPLE_SERVICE_ID to enable)")
        }
    }

    val tokenService = TokenService(authConfig)
    val authService = AuthService(
        tokens = accountTokens,
        database = database,
        accountRepository = accountRepository,
        passwordHasher = passwordHasher,
        tokenService = tokenService,
        config = authConfig,
        userGroupRepository = userGroupRepository,
    )
    configureBearerAuthentication(authConfig, tokenService, accountRepository)

    // The moderation panel is off unless POSTER_ADMIN_ENABLED=true, so it is
    // never exposed by accident on an environment that does not need it.
    val adminConfig = AdminConfig()
    val moderationRepository = ModerationRepository(database)
    val crashRepository = CrashRepository(driver)
    val reportsRepository = ReportsRepository(database)
    val feedbackRepository = FeedbackRepository(database)
    val commentsRepository = CommentsRepository(database)
    val notificationsRepository = NotificationsRepository(database)
    // Null with the feature off: no rows are written, no routes mounted.
    val notifier = if (Features.PUSH_NOTIFICATIONS) {
        Notifier(notificationsRepository, accountRepository, pushSenders ?: PushSenders.fromEnvironment(), scope = this)
    } else {
        null
    }
    val eventRepository = EventRepository(driver)
    // Operator alerts to Telegram. Not configured on staging or in tests, where
    // it prints instead. Fired and forgotten so a Telegram outage never fails or
    // slows the request that triggered it.
    val alerts = TelegramNotifier.fromEnvironment()
    // The admin panel lives on this same server; the alerts link straight to the
    // section that handles each kind, so a tap goes from the message to acting on it.
    val adminBase = (System.getenv("POSTER_BASE_URL") ?: AppInfo.WEB_ORIGIN) + "/admin"
    fun alert(text: String) { launch { alerts.notify(text) } }
    if (adminConfig.enabled) {
        log.info("admin panel: enabled at /admin")
        configureAdminSessions(adminConfig)
        bootstrapAdmin(adminConfig, accountRepository) { log.info(it) }
    } else {
        // Otherwise /admin simply 404s, which looks identical to a bad deploy.
        log.info("admin panel: disabled (set POSTER_ADMIN_ENABLED=true to enable)")
    }

    // One throttle for the API login, the web delete-account page and the
    // admin panel: all three check the same password, so a separate allowance
    // on any of them would simply be the way around the others.
    val credentialThrottle = AttemptThrottle()
    // Registering gets its own. Sharing the login's would let somebody lock a
    // person out of signing in by registering at their address five times.
    val registerThrottle = AttemptThrottle()

    routing {
        // The web app's bundle, when told where it is (`wasmJsBrowserDistribution`
        // output). Same origin as the API, so no CORS and no second host.
        System.getenv("POSTER_WEB_DIR")?.takeIf { it.isNotBlank() }?.let { dir ->
            staticFiles("/app", java.io.File(dir)) { default("index.html") }
        }
        landingPage()
        privacyPage()
        termsPage()
        healthRoutes()
        wellKnownRoutes()

        if (adminConfig.enabled) {
            adminRoutes(
                accountRepository,
                moderationRepository,
                passwordHasher,
                groupRepository,
                tagRepository,
                userGroupRepository,
                revokeSessions = authService::revokeAll,
                throttle = credentialThrottle,
                crashes = crashRepository,
                reports = reportsRepository,
                feedback = feedbackRepository,
                events = eventRepository,
                comments = commentsRepository,
                onMemberAdded = { userId, group, actor -> notifier?.addedToGroup(userId, group, actor) },
            )
        }

        debugFixtureRoutes(
            enabled = fixturesEnabled,
            confirmAddress = { email ->
                val user = accountRepository.userByEmail(email)
                if (user == null) false else {
                    accountRepository.addOrUpdateUser(user.copy(verifiedAt = java.time.Instant.now().toString()))
                    true
                }
            },
            createGroup = { id, name, inviteCode ->
                groupRepository.addOrUpdateGroup(Group(id, name, inviteCode))
            },
            seedLiked = { postId ->
                applyLikedFixture(postId, accountRepository, database, passwordHasher)
                true
            },
        ) {
            applyIntegrationFixtures(
                database = database,
                accountRepository = accountRepository,
                tagRepository = tagRepository,
                groupRepository = groupRepository,
                userGroupRepository = userGroupRepository,
                passwordHasher = passwordHasher,
            )
        }
        // Crashes, diagnostic events, the startup config and the paywall ping.
        // All four are outside authentication on purpose — see the routes.
        diagnosticRoutes(crashRepository, eventRepository, adminBase, ::alert)

        authRoutes(authService, accountMail, accountRepository, socialVerifiers, credentialThrottle, registerThrottle)
        // Where the links in those emails land.
        accountPages(
            authService,
            accountRepository,
            credentialThrottle,
            // The page draws its Google button only when the server has a
            // verifier for what the button produces.
            googleVerifier = socialVerifiers.firstOrNull { it.provider == "google" },
            googleClientId = googleClientId,
        )
        if (Features.GROUPS) groupPages(groupRepository, userGroupRepository)
        // The public web page a shared post link opens. Unauthenticated and
        // public-only by construction — see PostSharePage / getPostByShareToken.
        if (Features.SHARING) postSharePage(postsRepository, uploadStore)
        // The JSON the app fetches when a poster://post/{token} deep link is
        // opened, so it can show the post in-app. Public-only, same as the page.
        if (Features.SHARING) get("/shared/{token}") {
            val token = call.parameters["token"].orEmpty()
            val post = token.takeIf { it.isNotBlank() }
                ?.let { postsRepository.postByShareToken(it) }
            if (post == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(post)
        }

        // Google Play requires this of every app in a social category, and it
        // has to be reachable by anybody, anywhere, without the app. The
        // address is a setting because the published contact may change
        // without the standards changing.
        childSafetyPage(
            contactEmail = System.getenv("POSTER_SAFETY_CONTACT")
                ?: "contact@example.com",
        )
        if (notifier != null) deviceWithdrawalRoute(notificationsRepository)
        authenticate("auth-jwt") {
            if (uploadStore != null) uploadRoutes(uploadStore, postsRepository, accountRepository)
            if (notifier != null) notificationRoutes(notificationsRepository)
            get("/auth/me") {
                val user = accountRepository.userById(call.authenticatedUserId())
                if (user == null) call.respond(HttpStatusCode.NotFound) else call.respond(user)
            }

            if (Features.FEEDBACK) feedbackRoutes(feedbackRepository, accountRepository, adminBase, ::alert)
            if (Features.TAGS) tagRoutes(tagRepository)
            postRoutes(
                postsRepository,
                accountRepository,
                favoritesRepository,
                commentsRepository,
                userGroupRepository,
                reportsRepository,
                uploadStore,
                notifier,
                adminBase,
                ::alert,
            )
            accountRoutes(accountRepository, postsRepository, authService, uploadStore)
            socialRoutes(bookmarksRepository, followsRepository, postsRepository, accountRepository)
            if (Features.LIKES) {
                favoriteRoutes(favoritesRepository, postsRepository, commentsRepository, accountRepository, notifier)
            }
            if (Features.GROUPS) {
                groupRoutes(groupRepository, userGroupRepository, accountRepository, groupMail, notifier)
            }
        }
    }
}
