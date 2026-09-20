package com.example.poster.admin

import com.example.poster.config.Features
import com.example.poster.comments.CommentsRepository
import com.example.poster.config.AppInfo
import io.ktor.http.HttpStatusCode
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
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
import com.example.poster.INVITE_ALPHABET
import com.example.poster.INVITE_CODE_LENGTH
import com.example.poster.model.AccountRepository
import com.example.poster.newInviteCode
import com.example.poster.model.Group
import com.example.poster.model.GroupRepository
import com.example.poster.model.UserGroupRepository
import com.example.poster.model.ModerationRepository
import com.example.poster.model.PostVisibility
import com.example.poster.model.AppEventSeverity
import com.example.poster.model.EventRepository
import com.example.poster.model.FeedbackRepository
import com.example.poster.model.ReportsRepository
import com.example.poster.model.User
import kotlinx.html.*
import com.example.poster.model.CrashRepository
import com.example.poster.model.Language
import com.example.poster.model.Tag
import com.example.poster.model.TAG_GROUPS
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

        get("/users") {
            val admin = call.requireAdmin(accounts) ?: return@get
            val csrf = call.sessions.get<AdminSession>()!!.csrf
            val users = moderation.allUsersIncludingBanned().sortedBy { it.email }
            call.respondHtml {
                page("Users", admin, reports.pendingPosts()) {
                    table {
                        tr {
                            th { +"Email" }; th { +"Name" }; th { +"Role" }; th { +"Status" }
                            th { +"Confirmed" }; th { +"Reads" }; th { +"Actions" }
                        }
                        users.forEach { u ->
                            tr {
                                td {
                                    expandableCell(u.email) {
                                        dl {
                                            field("Name", u.fullName())
                                            field("Guid", u.guid)
                                            field("Role", u.role)
                                            field("Status", if (u.isBanned) "banned — ${u.status}" else "active")
                                            field("Confirmed", u.verifiedAt ?: "not confirmed")
                                            field("Reads", u.languages.joinToString(", ") { Language.nameOf(it) })
                                            field("Writes in", Language.nameOf(u.defaultLanguage))
                                            field("Shows name", if (u.showName) "yes" else "no")
                                        }
                                    }
                                }
                                td { +u.fullName() }
                                td { +u.role }
                                td { +(if (u.isBanned) "banned — ${u.status}" else "active") }
                                td {
                                    if (u.verifiedAt != null) {
                                        +"confirmed"
                                    } else {
                                        +"not confirmed"
                                        br()
                                        // The way through when the email does
                                        // not arrive, which is the common case
                                        // for a domain nobody has heard of yet.
                                        form(
                                            action = "/admin/users/${u.guid}/verify",
                                            method = FormMethod.post,
                                        ) {
                                            hiddenInput(name = "csrf") { value = csrf }
                                            submitInput { value = "Confirm by hand" }
                                        }
                                    }
                                }
                                // Read-only: which languages somebody wants to
                                // read is theirs to choose, and shown here only
                                // because "why can they not see it" is the first
                                // question a missing post raises.
                                td {
                                    +u.languages.joinToString(", ") { Language.nameOf(it) }
                                    if (u.languages.size > 1) {
                                        br()
                                        +"writes in ${Language.nameOf(u.defaultLanguage)}"
                                    }
                                }
                                td {
                                    if (u.guid == admin.guid) {
                                        +"— you —"
                                    } else if (u.isBanned) {
                                        form(action = "/admin/users/${u.guid}/unban", method = FormMethod.post) {
                                            hiddenInput(name = "csrf") { value = csrf }
                                            submitInput { value = "Unban" }
                                        }
                                    } else {
                                        form(action = "/admin/users/${u.guid}/ban", method = FormMethod.post) {
                                            hiddenInput(name = "csrf") { value = csrf }
                                            textInput(name = "reason") { placeholder = "reason (required)"; required = true }
                                            submitInput { value = "Ban" }
                                        }
                                    }
                                    // Available for every account, including your
                                    // own and a banned one: forgetting a password
                                    // has nothing to do with either.
                                    form(action = "/admin/users/${u.guid}/reset-password", method = FormMethod.post) {
                                        hiddenInput(name = "csrf") { value = csrf }
                                        submitInput { value = "Reset password" }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        post("/users/{id}/ban") {
            val admin = call.requireAdmin(accounts) ?: return@post
            val params = call.receiveParameters()
            if (!call.checkCsrf(params["csrf"])) return@post
            val id = call.parameters["id"].orEmpty()
            val reason = params["reason"]?.trim().orEmpty()
            when {
                id == admin.guid ->
                    // Banning yourself would lock the panel with no way back in.
                    call.respondText("You cannot ban yourself", status = HttpStatusCode.BadRequest)
                reason.isBlank() ->
                    call.respondText("A reason is required", status = HttpStatusCode.BadRequest)
                else -> {
                    moderation.ban(admin.guid, id, reason)
                    call.respondRedirect("/admin/users")
                }
            }
        }

        /**
         * Gives somebody a way back in when they have forgotten their password.
         *
         * There is no email on this server, so everyday is a phone call: the new
         * password is generated here, shown once, and read out. It is never
         * logged, never stored in the clear, and never shown again — a second
         * reset is one more button press, which is cheaper than keeping it
         * somewhere it could be read later.
         */
        post("/users/{id}/reset-password") {
            val admin = call.requireAdmin(accounts) ?: return@post
            if (!call.checkCsrf(call.receiveParameters()["csrf"])) return@post
            val user = accounts.userById(call.parameters["id"].orEmpty())
            if (user == null) {
                call.respondText("No such account", status = HttpStatusCode.NotFound)
                return@post
            }

            val password = TemporaryPassword.generate()
            accounts.addOrUpdateUser(user.copy(passwordHash = hasher.hash(password)))
            // Otherwise whoever is holding an old session keeps it.
            revokeSessions(user.guid)
            moderation.recordPasswordReset(admin.guid, user.guid)

            call.respondHtml {
                page("Password reset", admin) {
                    p { +"New password for ${user.email}:" }
                    p { code { +password } }
                    p {
                        +("Shown once — read it out now. The app has no way to "
                            + "change a password yet, so this stays theirs until "
                            + "it is reset again from here.")
                    }
                    a(href = "/admin/users") { +"Back to users" }
                }
            }
        }

        /** Confirming an address for somebody the email never reached. */
        post("/users/{id}/verify") {
            val admin = call.requireAdmin(accounts) ?: return@post
            if (!call.checkCsrf(call.receiveParameters()["csrf"])) return@post
            moderation.markVerified(
                actorId = admin.guid,
                userId = call.parameters["id"].orEmpty(),
                at = kotlinx.datetime.Clock.System.now().toString(),
            )
            call.respondRedirect("/admin/users")
        }

        post("/users/{id}/unban") {
            val admin = call.requireAdmin(accounts) ?: return@post
            if (!call.checkCsrf(call.receiveParameters()["csrf"])) return@post
            moderation.unban(admin.guid, call.parameters["id"].orEmpty())
            call.respondRedirect("/admin/users")
        }

        /**
         * What people have objected to, and what to do about it.
         *
         * Grouped by post rather than listed per report: five reports of one
         * post are one decision. Hiding is the existing soft delete, so it
         * lands in the audit log beside every other moderation action and can
         * be undone from the posts page.
         */
        if (Features.REPORTS) get("/reports") {
            val admin = call.requireAdmin(accounts) ?: return@get
            val csrf = call.sessions.get<AdminSession>()!!.csrf
            val authors = moderation.allUsersIncludingBanned().associateBy { it.guid }
            // The report row lists only a title and excerpt; the full post (tags,
            // group, like count, answer) is joined in for the expandable panel.
            val postsById = moderation.allPostsIncludingDeleted().associate { it.first.guid to it.first }
            call.respondHtml {
                page("Reports", admin, reports.pendingPosts()) {
                    val reported = reports.reported()
                    if (reported.isEmpty()) {
                        div("empty") { +"Nothing has been reported." }
                        return@page
                    }
                    p("hint") {
                        +("A report is somebody saying this should not be here. It hides nothing "
                            + "on its own — in a small community app the usual reason a post looks wrong "
                            + "is that somebody misread it.")
                    }
                    table {
                        tr {
                            th { +"Post" }; th { +"Author" }; th { +"Reports" }
                            th { +"Reasons given" }; th { +"State" }; th { +"Actions" }
                        }
                        reported.forEach { item ->
                            tr(classes = if (item.hidden) "muted" else "") {
                                td {
                                    // Native <details>: the whole post without a
                                    // second page or any script.
                                    details {
                                        summary { strong { +item.title } }
                                        div("detail") {
                                            p("message") { +item.message }
                                            dl {
                                                val full = postsById[item.postId]
                                                val author = authors[item.author]
                                                dt { +"Author" }
                                                dd { +(author?.let { "${it.name} ${it.surname} <${it.email}>" } ?: item.author) }
                                                if (full != null) {
                                                    full.group?.let { cid ->
                                                        dt { +"Group" }
                                                        dd { +(groups.groupById(cid)?.name ?: cid) }
                                                    }
                                                    dt { +"Visibility" }; dd { +full.visibility }
                                                    dt { +"Language" }; dd { +full.language }
                                                    if (full.tags.isNotEmpty()) { dt { +"Tags" }; dd { +full.tags.joinToString(", ") } }
                                                    dt { +"Liked" }; dd { +full.likes.toString() }
                                                    dt { +"Created" }; dd { +full.date.toString() }
                                                    full.completedAt?.let {
                                                        dt { +"Answered" }; dd { +(full.completionMessage ?: "yes") }
                                                    }
                                                }
                                                dt { +"Post id" }; dd { +item.postId }
                                                dt { +"Last reported" }; dd { +item.lastReported }
                                            }
                                        }
                                    }
                                }
                                td { +(authors[item.author]?.email ?: item.author) }
                                td { span("badge count") { +item.reportCount.toString() } }
                                td {
                                    if (item.reasons.isEmpty()) {
                                        span("quiet") { +"none given" }
                                    } else {
                                        item.reasons.forEach { reason -> div("reason") { +"“$reason”" } }
                                    }
                                }
                                td {
                                    if (item.hidden) span("badge hidden") { +"hidden" }
                                    else span("badge live") { +item.visibility }
                                }
                                td("actions") {
                                    if (!item.hidden) {
                                        form(action = "/admin/reports/${item.postId}/hide", method = FormMethod.post) {
                                            hiddenInput(name = "csrf") { value = csrf }
                                            submitInput(classes = "danger") { value = "Hide post" }
                                        }
                                    }
                                    form(action = "/admin/reports/${item.postId}/dismiss", method = FormMethod.post) {
                                        hiddenInput(name = "csrf") { value = csrf }
                                        submitInput { value = "Dismiss" }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        /** Hides it and clears the reports: the decision has been made. */
        if (Features.REPORTS) post("/reports/{id}/hide") {
            val admin = call.requireAdmin(accounts) ?: return@post
            val params = call.receiveParameters()
            if (!call.checkCsrf(params["csrf"])) return@post
            val postId = call.parameters["id"].orEmpty()
            moderation.softDeletePost(admin.guid, postId, "reported")
            reports.dismissFor(postId)
            call.respondRedirect("/admin/reports")
        }

        /** Looked at, and it is fine. The pile has to be able to shrink. */
        if (Features.REPORTS) post("/reports/{id}/dismiss") {
            val admin = call.requireAdmin(accounts) ?: return@post
            val params = call.receiveParameters()
            if (!call.checkCsrf(params["csrf"])) return@post
            reports.dismissFor(call.parameters["id"].orEmpty())
            call.respondRedirect("/admin/reports")
        }

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

        get("/posts") {
            val admin = call.requireAdmin(accounts) ?: return@get
            val csrf = call.sessions.get<AdminSession>()!!.csrf
            val authors = moderation.allUsersIncludingBanned().associateBy { it.guid }
            val groupNames = groups.allGroups().associate { it.id to it.name }
            call.respondHtml {
                page("Posts", admin, reports.pendingPosts()) {
                    p {
                        +("Everything written before posts had a language says English. "
                            + "Whoever wrote a post chooses its language; this is here to "
                            + "correct the ones that predate the choice.")
                    }
                    table {
                        tr {
                            th { +"Post" }; th { +"Author" }; th { +"Who sees it" }
                            th { +"Tags" }; th { +"State" }
                            th { +"Language" }; th { +"Actions" }
                        }
                        moderation.allPostsIncludingDeleted().forEach { (post, deleted) ->
                            tr {
                                // The words as well as the title: a title alone
                                // says too little to moderate on, and the text
                                // is the thing a report is usually about.
                                td {
                                    expandableCell(post.title) {
                                        p("message") { +post.message }
                                        dl {
                                            field("Author", authors[post.author]?.let { "${it.name} ${it.surname} <${it.email}>" } ?: post.author)
                                            post.group?.let { field("Group", groupNames[it] ?: it) }
                                            field("Visibility", post.visibility)
                                            field("Language", post.language)
                                            field("Tags", post.tags.joinToString(", ").ifBlank { null })
                                            field("Liked", post.likes.toString())
                                            field("Created", post.date.toString())
                                            if (post.completedAt != null) field("Answered", post.completionMessage ?: "yes")
                                            field("Post id", post.guid)
                                        }
                                    }
                                }
                                td { +(authors[post.author]?.email ?: post.author) }
                                // What a post is worth knowing before acting on
                                // it: a private one was never meant to be read by
                                // the room, and a group one belongs to a room
                                // that has a name.
                                td {
                                    +when (post.visibility) {
                                        PostVisibility.PRIVATE -> "private"
                                        PostVisibility.GROUP -> "group"
                                        else -> "public"
                                    }
                                    post.group?.let { id ->
                                        br(); +(groupNames[id] ?: id)
                                    }
                                }
                                td {
                                    if (post.tags.isEmpty()) +"—" else +post.tags.joinToString(", ")
                                }
                                td {
                                    +(if (deleted) "deleted" else "visible")
                                    // A completion message is user-authored text like the
                                    // post itself, so it has to be readable here.
                                    if (post.completedAt != null) {
                                        br()
                                        +"answered"
                                        post.completionMessage?.takeIf { it.isNotBlank() }?.let {
                                            br(); +"“$it”"
                                        }
                                    }
                                }
                                td {
                                    form(
                                        action = "/admin/posts/${post.guid}/language",
                                        method = FormMethod.post,
                                    ) {
                                        hiddenInput(name = "csrf") { value = csrf }
                                        select {
                                            name = "language"
                                            Language.ALL.forEach { code ->
                                                option {
                                                    value = code
                                                    selected = code == post.language
                                                    +Language.nameOf(code)
                                                }
                                            }
                                        }
                                        submitInput { value = "Save" }
                                    }
                                }
                                td {
                                    val action = if (deleted) "restore" else "delete"
                                    form(action = "/admin/posts/${post.guid}/$action", method = FormMethod.post) {
                                        hiddenInput(name = "csrf") { value = csrf }
                                        if (!deleted) {
                                            textInput(name = "reason") { placeholder = "reason" }
                                        }
                                        submitInput { value = action.replaceFirstChar { it.uppercase() } }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        /**
         * Correcting what a post is written in.
         *
         * Only the language: the words are whoever wrote them, and a panel that
         * can edit a post's text is a panel that can put words in somebody's
         * mouth. Audited like every other action here.
         */
        post("/posts/{id}/language") {
            val admin = call.requireAdmin(accounts) ?: return@post
            val params = call.receiveParameters()
            if (!call.checkCsrf(params["csrf"])) return@post
            val language = params["language"].orEmpty()
            if (!Language.isKnown(language)) {
                call.respondText("Unknown language", status = HttpStatusCode.BadRequest)
                return@post
            }
            moderation.setPostLanguage(admin.guid, call.parameters["id"].orEmpty(), language)
            call.respondRedirect("/admin/posts")
        }

        post("/posts/{id}/delete") {
            val admin = call.requireAdmin(accounts) ?: return@post
            val params = call.receiveParameters()
            if (!call.checkCsrf(params["csrf"])) return@post
            moderation.softDeletePost(admin.guid, call.parameters["id"].orEmpty(), params["reason"]?.trim())
            call.respondRedirect("/admin/posts")
        }

        post("/posts/{id}/restore") {
            val admin = call.requireAdmin(accounts) ?: return@post
            if (!call.checkCsrf(call.receiveParameters()["csrf"])) return@post
            moderation.restorePost(admin.guid, call.parameters["id"].orEmpty())
            call.respondRedirect("/admin/posts")
        }

        if (Features.COMMENTS) get("/comments") {
            val admin = call.requireAdmin(accounts) ?: return@get
            val csrf = call.sessions.get<AdminSession>()!!.csrf
            call.respondHtml {
                page("Comments", admin, reports.pendingPosts()) {
                    p { +"The newest 500 comments, hidden ones included. Hiding takes a comment off the post for everybody; the words stay here." }
                    table {
                        tr { th { +"Comment" }; th { +"Author" }; th { +"On" }; th { +"When" }; th { +"State" }; th { +"Action" } }
                        comments.allIncludingHidden().forEach { entry ->
                            tr {
                                td { expandableCell(entry.comment.text.take(80)) { p("message") { +entry.comment.text }; dl { field("Comment id", entry.comment.guid) } } }
                                td { +entry.authorEmail }
                                td { +entry.postTitle }
                                td { +entry.comment.createdAt.take(16).replace('T', ' ') }
                                td { +(if (entry.hidden) "hidden" else "visible") }
                                td {
                                    val action = if (entry.hidden) "restore" else "hide"
                                    form(action = "/admin/comments/${entry.comment.guid}/$action", method = FormMethod.post) {
                                        hiddenInput(name = "csrf") { value = csrf }
                                        if (!entry.hidden) textInput(name = "reason") { placeholder = "reason" }
                                        submitInput { value = action.replaceFirstChar { it.uppercase() } }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (Features.COMMENTS) post("/comments/{id}/hide") {
            val admin = call.requireAdmin(accounts) ?: return@post
            val params = call.receiveParameters()
            if (!call.checkCsrf(params["csrf"])) return@post
            val id = call.parameters["id"].orEmpty()
            comments.hide(id)
            moderation.recordCommentAction(admin.guid, "hide", id, params["reason"]?.trim())
            call.respondRedirect("/admin/comments")
        }

        if (Features.COMMENTS) post("/comments/{id}/restore") {
            val admin = call.requireAdmin(accounts) ?: return@post
            if (!call.checkCsrf(call.receiveParameters()["csrf"])) return@post
            val id = call.parameters["id"].orEmpty()
            comments.restore(id)
            moderation.recordCommentAction(admin.guid, "restore", id, null)
            call.respondRedirect("/admin/comments")
        }

        if (Features.TAGS) get("/tags") {
            val admin = call.requireAdmin(accounts) ?: return@get
            val csrf = call.sessions.get<AdminSession>()!!.csrf
            val authors = moderation.allUsersIncludingBanned().associateBy { it.guid }
            call.respondHtml {
                page("Tags", admin) {
                    p {
                        +("The id is what a post stores and what a filter matches, and it "
                            + "never changes. The labels are what people read.")
                    }
                    p {
                        +("A tag belongs to one group, which is the only reason 60 of them "
                            + "fit in a picker: it shows six groups, and one group's tags. "
                            + "A tag with no group still appears, under its own heading — "
                            + "unfiled is untidy, unpickable would be a bug.")
                    }
                    p {
                        +("\"On posts\" is how many carry the tag today. Deleting a tag "
                            + "takes it off every one of them, and there is no way to put it "
                            + "back — so a tag in use is better moved onto a group than "
                            + "deleted, unless losing it is the point.")
                    }
                    h2 { +"Add a tag" }
                    form(action = "/admin/tags", method = FormMethod.post) {
                        hiddenInput(name = "csrf") { value = csrf }
                        textInput(name = "id") { placeholder = "id (health)" }
                        textInput(name = "label_en") { placeholder = "English" }
                        textInput(name = "label_ru") { placeholder = "Русский" }
                        groupSelect(null)
                        submitInput { value = "Add" }
                    }
                    // The list somebody needs before deleting anything: every
                    // tag that is on a post but on no shelf, and which posts
                    // those are. After the curated set was cut these are exactly
                    // the tags that went, plus anything typed by hand back when
                    // the field was free text. Retag those posts in the app
                    // first, or deleting the tag takes the only tag they have.
                    val allTags = tags.allTags()
                    val strays = allTags.filter { it.group == null }.map { it.guid }.toSet()
                    if (strays.isNotEmpty()) {
                        val affected = moderation.allPostsIncludingDeleted()
                            .filter { (post, deleted) ->
                                !deleted && post.tags.any { it in strays }
                            }
                        if (affected.isNotEmpty()) {
                            h2 { +"On a post, but on no shelf" }
                            p {
                                +("These posts carry a tag that is not in any group. "
                                    + "Deleting such a tag takes it off the post for good, "
                                    + "so change the post's tags first — or move the tag "
                                    + "onto a group and keep it.")
                            }
                            table {
                                tr { th { +"Post" }; th { +"Author" }; th { +"Tags with no shelf" } }
                                affected.forEach { (post, _) ->
                                    tr {
                                        td { +post.title }
                                        td { +(authors[post.author]?.email ?: post.author) }
                                        td {
                                            +post.tags.filter { it in strays }.joinToString(", ")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Offered under the list of what it touches, not above it:
                    // the whole reason this is safe to press is the section
                    // before it, and a button somebody meets first is a button
                    // pressed without reading.
                    val unfiled = allTags.filter { it.group == null }
                    if (unfiled.isNotEmpty()) {
                        h2 { +"Delete every tag with no group" }
                        p {
                            +("${unfiled.size} tag${if (unfiled.size == 1) "" else "s"} sit on no "
                                + "shelf. This deletes all of them and takes them off any post "
                                + "above. Tags in a group are never touched — being on a shelf is "
                                + "what \"we meant to keep this\" means. There is no undo.")
                        }
                        form(action = "/admin/tags/unfiled/delete", method = FormMethod.post) {
                            hiddenInput(name = "csrf") { value = csrf }
                            // Typed, not clicked. The count is on screen twice
                            // and has to be copied, which is the pause.
                            textInput(name = "confirm") { placeholder = "type ${unfiled.size} to confirm" }
                            submitInput { value = "Delete ${unfiled.size} tags" }
                        }
                    }

                    h2 { +"Existing" }
                    val usage = tags.usageCounts()
                    table {
                        tr {
                            th { +"id" }; th { +"English" }; th { +"Русский" }
                            th { +"On posts" }; th { +"Group" }; th { +"" }
                        }
                        // Grouped, then alphabetical inside the group: this page
                        // is where somebody notices a tag is filed wrong, and a
                        // flat alphabetical list is exactly where they would not.
                        val byGroup = allTags.sortedBy { it.name }.groupBy { it.group }
                        val order = TAG_GROUPS.map { it.id } + listOf(null)
                        order.forEach { groupId ->
                            val inGroup = byGroup[groupId].orEmpty()
                            if (inGroup.isEmpty()) return@forEach
                            tr {
                                td {
                                    attributes["colspan"] = "6"
                                    strong {
                                        +(TAG_GROUPS.firstOrNull { it.id == groupId }?.english
                                            ?: "Not in any group")
                                    }
                                    +" · ${inGroup.size}"
                                }
                            }
                            inGroup.forEach { tag ->
                                tr {
                                    td { code { +tag.name } }
                                    td {
                                        form(action = "/admin/tags/${tag.guid}", method = FormMethod.post) {
                                            hiddenInput(name = "csrf") { value = csrf }
                                            textInput(name = "label_en") { value = tag.labelEn ?: "" }
                                            textInput(name = "label_ru") { value = tag.labelRu ?: "" }
                                            submitInput { value = "Save" }
                                        }
                                    }
                                    // How many posts lose this tag if it is
                                    // deleted. The number is the whole reason to
                                    // hesitate, so it sits next to the button.
                                    td {
                                        val used = usage[tag.guid] ?: 0
                                        if (used == 0) +"—" else strong { +used.toString() }
                                    }
                                    td {
                                        form(action = "/admin/tags/${tag.guid}/group", method = FormMethod.post) {
                                            hiddenInput(name = "csrf") { value = csrf }
                                            groupSelect(tag.group)
                                            submitInput { value = "Move" }
                                        }
                                    }
                                    td {
                                        form(action = "/admin/tags/${tag.guid}/delete", method = FormMethod.post) {
                                            hiddenInput(name = "csrf") { value = csrf }
                                            submitInput { value = "Delete" }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (Features.TAGS) post("/tags") {
            val admin = call.requireAdmin(accounts) ?: return@post
            val params = call.receiveParameters()
            if (!call.checkCsrf(params["csrf"])) return@post
            val id = params["id"]?.trim()?.lowercase().orEmpty()
            if (id.isNotEmpty()) {
                tags.addOrUpdateTag(Tag(guid = id, name = id))
                tags.updateLabels(id, params["label_en"]?.trim(), params["label_ru"]?.trim())
                tags.updateGroup(id, params["group"]?.takeIf { g -> TAG_GROUPS.any { it.id == g } })
                moderation.recordGroupAction(admin.guid, "tag:add", id, null)
            }
            call.respondRedirect("/admin/tags")
        }

        if (Features.TAGS) post("/tags/{id}") {
            val admin = call.requireAdmin(accounts) ?: return@post
            val params = call.receiveParameters()
            if (!call.checkCsrf(params["csrf"])) return@post
            val id = call.parameters["id"].orEmpty()
            tags.updateLabels(id, params["label_en"]?.trim(), params["label_ru"]?.trim())
            moderation.recordGroupAction(admin.guid, "tag:relabel", id, null)
            call.respondRedirect("/admin/tags")
        }

        /**
         * Moving a tag to another group, or off all of them.
         *
         * Its own route rather than a field on the label form: the two are
         * corrected at different moments — a label because the wording is off,
         * a group because somebody looked at the picker and the tag was on the
         * wrong shelf — and one form saving both means every filing change also
         * rewrites labels somebody may be midway through editing.
         */
        if (Features.TAGS) post("/tags/{id}/group") {
            val admin = call.requireAdmin(accounts) ?: return@post
            val params = call.receiveParameters()
            if (!call.checkCsrf(params["csrf"])) return@post
            val id = call.parameters["id"].orEmpty()
            val raw = params["group"].orEmpty()
            if (raw.isNotEmpty() && TAG_GROUPS.none { it.id == raw }) {
                call.respondText("Unknown group", status = HttpStatusCode.BadRequest)
                return@post
            }
            val group = raw.takeIf { it.isNotEmpty() }
            tags.updateGroup(id, group)
            moderation.recordGroupAction(admin.guid, "tag:group", id, group ?: "none")
            call.respondRedirect("/admin/tags")
        }

        /**
         * The tidy-up after cutting the curated set.
         *
         * Guarded by typing the count rather than by a confirm dialog: the
         * number is only on the page that also lists which posts are
         * affected, so copying it means having been there. A stale page — one
         * loaded before somebody filed a few — carries the old count and is
         * refused, which is the point.
         */
        if (Features.TAGS) post("/tags/unfiled/delete") {
            val admin = call.requireAdmin(accounts) ?: return@post
            val params = call.receiveParameters()
            if (!call.checkCsrf(params["csrf"])) return@post
            val expected = tags.allTags().count { it.group == null }
            if (params["confirm"]?.trim()?.toIntOrNull() != expected) {
                call.respondText(
                    "Type $expected to confirm. If that is not the number you saw, " +
                        "the page was out of date — go back and look again.",
                    status = HttpStatusCode.BadRequest,
                )
                return@post
            }
            val deleted = tags.deleteUnfiledTags()
            moderation.recordGroupAction(
                admin.guid, "tag:delete_unfiled", "tags", deleted.sorted().joinToString(", "),
            )
            call.respondRedirect("/admin/tags")
        }

        if (Features.TAGS) post("/tags/{id}/delete") {
            val admin = call.requireAdmin(accounts) ?: return@post
            if (!call.checkCsrf(call.receiveParameters()["csrf"])) return@post
            val id = call.parameters["id"].orEmpty()
            tags.deleteTag(id)
            moderation.recordGroupAction(admin.guid, "tag:delete", id, null)
            call.respondRedirect("/admin/tags")
        }

        if (Features.GROUPS) get("/groups") {
            val admin = call.requireAdmin(accounts) ?: return@get
            val csrf = call.sessions.get<AdminSession>()!!.csrf
            val all = groups.allGroups().sortedBy { it.name }
            val byId = accounts.allUsers().associateBy { it.guid }
            // Counted once for all of them rather than queried per row: this
            // page lists every group there is, and a query inside the loop
            // is a query per group.
            val postCounts = moderation.allPostsIncludingDeleted()
                .mapNotNull { (post, _) -> post.group }
                .groupingBy { it }
                .eachCount()
            val ownedCounts = all.mapNotNull { it.owner }.groupingBy { it }.eachCount()
            call.respondHtml {
                page("Groups", admin) {
                    h2 { +"Create" }
                    form(action = "/admin/groups", method = FormMethod.post) {
                        hiddenInput(name = "csrf") { value = csrf }
                        textInput(name = "name") { placeholder = "name"; required = true }
                        textInput(name = "inviteCode") { placeholder = "invite code (optional)" }
                        if (Features.PUBLIC_GROUPS) select {
                            name = "visibility"
                            option { value = "private"; +"invite-only" }
                            option { value = "public"; +"public (listed, anybody may join)" }
                        }
                        submitInput { value = "Create" }
                    }

                    h2 { +"Existing" }
                    table {
                        tr {
                            th { +"Name" }; th { +"Created by" }; th { +"Invite code" }
                            th { +"Members" }; th { +"Posts" }; th { +"Actions" }
                        }
                        all.forEach { group ->
                            val members = memberships.getUsersForGroup(group.id)
                            tr {
                                td {
                                    expandableCell(group.name) {
                                        dl {
                                            field("Id", group.id)
                                            field("Owner", group.owner?.let { byId[it]?.email ?: it } ?: "admin")
                                            field("Visibility", group.visibility)
                                            field("Invite code", group.inviteCode)
                                            field("Members", members.size.toString())
                                            field("Member emails", members.joinToString(", ") { byId[it]?.email ?: it }.ifBlank { null })
                                            field("Posts", (postCounts[group.id] ?: 0).toString())
                                        }
                                        div { a(href = "/admin/groups/${group.id}") { +"Open full page →" } }
                                    }
                                }
                                td {
                                    val owner = group.owner
                                    if (owner == null) {
                                        // Every group made before people could
                                        // make their own, and every one made here.
                                        +"admin"
                                    } else {
                                        +(byId[owner]?.email ?: owner)
                                        val owned = ownedCounts[owner] ?: 0
                                        // Five is the cap the create endpoint
                                        // enforces; worth seeing who is at it.
                                        if (owned > 1) +" · owns $owned"
                                    }
                                }
                                td { code { +group.inviteCode } }
                                td { +members.size.toString() }
                                td { +(postCounts[group.id] ?: 0).toString() }
                                td {
                                    form(action = "/admin/groups/${group.id}/invite", method = FormMethod.post) {
                                        hiddenInput(name = "csrf") { value = csrf }
                                        submitInput { value = "New invite code" }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (Features.GROUPS) post("/groups") {
            val admin = call.requireAdmin(accounts) ?: return@post
            val params = call.receiveParameters()
            if (!call.checkCsrf(params["csrf"])) return@post
            val name = params["name"]?.trim().orEmpty()
            if (name.isBlank()) {
                call.respondText("A name is required", status = HttpStatusCode.BadRequest)
                return@post
            }
            // A code typed in by hand is checked to the shape the generator
            // makes it. Anything shorter, or carrying a vowel, is guessable —
            // and the code is the whole of the security on a group.
            val typed = params["inviteCode"]?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
            if (typed != null && !validInviteCode(typed)) {
                call.respondText(
                    "An invite code is $INVITE_CODE_LENGTH characters from $INVITE_ALPHABET",
                    status = HttpStatusCode.BadRequest,
                )
                return@post
            }
            val id = java.util.UUID.randomUUID().toString()
            val invite = typed ?: newInviteCode(groups::groupByInviteCode)
            val visibility = if (Features.PUBLIC_GROUPS && params["visibility"] == "public") "public" else "private"
            groups.addOrUpdateGroup(Group(id = id, name = name, inviteCode = invite, visibility = visibility))
            moderation.recordGroupAction(admin.guid, "group:create", id, name)
            call.respondRedirect("/admin/groups")
        }

        if (Features.GROUPS) post("/groups/{id}/invite") {
            val admin = call.requireAdmin(accounts) ?: return@post
            if (!call.checkCsrf(call.receiveParameters()["csrf"])) return@post
            val id = call.parameters["id"].orEmpty()
            val group = groups.groupById(id)
            if (group == null) {
                call.respondText("No such group", status = HttpStatusCode.NotFound)
                return@post
            }
            // Rotating the code invalidates every invite already handed out.
            groups.addOrUpdateGroup(group.copy(inviteCode = newInviteCode(groups::groupByInviteCode)))
            moderation.recordGroupAction(admin.guid, "group:rotate-invite", id, null)
            call.respondRedirect("/admin/groups")
        }

        if (Features.GROUPS) get("/groups/{id}") {
            val admin = call.requireAdmin(accounts) ?: return@get
            val csrf = call.sessions.get<AdminSession>()!!.csrf
            val id = call.parameters["id"].orEmpty()
            val group = groups.groupById(id)
            if (group == null) {
                call.respondText("No such group", status = HttpStatusCode.NotFound)
                return@get
            }
            val byId = accounts.allUsers().associateBy { it.guid }
            val memberIds = memberships.getUsersForGroup(id)
            call.respondHtml {
                page("${group.name} — members", admin) {
                    p {
                        +"Invite code: "; code { +group.inviteCode }
                        +" · created by "
                        +(group.owner?.let { byId[it]?.email ?: it } ?: "admin")
                    }

                    h2 { +"Rename" }
                    form(action = "/admin/groups/$id/rename", method = FormMethod.post) {
                        hiddenInput(name = "csrf") { value = csrf }
                        textInput(name = "name") { value = group.name; required = true }
                        submitInput { value = "Rename" }
                    }
                    p {
                        +("Renaming changes what members see. It does not touch the invite " +
                            "code, so invitations already sent keep working.")
                    }

                    h2 { +"Members" }
                    table {
                        tr { th { +"Email" }; th { +"" } }
                        memberIds.forEach { memberId ->
                            tr {
                                td { +(byId[memberId]?.email ?: memberId) }
                                td {
                                    form(action = "/admin/groups/$id/remove", method = FormMethod.post) {
                                        hiddenInput(name = "csrf") { value = csrf }
                                        hiddenInput(name = "userId") { value = memberId }
                                        submitInput { value = "Remove" }
                                    }
                                }
                            }
                        }
                    }
                    h2 { +"Add member" }
                    form(action = "/admin/groups/$id/add", method = FormMethod.post) {
                        hiddenInput(name = "csrf") { value = csrf }
                        select {
                            name = "userId"
                            byId.values.filterNot { it.guid in memberIds }.sortedBy { it.email }.forEach {
                                option { value = it.guid; +it.email }
                            }
                        }
                        submitInput { value = "Add" }
                    }
                }
            }
        }

        /**
         * Renames a group.
         *
         * The name and nothing else: the invite code is what people join with,
         * and rotating it is a separate, deliberate act with its own button.
         */
        if (Features.GROUPS) post("/groups/{id}/rename") {
            val admin = call.requireAdmin(accounts) ?: return@post
            val params = call.receiveParameters()
            if (!call.checkCsrf(params["csrf"])) return@post
            val id = call.parameters["id"].orEmpty()
            val group = groups.groupById(id)
            if (group == null) {
                call.respondText("No such group", status = HttpStatusCode.NotFound)
                return@post
            }
            val name = params["name"]?.trim().orEmpty()
            if (name.isBlank()) {
                call.respondText("A name is required", status = HttpStatusCode.BadRequest)
                return@post
            }
            groups.addOrUpdateGroup(group.copy(name = name))
            moderation.recordGroupAction(admin.guid, "group:rename", id, name)
            call.respondRedirect("/admin/groups/$id")
        }

        if (Features.GROUPS) post("/groups/{id}/add") {
            val admin = call.requireAdmin(accounts) ?: return@post
            val params = call.receiveParameters()
            if (!call.checkCsrf(params["csrf"])) return@post
            val id = call.parameters["id"].orEmpty()
            val userId = params["userId"].orEmpty()
            memberships.addUserToGroup(userId, id)
            groups.groupById(id)?.let { onMemberAdded(userId, it, admin.guid) }
            moderation.recordGroupAction(admin.guid, "group:add-member", id, userId)
            call.respondRedirect("/admin/groups/$id")
        }

        if (Features.GROUPS) post("/groups/{id}/remove") {
            val admin = call.requireAdmin(accounts) ?: return@post
            val params = call.receiveParameters()
            if (!call.checkCsrf(params["csrf"])) return@post
            val id = call.parameters["id"].orEmpty()
            val userId = params["userId"].orEmpty()
            memberships.removeUserFromGroup(userId, id)
            moderation.recordGroupAction(admin.guid, "group:remove-member", id, userId)
            call.respondRedirect("/admin/groups/$id")
        }

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

private fun HTML.loginPage(error: String?) {
    head {
        title { +"${AppInfo.NAME} — admin" }
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        styleBlock()
    }
    body {
        h1 { +"${AppInfo.NAME} admin" }
        if (error != null) p(classes = "error") { +error }
        form(action = "/admin/login", method = FormMethod.post) {
            p { emailInput(name = "email") { placeholder = "email"; required = true } }
            p { passwordInput(name = "password") { placeholder = "password"; required = true } }
            p { submitInput { value = "Sign in" } }
        }
    }
}

private fun HTML.page(heading: String, admin: User, waiting: Int = 0, content: BODY.() -> Unit) {
    head {
        title { +"${AppInfo.NAME} — $heading" }
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        styleBlock()
    }
    body {
        nav {
            a(href = "/admin") { +"Overview" }
            a(href = "/admin/users") { +"Users" }
            a(href = "/admin/posts") { +"Posts" }
            if (Features.COMMENTS) a(href = "/admin/comments") { +"Comments" }
            if (Features.GROUPS) a(href = "/admin/groups") { +"Groups" }
            a(href = "/admin/audit") { +"Audit" }
            if (Features.CRASH_REPORTS) a(href = "/admin/crashes") { +"Crashes" }
            if (Features.REPORTS) a(href = "/admin/reports") {
                +"Reports"
                if (waiting > 0) span("badge count") { +waiting.toString() }
            }
            if (Features.FEEDBACK) a(href = "/admin/feedback") { +"Feedback" }
            if (Features.TELEMETRY) a(href = "/admin/events") { +"Events" }
            div("spacer") {}
            span("who") { +admin.email }
            form(action = "/admin/logout", method = FormMethod.post) { submitInput { value = "Sign out" } }
        }
        h1 { +heading }
        content()
    }
}

/**
 * One number, what it counts, and where to go about it.
 *
 * A link rather than a tile with a link in it: the whole card is the target,
 * because a number nobody can click is a number somebody has to go and find.
 */
private fun FlowContent.statCard(label: String, value: Int, href: String, urgent: Boolean = false) {
    a(href = href, classes = if (urgent) "card urgent" else "card") {
        span("value") { +value.toString() }
        span("label") { +label }
    }
}

private fun HEAD.styleBlock() {
    style {
        unsafe {
            +"""
            /*
               Same warm palette as the app and the site, and the same three
               theme states: light, dark, and whatever the machine says. This is
               read at odd hours on whatever is to hand.
            */
            :root {
              --bg: #fdf8f4; --panel: #fffdfb; --fg: #211a16; --quiet: #6d5a51;
              --line: #e6d8d0; --accent: #8c4a32; --danger: #9c4238;
              --live: #2f6b4f; --badge: #f0e3db;
            }
            @media (prefers-color-scheme: dark) {
              :root {
                --bg: #241d19; --panel: #2e2521; --fg: #f4ebe5; --quiet: #bda99e;
                --line: #453832; --accent: #e0a184; --danger: #e08b7f;
                --live: #7fc0a0; --badge: #3d322c;
              }
            }
            * { box-sizing: border-box; }
            body { font: 15px/1.5 system-ui, -apple-system, sans-serif; margin: 0;
                   padding: 0 0 4rem; color: var(--fg); background: var(--bg); }
            h1 { font-size: 1.5rem; margin: 1.5rem 2rem .25rem; }
            h2 { font-size: .8rem; text-transform: uppercase; letter-spacing: .06em;
                 color: var(--quiet); margin: 2rem 2rem .5rem; font-weight: 600; }
            /*
               Everything in the body sits on the same left edge. Listing the
               selectors that should be indented meant every element nobody
               thought of — a heading, a form — sat flush against the window
               while the table beside it did not.
            */
            body > *:not(nav) { margin-left: 2rem; margin-right: 2rem; }
            body > table { width: calc(100% - 4rem); }

            /*
               Links inside the page, which had no colour of their own: the
               browser's blue and visited purple on a dark brown background,
               which read as broken rather than as links.
            */
            a { color: var(--accent); }
            a:hover { text-decoration: none; }

            /* Sticky, because these tables are long and the way out should not be. */
            nav { position: sticky; top: 0; z-index: 2; display: flex; align-items: center;
                  gap: 1.25rem; padding: .75rem 2rem; background: var(--panel);
                  border-bottom: 1px solid var(--line); font-size: .9rem; }
            nav a { color: var(--fg); text-decoration: none; padding: .25rem 0;
                    border-bottom: 2px solid transparent; }
            nav a:hover { border-bottom-color: var(--accent); }
            nav .spacer { flex: 1; }
            nav .who { color: var(--quiet); font-size: .85rem; }

            table { border-collapse: collapse; width: calc(100% - 4rem); background: var(--panel);
                    border: 1px solid var(--line); border-radius: 10px; overflow: hidden; margin-top: 1rem; }
            th { text-align: left; padding: .6rem .75rem; background: var(--badge);
                 font-size: .78rem; text-transform: uppercase; letter-spacing: .04em;
                 color: var(--quiet); border-bottom: 1px solid var(--line); }
            td { text-align: left; padding: .7rem .75rem; border-bottom: 1px solid var(--line);
                 vertical-align: top; }
            tr:last-child td { border-bottom: 0; }
            tr:hover td { background: color-mix(in srgb, var(--badge) 40%, transparent); }
            tr.muted td { opacity: .55; }

            .badge { display: inline-block; padding: .1rem .5rem; border-radius: 999px;
                     background: var(--badge); font-size: .78rem; }
            nav .badge { margin-left: .35rem; }
            .badge.count { background: var(--danger); color: #fff; }
            .badge.hidden { background: var(--badge); color: var(--quiet); }
            .badge.live { color: var(--live); border: 1px solid currentColor; background: none; }
            .excerpt { color: var(--quiet); font-size: .85rem; margin-top: .2rem;
                       max-width: 32rem; white-space: pre-wrap; }
            .reason { font-size: .85rem; margin-bottom: .2rem; }
            .quiet, .hint { color: var(--quiet); }
            .hint { max-width: 44rem; }
            .empty { margin-top: 2rem; color: var(--quiet); }
            .actions { white-space: nowrap; }
            .actions form { display: inline; }

            /* Cards: one number each, the whole thing clickable. */
            .cards { display: flex; flex-wrap: wrap; gap: .75rem; margin-top: .5rem; }
            .card { display: flex; flex-direction: column; gap: .15rem; min-width: 11rem;
                    padding: .9rem 1.1rem; border-radius: 12px; text-decoration: none;
                    background: var(--panel); border: 1px solid var(--line); color: var(--fg); }
            .card:hover { border-color: var(--accent); }
            .card .value { font-size: 1.9rem; font-weight: 600; line-height: 1.1; }
            .card .label { font-size: .82rem; color: var(--quiet); }
            /* Colour only where there is something to do, so it keeps meaning something. */
            .card.urgent { border-color: var(--danger); }
            .card.urgent .value { color: var(--danger); }
            .links { display: flex; flex-direction: column; gap: .4rem; margin-top: .5rem; }

            .error { color: var(--danger); }
            input[type=submit], button { cursor: pointer; font: inherit; font-size: .85rem;
                   padding: .35rem .7rem; border-radius: 7px; border: 1px solid var(--line);
                   background: var(--panel); color: var(--fg); }
            input[type=submit]:hover { border-color: var(--accent); }
            input[type=submit].danger { border-color: var(--danger); color: var(--danger); }
            input[type=text], input[type=email], input[type=password], select {
                   font: inherit; padding: .35rem .5rem; border-radius: 7px;
                   border: 1px solid var(--line); background: var(--panel); color: var(--fg); }
            textarea { font: inherit; width: 100%; max-width: 30rem; padding: .4rem .5rem;
                   border-radius: 7px; border: 1px solid var(--line);
                   background: var(--panel); color: var(--fg); }

            /*
               Read on a phone as often as a laptop. Without a viewport meta a
               mobile browser renders at desktop width and zooms out; with it,
               this tightens the 2rem gutters to 1rem, lets the nav wrap, stacks
               the cards, and lets a wide table scroll inside itself rather than
               pushing the whole page sideways.
            */
            @media (max-width: 640px) {
              h1 { margin: 1rem 1rem .25rem; font-size: 1.3rem; }
              h2 { margin: 1.25rem 1rem .5rem; }
              body > *:not(nav) { margin-left: 1rem; margin-right: 1rem; }
              body > table { width: calc(100% - 2rem); }
              table { width: 100%; display: block; overflow-x: auto; }
              nav { flex-wrap: wrap; gap: .6rem 1rem; padding: .6rem 1rem; }
              nav .spacer { display: none; }
              nav .who { width: 100%; order: 99; }
              .card { min-width: 100%; }
              .excerpt, .hint { max-width: 100%; }
              textarea { max-width: 100%; }
            }
            """
        }
    }
}

/** A row cell whose summary opens the whole record inline — native <details>, no script. */
private fun kotlinx.html.FlowContent.expandableCell(summaryText: String, body: kotlinx.html.DIV.() -> Unit) {
    details {
        summary { strong { +summaryText } }
        div("detail", body)
    }
}

/** A labelled line inside an expandable body; skipped when the value is blank. */
private fun kotlinx.html.DL.field(label: String, value: String?) {
    if (!value.isNullOrBlank()) { dt { +label }; dd { +value } }
}

/**
 * A code an operator typed in by hand. Same shape the generator makes — eight
 * of its alphabet — because a short or vowel-carrying code is a guessable one,
 * and the code is the whole of the security on a group.
 */
private fun validInviteCode(code: String): Boolean = INVITE_CODE.matches(code)

private val INVITE_CODE = Regex("[$INVITE_ALPHABET]{$INVITE_CODE_LENGTH}")

/** The group dropdown, the same in the add form and on every row. */
private fun kotlinx.html.FlowOrInteractiveOrPhrasingContent.groupSelect(selected: String?) {
    select {
        name = "group"
        option {
            value = ""
            this.selected = selected == null
            +"— no group —"
        }
        TAG_GROUPS.forEach { group ->
            option {
                value = group.id
                this.selected = group.id == selected
                +group.english
            }
        }
    }
}
