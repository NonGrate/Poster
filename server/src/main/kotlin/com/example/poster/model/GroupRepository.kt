package com.example.poster.model

interface GroupRepository {
    fun allGroups(): List<Group>
    fun groupById(id: String): Group?
    fun groupByInviteCode(inviteCode: String): Group?
    fun addOrUpdateGroup(group: Group)
    /** Groups anybody may find (feature.publicGroups), with member counts. */
    fun publicGroups(): List<Group> = emptyList()
    fun setVisibility(id: String, visibility: String) = Unit
    fun removeGroup(id: String): Boolean

    /** How many this person has created. The cap on creating them reads this. */
    fun countOwnedBy(userId: String): Long
}

