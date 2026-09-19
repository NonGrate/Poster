package com.example.poster.model

interface GroupRepository {
    fun allGroups(): List<Group>
    fun groupById(id: String): Group?
    fun groupByInviteCode(inviteCode: String): Group?
    fun addOrUpdateGroup(group: Group)
    fun removeGroup(id: String): Boolean

    /** How many this person has created. The cap on creating them reads this. */
    fun countOwnedBy(userId: String): Long
}

