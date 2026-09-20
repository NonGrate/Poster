package com.example.poster.push

import com.example.poster.PostDatabase
import com.example.poster.model.AppNotification
import java.time.Instant
import java.util.UUID

data class Device(val token: String, val userId: String, val platform: String)

// No default database, for the reason given on PostsLocalRepository.
class NotificationsRepository(
    private val database: PostDatabase,
) {
    private val notifications = database.notificationQueries
    private val devices = database.deviceQueries

    fun record(userId: String, type: String, actor: String, postGuid: String? = null, groupId: String? = null): AppNotification {
        val guid = UUID.randomUUID().toString()
        val at = Instant.now().toString()
        notifications.insertNotification(guid, userId, type, actor, postGuid, groupId, at)
        return AppNotification(guid = guid, type = type, actor = actor, postGuid = postGuid, groupId = groupId, createdAt = at)
    }

    /** Newest first, with the post title and group name looked up so the app has something to show. */
    fun forUser(userId: String, limit: Long = 100): List<AppNotification> =
        notifications.notificationsForUser(userId, limit).executeAsList().map { row ->
            AppNotification(
                guid = row.guid,
                type = row.type,
                actor = row.actor,
                postGuid = row.post_guid,
                postTitle = row.post_guid?.let { database.postQueries.titleByGuid(it).executeAsOneOrNull() },
                groupId = row.group_id,
                groupName = row.group_id?.let { database.groupQueries.getGroupById(it).executeAsOneOrNull()?.name },
                createdAt = row.created_at,
                readAt = row.read_at,
            )
        }

    fun unread(userId: String): Int = notifications.countUnread(userId).executeAsOne().toInt()
    fun markAllRead(userId: String) = notifications.markAllRead(Instant.now().toString(), userId)

    fun registerDevice(userId: String, token: String, platform: String) =
        devices.upsertDevice(token, userId, platform, Instant.now().toString())
    fun unregisterDevice(token: String) = devices.deleteDevice(token)
    fun devicesFor(userId: String): List<Device> =
        devices.devicesForUser(userId).executeAsList().map { Device(it.token, it.user_id, it.platform) }
}
