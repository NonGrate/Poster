package com.example.poster.admin

import com.example.poster.comments.CommentsRepository
import com.example.poster.config.Features
import com.example.poster.model.AccountRepository
import com.example.poster.model.GroupRepository
import com.example.poster.model.Language
import com.example.poster.model.ModerationRepository
import com.example.poster.model.PostVisibility
import com.example.poster.model.ReportsRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import kotlinx.html.*

/**
 * The tables about what people wrote and who wrote it: `/admin/users`,
 * `/admin/reports`, `/admin/posts` and `/admin/comments`.
 *
 * One file because they are one job — a report names a post, a post names an
 * author, and hiding any of them lands in the same audit log.
 */
internal fun Route.adminPostsPages(
    accounts: AccountRepository,
    moderation: ModerationRepository,
    hasher: com.example.poster.auth.PasswordHasher,
    groups: GroupRepository,
    reports: ReportsRepository,
    comments: CommentsRepository,
    // Signing someone out everywhere. A new password is worth nothing while the
    // sessions opened with the old one keep working, so this has no default:
    // a caller has to say what it does.
    revokeSessions: (userId: String) -> Unit,
) {
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

    get("/posts") {
        val admin = call.requireAdmin(accounts) ?: return@get
        val csrf = call.sessions.get<AdminSession>()!!.csrf
        // The one private post this admin has just asked to see. Carried in the
        // query rather than remembered, so it lasts exactly one page: reading
        // the next private post is another button and another audit row.
        val revealed = call.request.queryParameters["reveal"]
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
                                    // A private post was written for nobody.
                                    // Moderating still needs it readable, so
                                    // it is one button away rather than
                                    // gone — and the button leaves a record.
                                    if (post.visibility == PostVisibility.PRIVATE && post.guid != revealed) {
                                        p("message") { em { +"Private. Hidden until revealed." } }
                                        form(action = "/admin/posts/${post.guid}/reveal", method = FormMethod.post) {
                                            hiddenInput(name = "csrf") { value = csrf }
                                            submitInput { value = "Reveal" }
                                        }
                                    } else {
                                        p("message") { +post.message }
                                    }
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

    post("/posts/{id}/reveal") {
        val admin = call.requireAdmin(accounts) ?: return@post
        val params = call.receiveParameters()
        if (!call.checkCsrf(params["csrf"])) return@post
        val id = call.parameters["id"].orEmpty()
        moderation.recordReveal(admin.guid, id)
        call.respondRedirect("/admin/posts?reveal=$id")
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
}
