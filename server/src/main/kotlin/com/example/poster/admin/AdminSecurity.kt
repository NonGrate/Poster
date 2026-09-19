package com.example.poster.admin

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.RoutingResolveContext
import io.ktor.server.sessions.SessionTransportTransformerMessageAuthentication
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.util.hex
import com.example.poster.model.AccountRepository
import com.example.poster.model.User
import java.security.SecureRandom

/**
 * The admin session. Separate from the mobile app's bearer tokens: HTMX posts
 * forms from a browser and cannot carry an Authorization header.
 *
 * [csrf] is minted at login and must accompany every mutating form, so a third
 * party site cannot drive the panel with the operator's cookie.
 */
@kotlinx.serialization.Serializable
data class AdminSession(val userId: String, val csrf: String)

/**
 * Values typed into a deployment UI arrive with stray quotes and whitespace more
 * often than not, and the failure mode here is a silent 404 rather than an
 * error, so they are cleaned before they are read.
 */
private fun env(name: String): String? =
    System.getenv(name)?.trim()?.trim('"')?.trim('\'')?.takeIf { it.isNotEmpty() }

class AdminConfig(
    /** Promoted to admin at startup. The only way an admin is created. */
    val bootstrapEmail: String? = env("POSTER_ADMIN_EMAIL"),
    /** Off unless explicitly enabled, so the panel is not exposed by accident. */
    val enabled: Boolean = env("POSTER_ADMIN_ENABLED")?.lowercase() == "true",
    signKey: String? = env("POSTER_ADMIN_SESSION_KEY"),
) {
    /** A random key means sessions do not survive a restart, which is acceptable. */
    val sessionSignKey: ByteArray = signKey?.toByteArray()
        ?: ByteArray(32).also { SecureRandom().nextBytes(it) }
}

fun Application.configureAdminSessions(config: AdminConfig) {
    install(Sessions) {
        cookie<AdminSession>("poster_admin") {
            cookie.path = "/admin"
            cookie.httpOnly = true
            cookie.extensions["SameSite"] = "Strict"
            // Set when the panel is served over TLS, which is how it should run.
            cookie.secure = env("POSTER_ADMIN_INSECURE_COOKIE")?.lowercase() != "true"
            cookie.maxAgeInSeconds = 60 * 60 * 8
            transform(SessionTransportTransformerMessageAuthentication(config.sessionSignKey))
        }
    }
}

/** Promotes the configured account once, loudly, and never creates one. */
fun bootstrapAdmin(config: AdminConfig, accounts: AccountRepository, log: (String) -> Unit) {
    val email = config.bootstrapEmail?.trim()?.lowercase() ?: return
    val user = accounts.userByEmail(email)
    if (user == null) {
        log("admin bootstrap: no account for $email — register it first, then restart")
        return
    }
    if (user.isAdmin) return
    accounts.addOrUpdateUser(user.copy(role = User.ROLE_ADMIN))
    log("admin bootstrap: promoted $email to admin")
}

/** The signed-in admin, or null. Never trusts the session alone — the role is re-read. */
fun ApplicationCall.adminUser(accounts: AccountRepository): User? {
    val session = sessions.get<AdminSession>() ?: return null
    val user = accounts.userById(session.userId) ?: return null
    return user.takeIf { it.isAdmin && !it.isBanned }
}

suspend fun ApplicationCall.requireAdmin(accounts: AccountRepository): User? {
    val user = adminUser(accounts)
    if (user == null) respondRedirect("/admin/login")
    return user
}

/** Rejects a form whose CSRF token does not match the session's. */
suspend fun ApplicationCall.checkCsrf(submitted: String?): Boolean {
    val expected = sessions.get<AdminSession>()?.csrf
    if (expected == null || submitted == null || submitted != expected) {
        respondText("Invalid CSRF token", status = HttpStatusCode.Forbidden)
        return false
    }
    return true
}

fun newCsrfToken(): String = hex(ByteArray(24).also { SecureRandom().nextBytes(it) })
