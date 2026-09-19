package com.example.poster.model

import kotlinx.serialization.Serializable

@Serializable
data class Group(
    val id: String,
    val name: String,
    val inviteCode: String,
    /**
     * Who created it, or null for the ones the admin panel made.
     *
     * Defaulted so an older client's JSON still parses, and so the many places
     * that build a Group without caring who owns it do not have to say so.
     */
    val owner: String? = null,
    /**
     * What to call the owner, for the people who are only members.
     *
     * A guid says nothing to somebody deciding whether to write into a room.
     * Null where there is nobody to name — a group the admin panel made, or
     * one whose owner has since deleted their account — and the screen then
     * says nothing rather than showing a blank.
     */
    val ownerName: String? = null,
    /**
     * The reader's own role in this group — "admin", "member", or null when
     * not known (built without a viewer, an older client's JSON). Filled by
     * getUserGroups, which is always scoped to the caller. Lets the screen
     * offer Manage to an admin, not only the owner.
     */
    val myRole: String? = null,
)
