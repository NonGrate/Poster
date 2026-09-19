package com.example.poster.preview

import com.example.poster.model.Group
import com.example.poster.model.GroupMember
import com.example.poster.model.GroupInvite
import com.example.poster.network.GroupApi
import com.example.poster.network.JoinResult

class FakeGroupApi : GroupApi {
    private val groups = mutableListOf<Group>(
        Group("group-a", "Group A", "GROUP_A_INVITE"),
        Group("group-b", "Group B", "GROUP_B_INVITE"),
        Group("book-club", "Book Club", "BOOK_CLUB_INVITE")
    )

    override suspend fun getAllGroups(): List<Group> = groups

    override suspend fun getGroupById(id: String): Group? = groups.find { it.id == id }

    override suspend fun getGroupByInviteCode(inviteCode: String): Group? = groups.find { it.inviteCode == inviteCode }




    override suspend fun joinWithInvite(userId: String, code: String) = JoinResult.INVALID
    override suspend fun getMembers(groupId: String) = emptyList<GroupMember>()

    override suspend fun removeGroup(groupId: String) = false

    override suspend fun inviteByEmail(groupId: String, email: String) = false
    override suspend fun removeMember(groupId: String, memberId: String) = false
    override suspend fun setMemberRole(groupId: String, memberId: String, role: String) = false
    override suspend fun getInvites(groupId: String) = emptyList<GroupInvite>()
    override suspend fun createInvite(groupId: String): String? = null
    override suspend fun revokeInvite(groupId: String, code: String) = false

    override suspend fun createGroup(name: String, visibility: String): Group? =
        Group(
            id = "preview-${groups.size + 1}",
            name = name,
            inviteCode = "PREVIEW${groups.size + 1}",
            owner = "preview-user",
        ).also { groups += it }

    override suspend fun getUserGroups(userId: String): List<Group> {
        // For fake implementation, return all groups for any user
        return groups
    }

    override suspend fun addUserToGroup(userId: String, groupId: String) {
        // No-op for fake implementation
    }

    override suspend fun removeUserFromGroup(userId: String, groupId: String) {
        // No-op for fake implementation
    }


}
