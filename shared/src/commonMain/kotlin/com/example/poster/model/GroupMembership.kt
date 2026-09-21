package com.example.poster.model

import kotlinx.serialization.Serializable

/**
 * Somebody in a group, as the other members see them.
 *
 * A name and nothing else. A group is people who already know each other,
 * so a name is enough to recognise somebody by — and an address is the one
 * thing a member did not choose to hand to everybody else in the room.
 */
@Serializable
data class GroupMember(
    val id: String,
    val name: String,
    val isOwner: Boolean = false,
    /** Promoted to admin by the owner. The owner is an admin without this flag. */
    val isAdmin: Boolean = false,
)

/**
 * One invitation, good once.
 *
 * [usedByName] rather than an id: the owner is reading a list to see whether
 * the invite they sent Anna was the one Anna used, and an id answers nothing.
 * Null while the invite is unspent.
 */
@Serializable
data class GroupInvite(
    val code: String,
    val groupId: String,
    val createdAt: String,
    val usedByName: String? = null,
    val usedAt: String? = null,
    val revokedAt: String? = null,
    /**
     * The address it was emailed to, or null for one made by hand.
     *
     * Shown so an owner reading the list can tell the two apart: one of these
     * is already with the person it is for, and the other is still theirs to
     * hand over.
     */
    val sentTo: String? = null,
) {
    /** Whether it can still let somebody in. */
    val live: Boolean get() = usedAt == null && revokedAt == null
}
