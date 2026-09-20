package com.example.poster.admin

import com.example.poster.INVITE_ALPHABET
import com.example.poster.INVITE_CODE_LENGTH
import com.example.poster.config.Features
import com.example.poster.model.AccountRepository
import com.example.poster.model.Group
import com.example.poster.model.GroupRepository
import com.example.poster.model.ModerationRepository
import com.example.poster.model.UserGroupRepository
import com.example.poster.newInviteCode
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

/** `/admin/groups` — the rooms, their members and the codes that let people in. */
internal fun Route.adminGroupsPages(
    accounts: AccountRepository,
    moderation: ModerationRepository,
    groups: GroupRepository,
    memberships: UserGroupRepository,
    /** After the panel adds somebody to a group: (member, group, admin). The notifier listens. */
    onMemberAdded: (userId: String, group: Group, actor: String) -> Unit,
) {
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
}

/**
 * A code an operator typed in by hand. Same shape the generator makes — eight
 * of its alphabet — because a short or vowel-carrying code is a guessable one,
 * and the code is the whole of the security on a group.
 */
private fun validInviteCode(code: String): Boolean = INVITE_CODE.matches(code)

private val INVITE_CODE = Regex("[$INVITE_ALPHABET]{$INVITE_CODE_LENGTH}")
