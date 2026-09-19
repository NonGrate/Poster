package com.example.poster.model

/**
 * The result of trying to spend an invite.
 *
 * [Invalid] is deliberately one answer for unknown, spent and withdrawn: telling
 * those apart hands a code-guesser an oracle for which kind of wrong they hit.
 * [WrongAddress] is separate because it can only be reached by someone who
 * already holds a real, live code — the person it was emailed to, signed in
 * under a different address — so it helps them without helping a guesser.
 */
sealed interface JoinOutcome {
    data class Joined(val group: Group) : JoinOutcome
    data object WrongAddress : JoinOutcome
    data object Invalid : JoinOutcome
}

interface UserGroupRepository {
    fun addUserToGroup(userId: String, groupId: String)
    fun removeUserFromGroup(userId: String, groupId: String)
    fun getGroupsForUser(userId: String): List<Group>
    fun getUsersForGroup(groupId: String): List<String>
    fun getGroupByInviteCode(inviteCode: String): Group?

    /** The members of a group, as the other members see them. */
    fun membersOf(groupId: String, ownerId: String?): List<GroupMember>

    fun isMember(userId: String, groupId: String): Boolean

    /** A member's role, or null when they are not in the group. */
    fun roleOf(userId: String, groupId: String): String?

    /** Owner only, enforced at the route: promote a member to admin, or back. */
    fun setRole(userId: String, groupId: String, role: String)

    // — invites, one row each, good once —

    /**
     * [sentTo] is the address it was emailed to, or null for one made by hand.
     * A bound invite can only be spent by the account holding that address; an
     * unbound one by anybody who has the code.
     */
    fun createInvite(
        groupId: String,
        createdBy: String,
        code: String,
        at: String,
        sentTo: String? = null,
    )

    fun invitesFor(groupId: String): List<GroupInvite>

    /** One invite, for the page an emailed link lands on. Null when unknown. */
    fun inviteByCode(code: String): GroupInvite?

    /**
     * Spends an invite and returns the group it was for. The read, the
     * address check and the write happen in one transaction, so two people
     * redeeming the same code cannot both win, and a bound invite is matched to
     * the spender by inbox (Gmail dots and +tags ignored) rather than by exact
     * string. See [JoinOutcome] for what the answers mean.
     */
    fun spendInvite(code: String, userId: String, at: String, spenderEmail: String): JoinOutcome

    fun revokeInvite(groupId: String, code: String, at: String): Boolean
}

