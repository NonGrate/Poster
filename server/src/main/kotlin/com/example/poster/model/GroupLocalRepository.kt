package com.example.poster.model

import com.example.poster.PostDatabase

// Takes the database rather than opening its own, matching AccountLocalRepository:
// a test can then hand it an in-memory one instead of the real file.
class GroupLocalRepository(
    private val database: PostDatabase,
) : GroupRepository {
    private val groupQueries = database.groupQueries
    private val postQueries = database.postQueries
    private val inviteQueries = database.groupInviteQueries
    private val userGroupQueries = database.userGroupQueries

    override fun allGroups(): List<Group> {
        return groupQueries.getAllGroups().executeAsList().map {
            Group(
                id = it.id,
                name = it.name,
                inviteCode = it.inviteCode,
                owner = it.owner,
                visibility = it.visibility,
            )
        }
    }

    override fun groupById(id: String): Group? {
        return groupQueries.getGroupById(id).executeAsOneOrNull()?.let {
            Group(
                id = it.id,
                name = it.name,
                inviteCode = it.inviteCode,
                owner = it.owner,
                visibility = it.visibility,
            )
        }
    }

    override fun groupByInviteCode(inviteCode: String): Group? {
        return groupQueries.getGroupByInviteCode(inviteCode).executeAsOneOrNull()?.let {
            Group(
                id = it.id,
                name = it.name,
                inviteCode = it.inviteCode,
                owner = it.owner,
                visibility = it.visibility,
            )
        }
    }

    override fun addOrUpdateGroup(group: Group) {
        val exists = groupQueries.getGroupById(group.id).executeAsOneOrNull() != null
        if (exists) {
            groupQueries.updateGroup(
                name = group.name,
                inviteCode = group.inviteCode,
                visibility = group.visibility,
                id = group.id,
            )
        } else {
            groupQueries.insertGroup(
                id = group.id,
                name = group.name,
                inviteCode = group.inviteCode,
                owner = group.owner,
                visibility = group.visibility,
            )
        }
    }

    override fun publicGroups(): List<Group> =
        groupQueries.getPublicGroups().executeAsList().map {
            Group(id = it.id, name = it.name, inviteCode = it.inviteCode, owner = it.owner, visibility = it.visibility, memberCount = it.memberCount.toInt())
        }

    override fun setVisibility(id: String, visibility: String) {
        groupQueries.setGroupVisibility(visibility, id)
    }

    override fun countOwnedBy(userId: String): Long =
        groupQueries.countGroupsOwnedBy(userId).executeAsOne()

    /**
     * Closes a group and lets its posts go private.
     *
     * Private rather than deleted: somebody wrote them and they are theirs. The
     * group closing is not a reason to take their words, and private is the
     * only honest answer to "who can see this now" — the room it was shared with
     * is gone, so nobody but the author can.
     *
     * Left as they were, they would have pointed at a group that no longer
     * exists and been visible to nobody while still claiming to be shared: the
     * feed asks whether the viewer is in the group, and nobody is in one
     * that has been deleted. There is no foreign key on Post.group to
     * catch that.
     *
     * One transaction, because a group deleted without its posts being
     * reassigned is exactly the state this exists to prevent.
     */
    override fun removeGroup(id: String): Boolean = database.transactionWithResult {
        val before = groupQueries.getAllGroups().executeAsList().size
        postQueries.makeGroupPostsPrivate(id)
        inviteQueries.deleteInvitesForGroup(id)
        userGroupQueries.deleteMembershipsOfGroup(id)
        groupQueries.deleteGroup(id)
        groupQueries.getAllGroups().executeAsList().size < before
    }
}
