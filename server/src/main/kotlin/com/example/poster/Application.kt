package com.example.poster

import com.example.poster.config.AppInfo
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.JsonConvertException
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
import io.ktor.server.request.receive
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import com.example.poster.domain.validation.AccountRules
import com.example.poster.domain.validation.GroupRules
import com.example.poster.domain.validation.FeedbackRules
import com.example.poster.domain.validation.PostRules
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
import com.example.poster.model.CompletePostRequest
import com.example.poster.model.ModerationRepository
import com.example.poster.model.*
import com.example.poster.config.Features
import com.example.poster.domain.validation.ImageRules
import com.example.poster.uploads.UploadStore
import com.example.poster.comments.CommentsRepository
import com.example.poster.comments.commentRoutes
import com.example.poster.push.NotificationsRepository
import com.example.poster.push.Notifier
import com.example.poster.push.PushSender
import com.example.poster.push.PushSenders
import com.example.poster.push.deviceWithdrawalRoute
import com.example.poster.push.notificationRoutes
import com.example.poster.uploads.uploadRoutes
import com.example.poster.db.DatabaseDriverFactory
import com.example.poster.db.DatabaseManager
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

    // So every request shows up: method, path, status. Without it a failed
    // sign-in is invisible and there is nothing to tell "the request never
    // arrived" from "the server refused it" — the whole point when debugging.
    install(CallLogging) {
        format { call ->
            "${call.request.httpMethod.value} ${call.request.path()} -> ${call.response.status()}"
        }
    }

    val database = DatabaseManager(DatabaseDriverFactory()).getDatabase()
    val tagRepository = TagLocalRepository()
    // The starting set, once. Anything already there is left alone, so labels
    // corrected in the panel are not overwritten by a deploy — and a new tag
    // does not need a deploy at all.
    tagRepository.seedCuratedTags()
    val postsRepository = PostsLocalRepository(tagRepository)
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
    val favoritesRepository = FavoritesLocalRepository()
    val groupRepository = GroupLocalRepository()
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
    val accountTokens = AccountTokens()
    val accountMail = AccountMail(mailer = mailer, tokens = accountTokens)
    val groupMail = GroupMail(mailer = mailer)
    // Google sign-in, when this deployment was given a client id. Absent is an
    // ordinary state: the route then answers "not built with that" and the app
    // hides the button, the same way billing does without a key.
    val googleClientId = googleClientIdOverride ?: System.getenv("POSTER_GOOGLE_CLIENT_ID").orEmpty()
    val socialVerifiers = verifiers ?: buildList {
        if (googleClientId.isNotBlank()) {
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
        if (appleAudiences.isNotEmpty()) {
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
    val crashRepository = CrashRepository()
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
    val eventRepository = EventRepository()
    // Operator alerts to Telegram. Not configured on staging or in tests, where
    // it prints instead. Fired and forgotten so a Telegram outage never fails or
    // slows the request that triggered it.
    val alerts = TelegramNotifier.fromEnvironment()
    // The admin panel lives on this same server; the alerts link straight to the
    // section that handles each kind, so a tap goes from the message to acting on it.
    val adminBase = (System.getenv("POSTER_BASE_URL") ?: AppInfo.WEB_ORIGIN) + "/admin"
    fun alert(text: String) = launch { alerts.notify(text) }
    if (adminConfig.enabled) {
        log.info("admin panel: enabled at /admin")
        configureAdminSessions(adminConfig)
        bootstrapAdmin(adminConfig, accountRepository) { log.info(it) }
    } else {
        // Otherwise /admin simply 404s, which looks identical to a bad deploy.
        log.info("admin panel: disabled (set POSTER_ADMIN_ENABLED=true to enable)")
    }

    routing {
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
        /**
         * Where the apps send a crash.
         *
         * Unauthenticated on purpose: an app that crashes before anybody signs
         * in — which is exactly when the worst crashes happen — has no token to
         * send. That makes it something anybody can post to, so the payload is
         * cut to size on the way in and the table keeps only its newest rows.
         */
        if (Features.CRASH_REPORTS) post("/crashes") {
            val report = runCatching { call.receive<CrashReport>() }.getOrNull()
            if (report == null || report.stack.isBlank() || report.type.isBlank()) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }
            crashRepository.record(report)
            alert(buildString {
                val at = report.occurredAt.take(16).replace('T', ' ')
                append("🔴 Crash · $at · ${report.platform} ${report.osVersion} · ${report.device} · app ${report.appVersion}\n")
                append(report.type)
                report.message?.takeIf { it.isNotBlank() }?.let { append(": $it") }
                append("\n")
                append(report.stack.lineSequence().take(6).joinToString("\n"))
                append("\n$adminBase/crashes")
            })
            call.respond(HttpStatusCode.NoContent)
        }

        // Diagnostic events. Optional auth: an event may be sent before anyone
        // has signed in (a slow or failed login), so the route accepts it either
        // way and takes the userId from the token only when there is one — never
        // from the body, which cannot be trusted. The name must be one the app
        // knows, so a bad client cannot fill the table with free text.
        if (Features.TELEMETRY) authenticate("auth-jwt", optional = true) {
            post("/events") {
                val event = runCatching { call.receive<AppEvent>() }.getOrNull()
                if (event == null || event.deviceId.isBlank() || event.name !in AppEventName.ALL) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
                val userId = call.principal<JWTPrincipal>()?.payload?.subject
                eventRepository.record(event, userId)
                call.respond(HttpStatusCode.NoContent)
            }
        }

        // One throttle for both, because both check the same password: a
        // separate allowance on the web page would be the way around the API's.
        val credentialThrottle = AttemptThrottle()
        authRoutes(authService, accountMail, accountRepository, socialVerifiers, credentialThrottle)
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

        /**
         * What the app is allowed to do today, asked at startup.
         *
         * Payments are the only thing here, and they are off until there is
         * somebody legally able to receive the money. The flag lives on the
         * server so switching it on is an environment variable and a restart
         * rather than a release, a review and a wait — the app is already in
         * people's hands by then, and the day the paperwork lands is not a day
         * to be waiting on Google.
         *
         * Unauthenticated on purpose: it says nothing about anybody, and the
         * paywall has to know before there is a session.
         */
        if (Features.SUPPORT) get("/config") {
            call.respond(
                RemoteConfig(
                    paymentsEnabled = System.getenv("POSTER_PAYMENTS_ENABLED")
                        ?.equals("true", ignoreCase = true) == true,
                ),
            )
        }

        /**
         * Somebody pressed a tier while payments were switched off.
         *
         * Worth recording rather than dropping: the paywall is being shown to
         * real people before it can take anything, and whether they reach for
         * it — and which tier — is the only evidence that will exist about
         * whether the tiers are priced and named sensibly. It is gone the
         * moment payments are switched on, because from then on a purchase
         * says it better.
         *
         * Logged rather than stored. A row per tap needs a table, a migration
         * and a screen to read it, for a question that stops being asked in a
         * month; the log already goes where somebody can read it.
         */
        if (Features.SUPPORT) post("/support/interest") {
            val tier = call.receive<Map<String, String>>()["tier"]?.take(64).orEmpty()
            // Optional, and read directly rather than through
            // authenticatedUserId, which asserts a principal: this route sits
            // outside authentication because somebody looking at the paywall
            // before they have an account is exactly the person worth hearing
            // from.
            val viewer = call.principal<JWTPrincipal>()?.payload?.subject
            log.info("support interest: tier=$tier user=${viewer ?: "anonymous"}")
            call.respond(HttpStatusCode.Accepted)
        }

        if (notifier != null) deviceWithdrawalRoute(notificationsRepository)
        authenticate("auth-jwt") {
            if (uploadStore != null) uploadRoutes(uploadStore, postsRepository, accountRepository)
            if (notifier != null) notificationRoutes(notificationsRepository)
            get("/auth/me") {
                val user = accountRepository.userById(call.authenticatedUserId())
                if (user == null) call.respond(HttpStatusCode.NotFound) else call.respond(user)
            }

            if (Features.FEEDBACK) route("/feedback") {
                // Sending feedback. Trimmed and length-checked with the same rule
                // the form uses — the form is not a boundary, anything can post.
                post {
                    val request = runCatching { call.receive<FeedbackRequest>() }.getOrNull()
                    if (request == null) {
                        call.respond(HttpStatusCode.BadRequest, ApiError("Malformed request"))
                        return@post
                    }
                    val message = request.message.trim()
                    if (!FeedbackRules.messageValid(message)) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiError("Feedback is 1 to ${FeedbackRules.MESSAGE_LIMIT} characters"),
                        )
                        return@post
                    }
                    val sender = call.authenticatedUserId()
                    feedbackRepository.submit(sender, message)
                    val who = accountRepository.userById(sender)?.email ?: sender
                    val at = java.time.Instant.now().toString().take(16).replace('T', ' ')
                    alert("💬 Feedback from $who · $at\n$message\n$adminBase/feedback")
                    call.respond(HttpStatusCode.NoContent)
                }
                // The sender's own feedback, with any reply. Newest first.
                get {
                    call.respond(feedbackRepository.forUser(call.authenticatedUserId()))
                }
            }

            if (Features.TAGS) route("/tags") {
            get {
                val tags = tagRepository.allTags()
                call.respond(tags)
            }
            get("/byName/{query}") {
                val query = call.parameters["query"]
                if (query == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@get
                }
                val tags = tagRepository.tagsByName(query)
                call.respond(tags)
            }
            get("/forPost/{postId}") {
                val postId = call.parameters["postId"]
                if (postId == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@get
                }
                val tags = tagRepository.getTagsForPost(postId)
                call.respond(tags)
            }
            // Adding and removing a post's tags used to be here, taking a
            // post id from anybody: a post's tags could be changed, or
            // stripped, by someone who did not write it — and with filtering,
            // retagging one changes who finds it. Tags travel with the post
            // through POST /posts, which checks the author, and nothing in
            // the app ever called these.
        }

        route("/posts") {
            // `likes` is never written to the post row — the favorites table is the
            // source of truth, so fill it in on the way out.
            if (Features.COMMENTS) {
                commentRoutes(commentsRepository, postsRepository, accountRepository) { post, actor -> notifier?.commented(post, actor) }
            }
            fun Post.withLikeCount() = copy(
                likes = favoritesRepository.countPostFavorites(guid).toInt(),
                comments = if (Features.COMMENTS) commentsRepository.countFor(guid) else 0,
            ).withAuthor(accountRepository)

            get {
                // The feed obeys the languages this person reads. Their own
                // posts come back whatever language those are in — the query
                // exempts them, so nothing anybody wrote disappears on them.
                val viewer = call.authenticatedUserId()
                val languages = accountRepository.userById(viewer)?.languages ?: Language.ALL
                // Newest first, capped. A caller may ask for fewer; asking for
                // more than the cap gets the cap, because the point of a limit
                // nobody can raise is that it holds.
                val limit = call.request.queryParameters["limit"]
                    ?.toIntOrNull()
                    // Zero, negative or not a number is a caller with a bug
                    // rather than somebody asking for nothing, so it reads as
                    // no answer at all.
                    ?.takeIf { it > 0 }
                    ?: DEFAULT_FEED_LIMIT
                // Where the last page ended. Both halves or neither: a date
                // without its guid cannot resume from a minute holding two
                // posts, which is exactly when resuming matters.
                val beforeDate = call.request.queryParameters["beforeDate"]
                val beforeGuid = call.request.queryParameters["beforeGuid"]
                val before = if (!beforeDate.isNullOrBlank() && !beforeGuid.isNullOrBlank()) {
                    FeedCursor(beforeDate, beforeGuid)
                } else {
                    null
                }
                // Narrowing to the tags the reader has chosen, comma separated.
                // Blank reads as no filter rather than as a tag nothing carries.
                //
                // `tag` is still accepted, singular, because a client older
                // than multi-select sends it and its feed should keep filtering
                // rather than quietly showing everything.
                val tags = (call.request.queryParameters["tags"]
                    ?: call.request.queryParameters["tag"])
                    .orEmpty()
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                // Narrowing to the rooms the reader has chosen, comma
                // separated, same shape as `tags`. The two are read together:
                // a group and a tag ask "from this room" and "about this",
                // and both have to hold.
                //
                // Unknown or foreign ids are not rejected. The query already
                // limits a reader to the rooms they belong to, so naming one
                // they do not returns nothing — and an error here would tell a
                // caller whether a group id exists, which is not theirs to
                // learn.
                val groups = call.request.queryParameters["groups"]
                    .orEmpty()
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                val query = call.request.queryParameters["q"].orEmpty()
                val posts = postsRepository
                    .visiblePosts(viewer, languages, limit, before, tags, groups, query)
                    .map { it.withLikeCount() }
                call.respond(posts)
            }
            // Your own posts, all of them. The feed is a page and yours can
            // fall outside it, which left My Posts looking like the app had
            // thrown one away.
            get("/mine") {
                val viewer = call.authenticatedUserId()
                if (viewer == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@get
                }
                call.respond(postsRepository.postsByAuthor(viewer).map { it.withLikeCount() })
            }
            get("/byId/{taskId}") {
                val guid = call.parameters["taskId"]
                if (guid == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@get
                }
                val viewer = call.authenticatedUserId()
                // Fetching by id must obey the same rules as the feed, or a
                // private post leaks to anyone who learns its id. Not-visible
                // reads as not-found: whether it exists is itself private.
                val post = postsRepository.visiblePostById(viewer, guid)
                if (post == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                call.respond(post.withLikeCount())
            }
            post {
                try {
                    val post = call.receive<Post>()
                    val currentUserId = call.authenticatedUserId()
                    // Writing needs a confirmed address; reading does not.
                    //
                    // Enforced here rather than by the app checking a flag: a
                    // modified client can ignore a field, and can ignore a
                    // status code just as easily, so the only thing that holds
                    // is the server declining to write. The code is there so
                    // the app knows to show the "confirm your email" screen
                    // rather than a generic failure.
                    if (Features.EMAIL_VERIFICATION_REQUIRED &&
                        accountRepository.userById(currentUserId)?.verifiedAt == null
                    ) {
                        call.respond(
                            HttpStatusCode.Forbidden,
                            ApiError(
                                message = "Confirm your email address before sharing a post",
                                code = ApiError.EMAIL_NOT_VERIFIED,
                            ),
                        )
                        return@post
                    }
                    val existingPost = postsRepository.postById(post.guid)
                    if (post.author != currentUserId || (existingPost != null && existingPost.author != currentUserId)) {
                        call.respond(HttpStatusCode.Forbidden)
                        return@post
                    }
                    // The form counts to the same number, but a form is not a
                    // boundary: anything can post here, and a post nobody can
                    // read past is the thing being prevented.
                    if (PostRules.messageTooLong(post.message) ||
                        PostRules.titleTooLong(post.title)
                    ) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiError("A post is at most ${PostRules.MESSAGE_LIMIT} characters"),
                        )
                        return@post
                    }
                    // An image must be one this server stored; a made-up id
                    // would be a post pointing at nothing, or at somebody else's
                    // image. With images off the field is simply dropped.
                    val saved = if (uploadStore == null) post.copy(image = null) else post
                    val image = saved.image
                    if (image != null && (!ImageRules.isValidId(image) || uploadStore?.file(image) == null)) {
                        call.respond(HttpStatusCode.BadRequest, ApiError("Unknown image"))
                        return@post
                    }
                    postsRepository.addOrUpdatePost(saved)
                    // Replacing or removing the picture leaves the old file with
                    // no post; take it now rather than waiting for the sweep.
                    val previousImage = existingPost?.image
                    if (previousImage != null && previousImage != saved.image) uploadStore?.delete(previousImage)
                    call.respond(HttpStatusCode.NoContent)
                } catch (ex: IllegalStateException) {
                    call.respond(HttpStatusCode.BadRequest)
                } catch (ex: JsonConvertException) {
                    call.respond(HttpStatusCode.BadRequest)
                }
            }
            // Completion is the author's to give: they are the only one who knows
            // how it went. Moderators can hide a post, not conclude it.
            if (Features.POST_COMPLETION) post("/{postId}/complete") {
                val guid = call.parameters["postId"]
                if (guid == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
                val post = postsRepository.postById(guid)
                if (post == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@post
                }
                if (post.author != call.authenticatedUserId()) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@post
                }
                val body = runCatching { call.receive<CompletePostRequest>() }.getOrNull()
                postsRepository.completePost(guid, body?.message?.trim()?.takeIf { it.isNotBlank() })
                call.respond(HttpStatusCode.NoContent)
            }

            if (Features.POST_COMPLETION) post("/{postId}/reopen") {
                val guid = call.parameters["postId"]
                if (guid == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
                val post = postsRepository.postById(guid)
                if (post == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@post
                }
                if (post.author != call.authenticatedUserId()) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@post
                }
                postsRepository.reopenPost(guid)
                call.respond(HttpStatusCode.NoContent)
            }

            /**
             * A share link for a post: mint (or reuse) its opaque token.
             *
             * Any signed-in person may share a PUBLIC post — it is already
             * public, and sharing is the point. Group and private posts are
             * refused: they are not for strangers, and the web page renders public
             * only, so a token must never exist for one. The token is unrelated to
             * the guid, so the URL it goes into cannot be turned back into an id.
             */
            if (Features.SHARING) post("/{postId}/share") {
                val guid = call.parameters["postId"]
                if (guid == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
                val post = postsRepository.postById(guid)
                if (post == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@post
                }
                // "public" is PostVisibility.PUBLIC; compared as a literal to
                // avoid an import for one use.
                if (post.visibility != "public") {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError("Only public posts can be shared"),
                    )
                    return@post
                }
                val token = postsRepository.ensureShareToken(guid) {
                    newShareToken { candidate -> postsRepository.postByShareToken(candidate) != null }
                }
                call.respond(mapOf("token" to token))
            }

            /**
             * "This should not be here."
             *
             * Recorded, not acted on. In a family app the usual reason a
             * post looks wrong is that somebody misread it, and hiding
             * something because one reader pressed a button would be a way to
             * silence the person it was written about.
             *
             * Answers the same whether this is the first report or the tenth,
             * and whether the post exists at all beyond the check below —
             * a caller learning which post ids are real is a caller mapping
             * other people's private posts.
             */
            if (Features.REPORTS) post("/{postId}/report") {
                val guid = call.parameters["postId"]
                val reporter = call.authenticatedUserId()
                if (guid == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
                val reason = runCatching { call.receive<ReportRequest>().reason }.getOrNull()
                val post = postsRepository.postById(guid)
                if (post != null && post.author != reporter) {
                    val trimmedReason = reason?.take(500)?.takeIf { it.isNotBlank() }
                    reportsRepository.record(post.guid, reporter, trimmedReason)
                    val reporterEmail = accountRepository.userById(reporter)?.email ?: reporter
                    val authorEmail = accountRepository.userById(post.author)?.email ?: post.author
                    alert(buildString {
                        append("🚩 Report · \"${post.title}\"\n")
                        append("reported by $reporterEmail — reason: ${trimmedReason ?: "(none given)"}\n")
                        append("post ${post.guid} · author $authorEmail")
                        append("\n$adminBase/reports")
                    })
                }
                call.respond(HttpStatusCode.NoContent)
            }

            delete("/{postId}") {
                val guid = call.parameters["postId"]
                if (guid == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@delete
                }
                val existingPost = postsRepository.postById(guid)
                if (existingPost != null && existingPost.author != call.authenticatedUserId()) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@delete
                }
                if (postsRepository.removePost(guid)) {
                    existingPost?.image?.let { uploadStore?.delete(it) }
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }

        route("/accounts") {
            // Listing every account — names, emails, roles — was reachable by
            // anybody signed in, and nothing in the app asked for it.
            get("/byId/{userId}") {
                val guid = call.parameters["userId"]
                if (guid == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@get
                }
                // Your own, and only your own — this returns an email, a role
                // and a status. The app restores its own session with it and
                // never asks about anybody else: it compares author ids rather
                // than looking people up.
                if (guid != call.authenticatedUserId()) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@get
                }
                val user = accountRepository.userById(guid)
                if (user == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                call.respond(user)
            }
            // Looking an account up by email was here, guarded to your own
            // address — which made it a way of asking the server for something
            // you already knew. Nothing called it.
            post {
                try {
                    val user = call.receive<User>()
                    val currentUser = accountRepository.userById(call.authenticatedUserId())
                    if (currentUser == null || user.guid != currentUser.guid) {
                        call.respond(HttpStatusCode.Forbidden)
                        return@post
                    }
                    // The same limits registration enforces: editing a profile is
                    // the other way an unbounded name or a broken email reaches
                    // the store, and this route trusted the body as-is.
                    val name = user.name.trim()
                    val surname = user.surname.trim()
                    val email = user.email.trim().lowercase()
                    if (!AccountRules.nameValid(name) ||
                        !AccountRules.surnameValid(surname) ||
                        !AccountRules.emailValid(email)
                    ) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiError("Name, surname and a valid email are required"),
                        )
                        return@post
                    }
                    // Start from the stored account and apply only what a
                    // person may change about themselves.
                    //
                    // This used to take the body as the new account and put the
                    // password back, which meant `role` and `status` arrived
                    // from the client: one POST with "role":"admin" made you an
                    // admin, and a banned person could lift their own ban. An
                    // allowlist cannot be got wrong the same way — a field
                    // added to User later is not editable until it is named here.
                    val languages = user.languages.filter(Language::isKnown).distinct()
                        .ifEmpty { currentUser.languages }
                    // An avatar is an upload id this server stored (feature.authors + images).
                    val photo = user.photo?.takeIf { Features.AUTHORS }
                    if (photo != null && (!ImageRules.isValidId(photo) || uploadStore?.file(photo) == null)) {
                        call.respond(HttpStatusCode.BadRequest, ApiError("Unknown picture"))
                        return@post
                    }
                    val previousPhoto = currentUser.photo
                    accountRepository.addOrUpdateUser(
                        currentUser.copy(
                            name = name,
                            surname = surname,
                            email = email,
                            photo = photo,
                            languages = languages,
                            // The language your posts start in has to be one
                            // you actually read.
                            defaultLanguage = user.defaultLanguage.takeIf { it in languages }
                                ?: languages.first(),
                            // Their choice to be named on a post's liked-by list.
                            showName = user.showName,
                        ),
                    )
                    if (previousPhoto != null && previousPhoto != photo) uploadStore?.delete(previousPhoto)
                    call.respond(HttpStatusCode.NoContent)
                } catch (ex: IllegalStateException) {
                    call.respond(HttpStatusCode.BadRequest)
                } catch (ex: JsonConvertException) {
                    call.respond(HttpStatusCode.BadRequest)
                }
            }
            delete("/{userId}") {
                val guid = call.parameters["userId"]
                if (guid == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@delete
                }
                if (guid != call.authenticatedUserId()) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@delete
                }
                // Sessions first, so a request already in flight cannot write
                // something back after the rows are gone.
                authService.revokeAll(guid)
                val images = postsRepository.postsByAuthor(guid).mapNotNull { it.image }
                if (accountRepository.deleteAccountAndContent(guid)) {
                    images.forEach { uploadStore?.delete(it) }
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }

        if (Features.LIKES) route("/favorites") {
            // Same as /posts: the stored `likes` column is meaningless, fill it from the table.
            fun Post.withLikeCount() = copy(
                likes = favoritesRepository.countPostFavorites(guid).toInt(),
                comments = if (Features.COMMENTS) commentsRepository.countFor(guid) else 0,
            ).withAuthor(accountRepository)

            get("/me") {
                val posts = favoritesRepository.getUserFavoritePosts(call.authenticatedUserId())
                call.respond(posts.map { it.withLikeCount() })
            }
            get("/user/{userId}") {
                val userId = call.parameters["userId"]
                if (userId == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@get
                }
                if (userId != call.authenticatedUserId()) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@get
                }
                val posts = favoritesRepository.getUserFavoritePosts(userId)
                call.respond(posts.map { it.withLikeCount() })
            }
            get("/check/{userId}/{postId}") {
                val userId = call.parameters["userId"]
                val postId = call.parameters["postId"]
                if (userId == null || postId == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@get
                }
                if (userId != call.authenticatedUserId()) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@get
                }
                val isFavorite = favoritesRepository.isPostFavorite(userId, postId)
                call.respond(isFavorite)
            }
            post("/{userId}/{postId}") {
                val userId = call.parameters["userId"]
                val postId = call.parameters["postId"]
                if (userId == null || postId == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
                if (userId != call.authenticatedUserId()) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@post
                }
                favoritesRepository.addFavoritePost(userId, postId)
                postsRepository.postById(postId)?.let { notifier?.liked(it, userId) }
                call.respond(HttpStatusCode.NoContent)
            }
            delete("/{userId}/{postId}") {
                val userId = call.parameters["userId"]
                val postId = call.parameters["postId"]
                if (userId == null || postId == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@delete
                }
                if (userId != call.authenticatedUserId()) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@delete
                }
                if (favoritesRepository.removeFavoritePost(userId, postId)) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
            // Who liked a given post was readable by anybody, for
            // any post. That is a list of people beside a subject they may
            // not have chosen to be associated with, and no screen shows it —
            // the cards show a count, which is the next route down.
            get("/count/{postId}") {
                val postId = call.parameters["postId"]
                if (postId == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@get
                }
                val count = favoritesRepository.countPostFavorites(postId)
                call.respond(count)
            }
            // The roster the detail screen shows: only the names of people who
            // opted in (User.showName), newest first, plus the total so the UI
            // can add "and N more people". Signed-in only (this block is under
            // authenticate), so it is not the open, anybody-can-read list the
            // count route's comment warns about.
            get("/post/{postId}/people") {
                val postId = call.parameters["postId"]
                if (postId == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@get
                }
                call.respond(favoritesRepository.likers(postId))
            }
        }

        if (Features.GROUPS) route("/groups") {
            // Managing members and invites is the owner's, and now also any
            // member the owner promoted to admin. Closing the group and
            // changing roles stay owner-only, so they keep the raw owner check.
            fun canManage(id: String, callerId: String): Boolean =
                groupRepository.groupById(id)?.owner == callerId ||
                    userGroupRepository.roleOf(callerId, id) == "admin"

            // Groups CRUD
            get {
                val groups = groupRepository.allGroups()
                call.respond(groups)
            }
            // feature.publicGroups: what anybody may browse and join without an invite.
            if (Features.PUBLIC_GROUPS) get("/public") {
                call.respond(groupRepository.publicGroups())
            }
            if (Features.PUBLIC_GROUPS) post("/{id}/visibility") {
                val id = call.parameters["id"].orEmpty()
                val visibility = runCatching { call.receive<Map<String, String>>()["visibility"] }.getOrNull()
                if (visibility != GroupVisibility.PUBLIC && visibility != GroupVisibility.PRIVATE) {
                    call.respond(HttpStatusCode.BadRequest, ApiError("visibility is public or private"))
                    return@post
                }
                if (groupRepository.groupById(id) == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@post
                }
                if (!canManage(id, call.authenticatedUserId())) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@post
                }
                groupRepository.setVisibility(id, visibility)
                call.respond(HttpStatusCode.NoContent)
            }
            get("/byId/{id}") {
                val id = call.parameters["id"]
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@get
                }
                val group = groupRepository.groupById(id)
                if (group == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    call.respond(group)
                }
            }
            get("/byInvite/{inviteCode}") {
                val inviteCode = call.parameters["inviteCode"]
                if (inviteCode == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@get
                }
                val group = groupRepository.groupByInviteCode(inviteCode)
                if (group == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    call.respond(group)
                }
            }
            /**
             * Makes a group, with the caller as its owner and first member.
             *
             * Renaming and deleting stay the admin panel's. Those used to be
             * here too, taking an id and a whole Group: any signed-in
             * person could overwrite any group — including its invite code,
             * which is how people get in — or delete one outright, taking every
             * post in it out of sight of its members. This takes a name and
             * nothing else, and touches only a row it just made.
             *
             * The invite code is generated here rather than accepted from the
             * caller. A chosen code is a guessable one, and the code is the
             * whole of the security on a group.
             */
            post("/create") {
                val userId = call.authenticatedUserId()
                val request = runCatching { call.receive<CreateGroupRequest>() }.getOrNull()
                val name = request?.name?.trim()
                // Public only when the feature is on; otherwise everything is invite-only.
                val visibility = if (Features.PUBLIC_GROUPS && request?.visibility == GroupVisibility.PUBLIC) GroupVisibility.PUBLIC else GroupVisibility.PRIVATE
                if (name.isNullOrBlank() || GroupRules.nameTooLong(name)) {
                    call.respond(HttpStatusCode.BadRequest, "A group needs a name")
                    return@post
                }
                // The same bar as writing a post, and for the same reason: a
                // group is a room other people are invited into, and an
                // address nobody has confirmed is not a person anybody can be
                // asked to trust. Enforced here rather than by the app checking
                // a flag — a modified client ignores a field as easily as it
                // ignores a status code, and only the server declining to write
                // actually holds.
                if (Features.EMAIL_VERIFICATION_REQUIRED &&
                    accountRepository.userById(userId)?.verifiedAt == null
                ) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ApiError(
                            message = "Confirm your email address before making a group",
                            code = ApiError.EMAIL_NOT_VERIFIED,
                        ),
                    )
                    return@post
                }
                if (groupRepository.countOwnedBy(userId) >= GROUPS_PER_PERSON) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        "You have created as many groups as one account can",
                    )
                    return@post
                }
                val group = Group(
                    id = java.util.UUID.randomUUID().toString(),
                    name = name,
                    inviteCode = newInviteCode(groupRepository::groupByInviteCode),
                    owner = userId,
                    visibility = visibility,
                )
                groupRepository.addOrUpdateGroup(group)
                // Joined here, not left to the client: a group whose
                // creator is not in it shows up nowhere and cannot be reached,
                // and a second call is a second chance to fail.
                userGroupRepository.addUserToGroup(userId, group.id)
                call.respond(HttpStatusCode.Created, group)
            }


            /**
             * Who is in a group.
             *
             * Members only: the list of people in a room is the room's business
             * and nobody else's. Names and no addresses — see [GroupMember].
             */
            get("/{id}/members") {
                val userId = call.authenticatedUserId()
                val id = call.parameters["id"].orEmpty()
                if (!userGroupRepository.isMember(userId, id)) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@get
                }
                val owner = groupRepository.groupById(id)?.owner
                call.respond(userGroupRepository.membersOf(id, owner))
            }

            /**
             * Removing somebody.
             *
             * The owner's to do, because a room where private posts are shared
             * needs somebody able to close the door — without it the only
             * recourse against one bad actor is abandoning the group and
             * rebuilding it around everybody else.
             *
             * Not themselves: an owner who removes themselves leaves a room
             * nobody can administer. Leaving is a different act and has its own
             * route.
             *
             * What the person wrote stays. It was shared in good faith and
             * others may still be liking it; they keep it in My Posts and
             * simply stop seeing the group's.
             */
            delete("/{id}/members/{memberId}") {
                val userId = call.authenticatedUserId()
                val id = call.parameters["id"].orEmpty()
                val memberId = call.parameters["memberId"].orEmpty()
                val group = groupRepository.groupById(id)
                if (group == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@delete
                }
                if (!canManage(id, userId)) {
                    call.respond(HttpStatusCode.Forbidden, "Only an owner or admin can remove somebody")
                    return@delete
                }
                // An admin cannot remove the owner or another admin — that is a
                // role change, which is owner-only. They manage plain members.
                val targetRole = userGroupRepository.roleOf(memberId, id)
                val targetIsPrivileged = memberId == group.owner || targetRole == "admin"
                if (targetIsPrivileged && group.owner != userId) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        "Only the owner can remove an admin",
                    )
                    return@delete
                }
                if (memberId == userId) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        "An owner cannot remove themselves. Leave the group instead.",
                    )
                    return@delete
                }
                userGroupRepository.removeUserFromGroup(memberId, id)
                call.respond(HttpStatusCode.NoContent)
            }

            /**
             * Promoting a member to admin, or back to a plain member.
             *
             * The owner's alone: an admin manages members and invites, but who
             * gets that power is the owner's to decide, or one admin could make
             * more and the owner would lose the room. The owner's own role is not
             * a thing to set — they are the owner, recorded on the group, not
             * a membership row — so targeting them, or oneself, is refused.
             */
            post("/{id}/members/{memberId}/role") {
                val userId = call.authenticatedUserId()
                val id = call.parameters["id"].orEmpty()
                val memberId = call.parameters["memberId"].orEmpty()
                val group = groupRepository.groupById(id)
                if (group == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@post
                }
                if (group.owner != userId) {
                    call.respond(HttpStatusCode.Forbidden, "Only the owner can change roles")
                    return@post
                }
                if (memberId == group.owner) {
                    call.respond(HttpStatusCode.BadRequest, "The owner's role cannot be changed")
                    return@post
                }
                val role = call.receive<Map<String, String>>()["role"]
                if (role != "admin" && role != "member") {
                    call.respond(HttpStatusCode.BadRequest, "Role must be admin or member")
                    return@post
                }
                if (!userGroupRepository.isMember(memberId, id)) {
                    call.respond(HttpStatusCode.NotFound)
                    return@post
                }
                userGroupRepository.setRole(memberId, id, role)
                call.respond(HttpStatusCode.NoContent)
            }

            /**
             * Closing a group.
             *
             * The owner's, and only theirs. What was shared into it becomes
             * private rather than being deleted — see
             * [GroupRepository.removeGroup], which does the whole thing
             * in one transaction.
             *
             * There was no way to do this at all until now: groups could
             * be made and never removed, so every one ever created by accident
             * is still there.
             */
            delete("/{id}") {
                val userId = call.authenticatedUserId()
                val id = call.parameters["id"].orEmpty()
                val group = groupRepository.groupById(id)
                if (group == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@delete
                }
                if (group.owner != userId) {
                    call.respond(HttpStatusCode.Forbidden, "Only the owner can close a group")
                    return@delete
                }
                groupRepository.removeGroup(id)
                call.respond(HttpStatusCode.NoContent)
            }

            /** The invitations an owner has issued, spent and unspent. */
            get("/{id}/invites") {
                val userId = call.authenticatedUserId()
                val id = call.parameters["id"].orEmpty()
                if (!canManage(id, userId)) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@get
                }
                call.respond(userGroupRepository.invitesFor(id))
            }

            /** A new invitation, good once. */
            post("/{id}/invites") {
                val userId = call.authenticatedUserId()
                val id = call.parameters["id"].orEmpty()
                if (!canManage(id, userId)) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@post
                }
                val code = newInviteCode { taken ->
                    // Unique across every invite, not just this group's: a
                    // code is redeemed without saying which group it is for.
                    if (userGroupRepository.invitesFor(id).any { it.code == taken }) {
                        Group(id = "taken", name = "", inviteCode = taken)
                    } else {
                        userGroupRepository.getGroupByInviteCode(taken)
                    }
                }
                userGroupRepository.createInvite(
                    groupId = id,
                    createdBy = userId,
                    code = code,
                    at = java.time.Instant.now().toString(),
                )
                call.respond(HttpStatusCode.Created, mapOf("code" to code))
            }

            /**
             * An invitation sent to an address, good once and only for them.
             *
             * The answer is the same whether or not that address has an
             * account. Saying which would make this a way to ask whether a
             * given person uses this app, which is not a thing an app should
             * answer questions about. The unregistered get
             * a message telling them what Poster is; the registered get one
             * that takes them to the room.
             *
             * It is also the same when a rate limit stopped the send, for the
             * same kind of reason: "too many" tells the sender their earlier
             * ones landed.
             */
            post("/{id}/invites/email") {
                val userId = call.authenticatedUserId()
                val id = call.parameters["id"].orEmpty()
                val group = groupRepository.groupById(id)
                if (group == null || !canManage(id, userId)) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@post
                }
                val address = call.receive<Map<String, String>>()["email"]
                    .orEmpty()
                    .trim()
                if (!AuthService.EMAIL_PATTERN.matches(address)) {
                    // A malformed address is the sender's own typing, not a
                    // fact about anybody else, so this one is safe to report.
                    call.respond(HttpStatusCode.BadRequest, "That does not look like an email address")
                    return@post
                }

                val recipient = accountRepository.userByEmail(address)
                val code = newInviteCode { taken ->
                    if (userGroupRepository.invitesFor(id).any { it.code == taken }) {
                        Group(id = "taken", name = "", inviteCode = taken)
                    } else {
                        userGroupRepository.getGroupByInviteCode(taken)
                    }
                }
                userGroupRepository.createInvite(
                    groupId = id,
                    createdBy = userId,
                    code = code,
                    at = java.time.Instant.now().toString(),
                    sentTo = address,
                )
                val sender = accountRepository.userById(userId)
                groupMail.sendInvitation(
                    senderId = userId,
                    toEmail = address,
                    inviterName = listOf(sender?.name, sender?.surname)
                        .filterNotNull()
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                        .ifBlank { "Somebody" },
                    groupName = group.name,
                    code = code,
                    recipientHasAccount = recipient != null,
                    // The language of whoever is being written to, when the app
                    // knows it. A stranger gets the sender's, which is a better
                    // guess than the default: people invite people they speak
                    // to.
                    language = recipient?.languages?.firstOrNull()
                        ?: sender?.languages?.firstOrNull()
                        ?: Language.DEFAULT,
                )
                call.respond(HttpStatusCode.Accepted)
            }

            /** Withdrawing one that has not been used. */
            post("/{id}/invites/{code}/revoke") {
                val userId = call.authenticatedUserId()
                val id = call.parameters["id"].orEmpty()
                if (!canManage(id, userId)) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@post
                }
                val revoked = userGroupRepository.revokeInvite(
                    groupId = id,
                    code = call.parameters["code"].orEmpty().uppercase(),
                    at = java.time.Instant.now().toString(),
                )
                call.respond(if (revoked) HttpStatusCode.NoContent else HttpStatusCode.NotFound)
            }

            // Membership
            get("/user/{userId}") {
                val userId = call.parameters["userId"]
                if (userId == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@get
                }
                if (userId != call.authenticatedUserId()) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@get
                }
                val userGroups = userGroupRepository.getGroupsForUser(userId)
                call.respond(userGroups)
            }
            /**
             * Join a user to a group by invite code or groupId
             * POST /groups/join { "userId": "...", "inviteCode": "..." } or { "userId": "...", "groupId": "..." }
             */
            post("/join") {
                val params = call.receive<Map<String, String>>()
                val userId = call.authenticatedUserId()
                val inviteCode = params["inviteCode"]
                val groupIdParam = params["groupId"]
                if (inviteCode == null && groupIdParam == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
                // An invite is spent by using it. The old behaviour matched a
                // code that lived on the group and never changed, so a code
                // posted anywhere kept working for anybody who found it, and
                // the owner could neither see it nor stop it.
                //
                // The room this code is for, found before anything is spent: an
                // invitation is good once, and burning one on somebody already
                // inside wastes it for the person it was meant for. Looked up
                // first rather than handled afterwards, because afterwards is
                // too late to give it back.
                val alreadyIn = inviteCode
                    ?.let { userGroupRepository.inviteByCode(it.trim().uppercase()) }
                    ?.takeIf { userGroupRepository.isMember(userId, it.groupId) }
                    ?.let { groupRepository.groupById(it.groupId) }

                val group = when {
                    alreadyIn != null -> alreadyIn
                    inviteCode != null -> when (
                        val outcome = userGroupRepository.spendInvite(
                            code = inviteCode.trim().uppercase(),
                            userId = userId,
                            at = java.time.Instant.now().toString(),
                            // An invite that was emailed belongs to the address it
                            // was sent to. One made by hand is unbound and this is
                            // not consulted.
                            spenderEmail = accountRepository.userById(userId)?.email.orEmpty(),
                        )
                    ) {
                        is JoinOutcome.Joined -> outcome.group
                        // Distinct from Invalid so the reader learns it is their
                        // address, not the code — see JoinOutcome. Only reachable
                        // by someone already holding a live code.
                        JoinOutcome.WrongAddress -> {
                            call.respond(
                                HttpStatusCode.Forbidden,
                                "This invitation was sent to a different email address",
                            )
                            return@post
                        }
                        JoinOutcome.Invalid -> {
                            call.respond(
                                HttpStatusCode.NotFound,
                                "That invitation is not valid any more",
                            )
                            return@post
                        }
                    }
                    // By id, for somebody the app already knows is allowed —
                    // there is no code to spend and nothing to check here that
                    // the membership row does not already say.
                    // Joining by id, without an invite, is what a public group is for;
                    // an invite-only group is not reachable this way.
                    groupIdParam != null -> groupRepository.groupById(groupIdParam)
                        ?.takeIf { Features.PUBLIC_GROUPS && it.visibility == GroupVisibility.PUBLIC }
                    else -> null
                }
                if (group == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        "That invitation is not valid any more",
                    )
                    return@post
                }
                userGroupRepository.addUserToGroup(userId, group.id)
                notifier?.joinedGroup(group, userId)
                call.respond(HttpStatusCode.NoContent)
            }
            delete("/leave") {
                val params = call.receive<Map<String, String>>()
                val userId = call.authenticatedUserId()
                val groupId = params["groupId"]
                if (groupId == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@delete
                }
                userGroupRepository.removeUserFromGroup(userId, groupId)
                call.respond(HttpStatusCode.NoContent)
            }
            // Asking whether somebody is in a group, and listing who is,
            // both had their clients removed with UserRepository. The second
            // handed out a group's membership to any member of it.
        }
        }
    }
}

