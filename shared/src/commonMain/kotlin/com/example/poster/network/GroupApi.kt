package com.example.poster.network

import com.example.poster.model.Group
import com.example.poster.model.GroupInvite
import com.example.poster.model.GroupMember

/**
 * The outcome of redeeming an invite. [INVALID] unifies unknown/spent/withdrawn
 * on purpose; [WRONG_ADDRESS] means a live code that belongs to a different
 * address than the one signed in.
 */
enum class JoinResult { JOINED, WRONG_ADDRESS, INVALID }

/**
 * Reading groups, making one, and joining one.
 *
 * Renaming and deleting them are the admin panel's alone. Those were here too,
 * and the endpoints behind them let any signed-in person overwrite a
 * group's invite code or delete it for everybody — so both the routes and
 * these calls are gone rather than guarded. [createGroup] is not the same
 * thing: it takes a name and touches only a row the server just made.
 */
interface GroupApi {
    suspend fun getAllGroups(): List<Group>

    suspend fun getGroupById(id: String): Group?

    /**
     * Looks a group up by its old standing code.
     *
     * Kept for reading only. Joining goes through [joinWithInvite] now, because
     * an invite is spent by using it and the server is the only place that can
     * say whether it is still good.
     */
    suspend fun getGroupByInviteCode(inviteCode: String): Group?

    /**
     * Redeems an invitation.
     *
     * The code goes to the server rather than being resolved here first: an
     * invite is good once, and whether this particular one is still live is a
     * question only the server can answer without a race.
     *
     * [JoinResult.INVALID] unifies unknown, spent and withdrawn on purpose —
     * telling them apart tells somebody holding a guessed code which kind of
     * wrong it was. [JoinResult.WRONG_ADDRESS] is separate: it can only be
     * reached by someone already holding a live code who is signed in under a
     * different address, so it helps them without helping a guesser.
     */
    suspend fun joinWithInvite(userId: String, code: String): JoinResult

    // — the owner's side of a group —

    /** Who is in it, as names. Members only. */
    suspend fun getMembers(groupId: String): List<GroupMember>

    /** Removes somebody. An owner or admin may; never to themselves. */
    suspend fun removeMember(groupId: String, memberId: String): Boolean

    /**
     * Promotes a member to admin, or back to a plain member. The owner's alone —
     * an admin manages members and invites, but not who else gets to. [role] is
     * "admin" or "member". True when the server took it.
     */
    suspend fun setMemberRole(groupId: String, memberId: String, role: String): Boolean

    /** The invitations issued for it, spent and unspent. Owner only. */
    suspend fun getInvites(groupId: String): List<GroupInvite>

    /** A new invitation, good once. Returns its code. */
    suspend fun createInvite(groupId: String): String?

    /** Withdraws one that has not been used. */
    suspend fun revokeInvite(groupId: String, code: String): Boolean

    /**
     * Invite somebody by email. The invitation is theirs alone: only the
     * account holding that address can spend it.
     *
     * True means the server took it, not that the address has an account —
     * it deliberately does not say, because answering would make this a way to
     * ask whether a given person uses a post app.
     */
    suspend fun inviteByEmail(groupId: String, email: String): Boolean

    /**
     * Close a group for good. Only its owner may.
     *
     * What was shared into it is not deleted — it becomes private to whoever
     * wrote it, and loses the group it pointed at. Somebody's post is
     * theirs, and closing the room they said it in is not a reason to take it
     * away from them.
     */
    suspend fun removeGroup(groupId: String): Boolean

    /**
     * Makes a group with this name and joins the caller to it.
     *
     * The server owns the id and the invite code — a code the client chose
     * would be a code somebody could guess. Returns null when the server
     * refused, which is either a name it would not take or one account having
     * created as many as it may.
     */
    suspend fun createGroup(name: String): Group?

    /**
     * Gets all groups that a user belongs to.
     */
    suspend fun getUserGroups(userId: String): List<Group>

    /**
     * Adds a user to a group.
     */
    suspend fun addUserToGroup(userId: String, groupId: String)

    /**
     * Removes a user from a group.
     */
    suspend fun removeUserFromGroup(userId: String, groupId: String)

    /**
     * Checks if a user is a member of a group.
     */

    /**
     * Gets all users in a specific group.
     */
}
