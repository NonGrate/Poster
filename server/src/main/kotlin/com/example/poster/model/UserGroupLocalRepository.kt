package com.example.poster.model

import com.example.poster.PostDatabase
import com.example.poster.util.emailMatchKey
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// No default database, for the reason given on PostsLocalRepository.
class UserGroupLocalRepository(
    private val database: PostDatabase,
) : UserGroupRepository {
    private val userGroupQueries = database.userGroupQueries
    private val groupQueries = database.groupQueries
    private val inviteQueries = database.groupInviteQueries

    override fun addUserToGroup(userId: String, groupId: String) {
        val currentTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toString()
        userGroupQueries.insertUserGroup(userId, groupId, currentTime)
    }

    override fun removeUserFromGroup(userId: String, groupId: String) {
        userGroupQueries.deleteUserGroup(userId, groupId)
    }

    override fun getGroupsForUser(userId: String): List<Group> {
        return userGroupQueries.getUserGroups(userId).executeAsList().map {
            Group(
                id = it.id,
                name = it.name,
                inviteCode = it.inviteCode,
                visibility = it.visibility,
                // Dropped here until now, so every group the app received
                // through this call looked ownerless and nobody could be shown
                // the controls for the ones they made.
                owner = it.owner,
                // Who to name for the people who are only members: a guid tells
                // somebody nothing about whose room they are writing into.
                ownerName = listOf(it.ownerName, it.ownerSurname)
                    .filterNotNull()
                    .filter { part -> part.isNotBlank() }
                    .joinToString(" ")
                    .ifBlank { null },
                // The caller's own role here: this query is scoped to them.
                myRole = it.myRole,
            )
        }
    }

    override fun getUsersForGroup(groupId: String): List<String> {
        return userGroupQueries.getGroupUsers(groupId).executeAsList().map { it.guid }
    }

    override fun membersOf(groupId: String, ownerId: String?): List<GroupMember> =
        userGroupQueries.getGroupUsers(groupId).executeAsList().map {
            GroupMember(
                id = it.guid,
                name = listOf(it.name, it.surname).filter { part -> part.isNotBlank() }
                    .joinToString(" ")
                    .ifBlank { it.email.substringBefore('@') },
                isOwner = it.guid == ownerId,
                isAdmin = it.membershipRole == "admin",
            )
        }

    override fun isMember(userId: String, groupId: String): Boolean =
        userGroupQueries.isUserInGroup(userId, groupId).executeAsOne()

    override fun roleOf(userId: String, groupId: String): String? =
        userGroupQueries.roleOf(userId, groupId).executeAsOneOrNull()

    override fun setRole(userId: String, groupId: String, role: String) {
        userGroupQueries.setMemberRole(role, userId, groupId)
    }

    override fun getGroupByInviteCode(inviteCode: String): Group? {
        return groupQueries.getGroupByInviteCode(inviteCode).executeAsOneOrNull()?.let {
            Group(
                id = it.id,
                name = it.name,
                inviteCode = it.inviteCode,
                owner = it.owner,
            )
        }
    }

    // — invites —

    override fun createInvite(
        groupId: String,
        createdBy: String,
        code: String,
        at: String,
        sentTo: String?,
    ) {
        inviteQueries.insertInvite(code, groupId, createdBy, at, sentTo?.lowercase())
    }

    override fun invitesFor(groupId: String): List<GroupInvite> {
        // One read of the members rather than a lookup per row: an owner who
        // has sent a dozen invites should not cost a dozen queries to show them.
        val names = userGroupQueries.getGroupUsers(groupId).executeAsList()
            .associate { it.guid to listOf(it.name, it.surname).filter(String::isNotBlank).joinToString(" ") }
        return inviteQueries.invitesForGroup(groupId).executeAsList().map {
            GroupInvite(
                code = it.code,
                groupId = it.groupId,
                createdAt = it.createdAt,
                usedByName = it.usedBy?.let { id -> names[id] },
                usedAt = it.usedAt,
                revokedAt = it.revokedAt,
                sentTo = it.sentTo,
            )
        }
    }

    override fun inviteByCode(code: String): GroupInvite? =
        inviteQueries.inviteByCode(code).executeAsOneOrNull()?.let {
            GroupInvite(
                code = it.code,
                groupId = it.groupId,
                createdAt = it.createdAt,
                usedAt = it.usedAt,
                revokedAt = it.revokedAt,
            )
        }

    override fun spendInvite(
        code: String,
        userId: String,
        at: String,
        spenderEmail: String,
    ): JoinOutcome =
        database.transactionWithResult {
            val invite = inviteQueries.inviteByCode(code).executeAsOneOrNull()
                ?: return@transactionWithResult JoinOutcome.Invalid
            if (invite.usedAt != null || invite.revokedAt != null) {
                return@transactionWithResult JoinOutcome.Invalid
            }
            // A bound invite is that address's. Matched by inbox — Gmail dots and
            // +tags ignored — so the person it was emailed to can accept it under
            // a spelling of their own address the mail did not use.
            val boundTo = invite.sentTo
            if (boundTo != null && emailMatchKey(boundTo) != emailMatchKey(spenderEmail)) {
                return@transactionWithResult JoinOutcome.WrongAddress
            }
            inviteQueries.spendInvite(userId, at, code)
            // The UPDATE's WHERE carries the still-live condition, so a second
            // redeemer of the same code changes no rows. Reading the row back is
            // how this call knows which of the two it was.
            val after = inviteQueries.inviteByCode(code).executeAsOneOrNull()
            if (after?.usedBy != userId) return@transactionWithResult JoinOutcome.Invalid
            groupQueries.getGroupById(invite.groupId).executeAsOneOrNull()?.let {
                JoinOutcome.Joined(Group(id = it.id, name = it.name, inviteCode = it.inviteCode, owner = it.owner))
            } ?: JoinOutcome.Invalid
        }

    override fun revokeInvite(groupId: String, code: String, at: String): Boolean =
        database.transactionWithResult {
            val invite = inviteQueries.inviteByCode(code).executeAsOneOrNull()
            if (invite == null || invite.groupId != groupId) return@transactionWithResult false
            inviteQueries.revokeInvite(at, code)
            inviteQueries.inviteByCode(code).executeAsOneOrNull()?.revokedAt != null
        }
}