@kotlinx.serialization.Serializable
private data class CreateGroupRequest(val name: String, val visibility: String = GroupVisibility.PRIVATE)

/**
 * How many groups one account may create.
 *
 * A cap rather than a rate limit: the point is that nobody fills the table
 * before there is any moderation to clean up after them, and five is more than
 * anybody running a family, a club and a couple of friends will need.
 */
private const val GROUPS_PER_PERSON = 5

/**
 * An invite code nobody can guess and anybody can read out loud.
 *
 * No vowels, so it cannot spell anything; no 0/O or 1/I/L, because these get
 * read down a phone line and written on paper. Retried on collision rather than
 * trusted to be unique — the space is large but the table is small, and a
 * duplicate would silently put somebody in the wrong group.
 */
internal fun newInviteCode(taken: (String) -> Group?): String {
    val alphabet = "BCDFGHJKMNPQRSTVWXYZ23456789"
    // SecureRandom, not Random.default. This code is the whole of the security
    // on a group — the comment above says nobody can guess it, and that is
    // only true of a generator built to resist guessing. The admin panel's copy
    // of this function had it right; this one did not.
    val random = java.security.SecureRandom()
    repeat(10) {
        val code = (1..8).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
        if (taken(code) == null) return code
    }
    error("could not find an unused invite code")
}

/**
 * An opaque token for a public share URL. Longer and lower-case than an invite
 * code — it is not read aloud, only clicked — and, like the invite code,
 * SecureRandom and unrelated to any id, so a share URL cannot be reversed into a
 * post's guid. [taken] reports a collision so a fresh one is drawn.
 */
internal fun newShareToken(taken: (String) -> Boolean): String {
    val alphabet = "abcdefghijkmnpqrstuvwxyz23456789"
    val random = java.security.SecureRandom()
    repeat(10) {
        val token = (1..12).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
        if (!taken(token)) return token
    }
    error("could not find an unused share token")
}

/** The account behind the bearer token. Internal so the auth routes share it. */
internal fun ApplicationCall.authenticatedUserId(): String =
    checkNotNull(principal<JWTPrincipal>()?.payload?.subject)

/**
 * feature.authors: the writer's name and avatar go out with the post. A blank
 * surname (an account made by a sign-in link) shows as the name alone.
 */
internal fun Post.withAuthor(accounts: AccountRepository): Post {
    if (!Features.AUTHORS) return this
    val user = accounts.userById(author) ?: return this
    return copy(authorName = "${user.name} ${user.surname}".trim(), authorPhoto = user.photo)
}

