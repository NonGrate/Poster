package com.example.poster.model

import kotlinx.serialization.Serializable

/** One line in the activity list. [type] is one of [NotificationType]. */
@Serializable
data class AppNotification(
    val guid: String,
    val type: String,
    val actor: String,
    val postGuid: String? = null,
    val postTitle: String? = null,
    val groupId: String? = null,
    val groupName: String? = null,
    val createdAt: String,
    val readAt: String? = null,
)

object NotificationType {
    /** Somebody liked your post. */
    const val LIKE = "like"
    /** Somebody commented on your post. */
    const val COMMENT = "comment"
    /** You were added to a group by its owner or an admin. */
    const val GROUP_ADDED = "group_added"
    /** Somebody joined a group you own. */
    const val GROUP_JOINED = "group_joined"
}

@Serializable
data class DeviceRegistration(val token: String, val platform: String)

@Serializable
data class UnreadCount(val unread: Int)
