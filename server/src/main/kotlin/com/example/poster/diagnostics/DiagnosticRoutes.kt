package com.example.poster.diagnostics

import com.example.poster.config.Features
import com.example.poster.model.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.log
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import kotlinx.serialization.json.Json
import kotlinx.io.readByteArray
import io.ktor.utils.io.readRemaining
import io.ktor.server.request.receiveChannel
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/**
 * The routes that say something about the app rather than about anybody's
 * posts: crash reports, diagnostic events, the remote config the app reads at
 * startup, and the paywall's interest ping.
 *
 * All four sit outside the bearer-authenticated block on purpose — each one is
 * needed at a moment when there may be no session — so each carries its own
 * feature gate and its own reason, given at the route.
 */
internal fun Route.diagnosticRoutes(
    crashRepository: CrashRepository,
    eventRepository: EventRepository,
    adminBase: String,
    alert: (String) -> Unit,
    intakeLimit: IntakeLimit = IntakeLimit(),
) {
    /**
     * Where the apps send a crash.
     *
     * Unauthenticated on purpose: an app that crashes before anybody signs
     * in — which is exactly when the worst crashes happen — has no token to
     * send. That makes it something anybody can post to, so the payload is
     * cut to size on the way in and the table keeps only its newest rows.
     */
    if (Features.CRASH_REPORTS) post("/crashes") {
        if (!intakeLimit.take()) {
            call.respond(HttpStatusCode.TooManyRequests)
            return@post
        }
        val report = runCatching { call.receiveBounded<CrashReport>(MAX_INTAKE_BODY) }.getOrNull()
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
            if (!intakeLimit.take()) {
                call.respond(HttpStatusCode.TooManyRequests)
                return@post
            }
            val event = runCatching { call.receiveBounded<AppEvent>(MAX_INTAKE_BODY) }.getOrNull()
            if (event == null || event.deviceId.isBlank() || event.name !in AppEventName.ALL) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }
            val userId = call.principal<JWTPrincipal>()?.payload?.subject
            eventRepository.record(event, userId)
            call.respond(HttpStatusCode.NoContent)
        }
    }

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
        call.application.log.info("support interest: tier=$tier user=${viewer ?: "anonymous"}")
        call.respond(HttpStatusCode.Accepted)
    }
}

/**
 * The most an unauthenticated report may be. The server-wide ceiling in
 * Application.kt goes on the declared Content-Length, and a chunked body
 * declares none — so the two routes a stranger can reach read their own bodies
 * with a hard stop instead of handing an unbounded stream to the deserializer.
 */
private const val MAX_INTAKE_BODY = 32L * 1024

/** Reads at most [limit] bytes, then parses. Anything longer is refused, not truncated. */
private suspend inline fun <reified T> ApplicationCall.receiveBounded(limit: Long): T {
    val bytes = receiveChannel().readRemaining(limit + 1).readByteArray()
    require(bytes.size <= limit) { "body over ${limit} bytes" }
    return Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
        .decodeFromString(bytes.decodeToString())
}
