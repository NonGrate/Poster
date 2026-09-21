package com.example.poster.groups

import com.example.poster.auth.AuthService
import com.example.poster.authenticatedUserId
import com.example.poster.config.Features
import com.example.poster.domain.validation.GroupRules
import com.example.poster.mail.GroupMail
import com.example.poster.model.*
import com.example.poster.newInviteCode
import com.example.poster.push.Notifier
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * `/groups` — the rooms, inside the bearer-authenticated block: making one,
 * who is in it, the invitations that let somebody in, and joining or leaving.
 */
internal fun Route.groupRoutes(
    groupRepository: GroupRepository,
    userGroupRepository: UserGroupRepository,
    accountRepository: AccountRepository,
    groupMail: GroupMail,
    notifier: Notifier?,
) {
    route("/groups") {
        // Managing members and invites is the owner's, and now also any
        // member the owner promoted to admin. Closing the group and
        // changing roles stay owner-only, so they keep the raw owner check.
        fun canManage(id: String, callerId: String): Boolean =
            groupRepository.groupById(id)?.owner == callerId ||
                userGroupRepository.roleOf(callerId, id) == "admin"

        // The invite code is the whole of the security on an invite-only
        // group, so it travels only to the people who may hand it out.
        // The app never reads it from these responses; the admins' panel
        // gets its codes from /{id}/invites.
        fun Group.forCaller(callerId: String): Group =
            if (canManage(id, callerId)) this else copy(inviteCode = "")

        // Listing every group was here, and it answered any signed-in
        // caller with every group's invite code — which is the whole of
        // the security on a group. Nothing in the app asked for it.
        // feature.publicGroups: what anybody may browse and join without an invite.
        if (Features.PUBLIC_GROUPS) get("/public") {
            val me = call.authenticatedUserId()
            call.respond(groupRepository.publicGroups().map { it.forCaller(me) })
        }
        if (Features.PUBLIC_GROUPS) post("/{id}/visibility") {
            val id = call.parameters["id"].orEmpty()
            val visibility = runCatching { call.receive<Map<String, String>>()["visibility"] }.getOrNull()
            if (visibility != GroupVisibility.PUBLIC && visibility != GroupVisibility.PRIVATE) {
                call.respond(HttpStatusCode.BadRequest, ApiError("visibility is public or private"))
                return@post
            }
            // Authorisation before existence, like /{id}/members: a
            // different answer for a group that is not the caller's and one
            // that is not there tells them which group ids are real.
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
                call.respond(group.forCaller(call.authenticatedUserId()))
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
            // Authorisation before existence, like /{id}/members: a
            // different answer for a group that is not the caller's and one
            // that is not there tells them which group ids are real.
            if (!canManage(id, userId)) {
                call.respond(HttpStatusCode.Forbidden, "Only an owner or admin can remove somebody")
                return@delete
            }
            val group = groupRepository.groupById(id)
            if (group == null) {
                call.respond(HttpStatusCode.NotFound)
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
            // Authorisation before existence, like /{id}/members: a
            // different answer for a group that is not the caller's and one
            // that is not there tells them which group ids are real.
            if (group == null || group.owner != userId) {
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
            // Authorisation before existence, like /{id}/members: a
            // different answer for a group that is not the caller's and one
            // that is not there tells them which group ids are real.
            if (group == null || group.owner != userId) {
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
            val code = newInviteCode { taken -> inviteCodeTaken(userGroupRepository, id, taken) }
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
            val code = newInviteCode { taken -> inviteCodeTaken(userGroupRepository, id, taken) }
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
            call.respond(userGroups.map { it.forCaller(userId) })
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
            // Not a member, or no such group: the same answer either way,
            // because which group ids are real is not a stranger's to learn.
            if (!userGroupRepository.isMember(userId, groupId)) {
                call.respond(HttpStatusCode.NotFound)
                return@delete
            }
            // An owner walking out strands the room: nobody left could
            // admit anybody, change a role or close it. Closing it is the
            // act they are actually after, and it has its own route.
            if (groupRepository.groupById(groupId)?.owner == userId) {
                call.respond(
                    HttpStatusCode.Conflict,
                    ApiError("An owner cannot leave their own group. Close it instead."),
                )
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
 * Whether an invite code is already spoken for, shaped as [newInviteCode] wants
 * it: a non-null group means taken. Unique across every invite rather than one
 * group's, because a code is redeemed without saying which group it is for.
 */
private fun inviteCodeTaken(
    memberships: UserGroupRepository,
    groupId: String,
    code: String,
): Group? =
    if (memberships.invitesFor(groupId).any { it.code == code }) {
        Group(id = "taken", name = "", inviteCode = code)
    } else {
        memberships.getGroupByInviteCode(code)
    }
