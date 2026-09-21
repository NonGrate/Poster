package com.example.poster.admin

import com.example.poster.config.Features
import com.example.poster.comments.CommentsRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.server.application.call
import com.example.poster.auth.AttemptThrottle
import com.example.poster.auth.PasswordHasher
import com.example.poster.model.AccountRepository
import com.example.poster.model.Group
import com.example.poster.model.GroupRepository
import com.example.poster.model.UserGroupRepository
import com.example.poster.model.ModerationRepository
import com.example.poster.model.AppEventSeverity
import com.example.poster.model.EventRepository
import com.example.poster.model.FeedbackRepository
import com.example.poster.model.ReportsRepository
import com.example.poster.model.User
import kotlinx.html.*
import com.example.poster.model.CrashRepository
import com.example.poster.model.TagLocalRepository

/**
 * The moderation panel. Server-rendered, no build step, served by the same
 * process as the API — the whole thing is HTML plus form posts.
 *
 * Every route re-reads the user's role from the database rather than trusting
 * the cookie, so revoking admin takes effect on the next request.
 */
fun Route.adminRoutes(
    accounts: AccountRepository,
    moderation: ModerationRepository,
    hasher: PasswordHasher,
    groups: GroupRepository,
    tags: TagLocalRepository,
    memberships: UserGroupRepository,
    // Signing someone out everywhere. A new password is worth nothing while the
    // sessions opened with the old one keep working, so this has no default:
    // a caller has to say what it does.
    revokeSessions: (userId: String) -> Unit,
    /**
     * The same allowance /auth/login spends. A separate one here would be a
     * second five guesses per address against the very accounts that can ban
     * people — and this form takes an ordinary account's password.
     */
    throttle: AttemptThrottle,
    crashes: CrashRepository,
    reports: ReportsRepository,
    feedback: FeedbackRepository,
    events: EventRepository,
    comments: CommentsRepository,
    /** After the panel adds somebody to a group: (member, group, admin). The notifier listens. */
    onMemberAdded: (userId: String, group: Group, actor: String) -> Unit = { _, _, _ -> },
) {
    route("/admin") {
        get("/login") {
            call.respondHtml { loginPage(error = null) }
        }

        post("/login") {
            val params = call.receiveParameters()
            val email = params["email"]?.trim()?.lowercase().orEmpty()
            val password = params["password"].orEmpty()
            // Unthrottled, this form was the way around /auth/login's limit
            // against exactly the accounts worth guessing at.
            if (throttle.retryAfter(email) != null) {
                call.respondHtml(HttpStatusCode.TooManyRequests) {
                    loginPage(error = "Too many attempts. Try again later.")
                }
                return@post
            }
            val user = accounts.userByEmail(email)

            // One message for every failure: a distinct "no such account" reply
            // would confirm which addresses exist.
            val ok = user != null &&
                user.isAdmin &&
                !user.isBanned &&
                hasher.verify(password, user.passwordHash)
            if (!ok) {
                throttle.recordFailure(email)
                call.respondHtml(HttpStatusCode.Unauthorized) { loginPage(error = "Invalid credentials") }
                return@post
            }
            throttle.clear(email)
            call.sessions.set(AdminSession(userId = user!!.guid, csrf = newCsrfToken()))
            call.respondRedirect("/admin")
        }

        post("/logout") {
            call.sessions.clear<AdminSession>()
            call.respondRedirect("/admin/login")
        }

        /**
         * Numbers first, and the ones that need a decision first among them.
         *
         * The rest of the panel is lists to work through; this is the page that
         * says whether anything needs working through at all. Anything with a
         * count above zero that wants attention is coloured; everything else is
         * a number somebody might be curious about.
         */
        get {
            val admin = call.requireAdmin(accounts) ?: return@get
            val users = moderation.allUsersIncludingBanned()
            val posts = moderation.allPostsIncludingDeleted()
            val waiting = reports.pendingPosts()
            val unconfirmed = users.count { it.verifiedAt == null && !it.isBanned }
            val recentCrashes = crashes.recent(limit = 200).size
            val misbehaviours = events.warnCount()
            call.respondHtml {
                page("Overview", admin, waiting) {
                    div("cards") {
                        // Wants a person. Coloured only when there is something
                        // to do, so colour keeps meaning something.
                        if (Features.REPORTS) statCard("Reports waiting", waiting, "/admin/reports", urgent = waiting > 0)
                        if (Features.CRASH_REPORTS) statCard("Crashes", recentCrashes, "/admin/crashes", urgent = recentCrashes > 0)
                        if (Features.TELEMETRY) statCard("Misbehaviours", misbehaviours, "/admin/events", urgent = misbehaviours > 0)
                        statCard("Unconfirmed accounts", unconfirmed, "/admin/users", urgent = false)
                    }
                    h2 { +"Where things stand" }
                    div("cards") {
                        statCard("People", users.count { !it.isBanned }, "/admin/users")
                        statCard("Banned", users.count { it.isBanned }, "/admin/users")
                        statCard("Posts", posts.count { !it.second }, "/admin/posts")
                        statCard("Hidden posts", posts.count { it.second }, "/admin/posts")
                        if (Features.GROUPS) statCard("Groups", groups.allGroups().size, "/admin/groups")
                        if (Features.TAGS) statCard("Tags", tags.allTags().size, "/admin/tags")
                    }
                    h2 { +"Everything else" }
                    div("links") {
                        a(href = "/admin/audit") { +"Audit log — every moderation action, and who did it" }
                    }
                }
            }
        }

        // The tables about what people wrote and who wrote it.
        adminPostsPages(accounts, moderation, hasher, groups, reports, comments, revokeSessions)

        if (Features.FEEDBACK) get("/feedback") {
            val admin = call.requireAdmin(accounts) ?: return@get
            val csrf = call.sessions.get<AdminSession>()!!.csrf
            call.respondHtml {
                page("Feedback", admin, reports.pendingPosts()) {
                    val all = feedback.all()
                    if (all.isEmpty()) {
                        div("empty") { +"Nobody has sent feedback yet." }
                        return@page
                    }
                    p("hint") {
                        +("What people asked for or ran into. Reply and it is shown to them "
                            + "the next time they open the feedback screen — there is no push.")
                    }
                    table {
                        tr {
                            th { +"From" }; th { +"Sent" }; th { +"Message" }; th { +"State" }; th { +"Reply" }
                        }
                        all.forEach { item ->
                            tr(classes = if (item.answered) "muted" else "") {
                                td {
                                    expandableCell(item.senderName.ifBlank { item.senderEmail }) {
                                        p("message") { +item.message }
                                        dl {
                                            field("Email", item.senderEmail)
                                            field("Sent", item.createdAt)
                                            field("State", if (item.answered) "answered" else "open")
                                            field("Reply", item.response)
                                            field("Id", item.id)
                                        }
                                    }
                                }
                                td { +item.createdAt.take(10) }
                                td { div("excerpt") { +item.message } }
                                td {
                                    if (item.answered) span("badge hidden") { +"answered" }
                                    else span("badge count") { +"open" }
                                }
                                td("actions") {
                                    item.response?.let { div("reason") { +"“$it”" } }
                                    form(action = "/admin/feedback/${item.id}/respond", method = FormMethod.post) {
                                        hiddenInput(name = "csrf") { value = csrf }
                                        textArea {
                                            name = "response"
                                            attributes["rows"] = "2"
                                            attributes["required"] = "required"
                                            +(item.response ?: "")
                                        }
                                        submitInput { value = if (item.answered) "Update reply" else "Reply" }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        /** The developer's reply. Flips it to answered; shown on the sender's next open. */
        if (Features.FEEDBACK) post("/feedback/{id}/respond") {
            val admin = call.requireAdmin(accounts) ?: return@post
            val params = call.receiveParameters()
            if (!call.checkCsrf(params["csrf"])) return@post
            val response = params["response"]?.trim().orEmpty()
            if (response.isNotEmpty()) {
                feedback.respond(call.parameters["id"].orEmpty(), response)
            }
            call.respondRedirect("/admin/feedback")
        }

        adminTagsPages(accounts, moderation, tags)

        adminGroupsPages(accounts, moderation, groups, memberships, onMemberAdded)

        /**
         * What the apps have crashed with.
         *
         * Newest first, and nothing here says who it happened to: a fault
         * report says what broke, not who was holding the phone.
         */
        if (Features.CRASH_REPORTS) get("/crashes") {
            val admin = call.requireAdmin(accounts) ?: return@get
            val reports = crashes.recent()
            call.respondHtml {
                page("Crashes", admin) {
                    if (reports.isEmpty()) {
                        p { +"Nothing has crashed, or nothing has been able to tell us." }
                    }
                    reports.forEach { report ->
                        details {
                            summary { strong { +"${report.type} — ${report.platform}" } }
                            div("detail") {
                                p {
                                    +("${report.device}, ${report.osVersion}, app ${report.appVersion}")
                                    br()
                                    +"happened ${report.occurredAt}, received ${report.receivedAt}"
                                }
                                report.message?.takeIf { it.isNotBlank() }?.let { p { +it } }
                                pre { code { +report.stack } }
                            }
                        }
                    }
                }
            }
        }

        if (Features.TELEMETRY) get("/events") {
            val admin = call.requireAdmin(accounts) ?: return@get
            val all = events.recent()
            val users = moderation.allUsersIncludingBanned().associateBy { it.guid }
            call.respondHtml {
                page("Events", admin) {
                    if (all.isEmpty()) {
                        div("empty") { +"No events yet." }
                        return@page
                    }
                    p("hint") {
                        +("Diagnostic events, grouped by who they came from. A "
                            + "misbehaviour (warn) is a failure or a timeout worth a look; "
                            + "info is a slow-but-fine operation. Anonymous events are from "
                            + "before sign-in, grouped by device.")
                    }
                    // Grouped per user so one person's story reads top to bottom;
                    // anonymous events fall together under their own heading.
                    // Groups ordered by their most recent event.
                    all.groupBy { it.userId }
                        .entries
                        .sortedByDescending { group -> group.value.maxOf { it.receivedAt ?: it.occurredAt } }
                        .forEach { (userId, group) ->
                            val who = userId?.let { users[it]?.email ?: it } ?: "anonymous"
                            val warns = group.count { it.severity == AppEventSeverity.WARN }
                            h2 {
                                +who
                                if (warns > 0) span("badge count") { +"$warns warn" }
                            }
                            table {
                                tr {
                                    th { +"When" }; th { +"Event" }; th { +"Severity" }
                                    th { +"Detail" }; th { +"Device" }; th { +"Build" }
                                }
                                group.forEach { event ->
                                    tr(classes = if (event.severity == AppEventSeverity.WARN) "" else "muted") {
                                        td { +(event.receivedAt ?: event.occurredAt) }
                                        td {
                                            expandableCell(event.name) {
                                                dl {
                                                    field("When", event.receivedAt ?: event.occurredAt)
                                                    field("Occurred", event.occurredAt)
                                                    field("Severity", if (event.severity == AppEventSeverity.WARN) "warn" else "info")
                                                    field("Detail", event.detail)
                                                    field("Device", "${event.platform} ${event.deviceId}")
                                                    field("Build", event.appVersion)
                                                    field("User", userId?.let { users[it]?.email ?: it } ?: "anonymous")
                                                }
                                            }
                                        }
                                        td {
                                            if (event.severity == AppEventSeverity.WARN) {
                                                span("badge hidden") { +"warn" }
                                            } else {
                                                span("badge live") { +"info" }
                                            }
                                        }
                                        td { +(event.detail ?: "—") }
                                        td { +"${event.platform} ${event.deviceId.take(8)}" }
                                        td { +event.appVersion }
                                    }
                                }
                            }
                        }
                }
            }
        }

        get("/audit") {
            val admin = call.requireAdmin(accounts) ?: return@get
            val users = moderation.allUsersIncludingBanned().associateBy { it.guid }
            call.respondHtml {
                page("Audit log", admin) {
                    table {
                        tr { th { +"When" }; th { +"Who" }; th { +"Action" }; th { +"Target" }; th { +"Reason" } }
                        moderation.recentAudit().forEach { entry ->
                            tr {
                                td { +entry.createdAt }
                                td { +(users[entry.actorId]?.email ?: entry.actorId) }
                                td { +entry.action }
                                td { +"${entry.targetType} ${entry.targetId.take(8)}" }
                                td { +(entry.reason ?: "—") }
                            }
                        }
                    }
                }
            }
        }
    }
}
