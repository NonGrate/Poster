package com.example.poster.push

import com.example.poster.config.Features
import com.example.poster.config.AppInfo
import com.example.poster.model.AccountRepository
import com.example.poster.model.Group
import com.example.poster.model.NotificationType
import com.example.poster.model.Post
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Turns "something happened to your post / group" into a row in the activity
 * list and a push to each of the person's devices. Nothing here is awaited by
 * a request: [scope] is the application's, so a slow APNs never slows a like.
 * Nobody is told about their own actions.
 */
class Notifier(
    private val notifications: NotificationsRepository,
    private val accounts: AccountRepository,
    private val senders: Map<String, PushSender>,
    private val scope: CoroutineScope,
    private val log: (String) -> Unit = ::println,
) {
    fun liked(post: Post, actor: String) {
        if (post.author == actor) return
        deliver(post.author, NotificationType.LIKE, actor, postGuid = post.guid, postTitle = post.title)
    }

    fun commented(post: Post, actor: String) {
        if (post.author == actor) return
        deliver(post.author, NotificationType.COMMENT, actor, postGuid = post.guid, postTitle = post.title)
    }

    /** [member] was put into [group] by [actor] (owner, admin, or the admin panel). */
    fun addedToGroup(member: String, group: Group, actor: String) {
        if (member == actor) return
        deliver(member, NotificationType.GROUP_ADDED, actor, groupId = group.id, groupName = group.name)
    }

    /** Somebody joined by invite; the owner hears about it. */
    fun joinedGroup(group: Group, member: String) {
        val owner = group.owner ?: return
        if (owner == member) return
        deliver(owner, NotificationType.GROUP_JOINED, member, groupId = group.id, groupName = group.name)
    }

    private fun deliver(
        recipient: String,
        type: String,
        actor: String,
        postGuid: String? = null,
        postTitle: String? = null,
        groupId: String? = null,
        groupName: String? = null,
    ) {
        val recorded = runCatching { notifications.record(recipient, type, actor, postGuid, groupId) }
            .onFailure { log("push: could not record notification: ${it.message}") }
            .getOrNull() ?: return
        val devices = notifications.devicesFor(recipient)
        if (devices.isEmpty()) return
        val language = accounts.userById(recipient)?.defaultLanguage ?: "en"
        val message = PushMessage(
            title = AppInfo.NAME,
            body = body(type, language, postTitle, groupName),
            data = buildMap {
                put("type", type)
                put("notificationGuid", recorded.guid)
                postGuid?.let { put("postGuid", it) }
                groupId?.let { put("groupId", it) }
            },
        )
        scope.launch {
            devices.forEach { device ->
                val sender = senders[device.platform] ?: return@forEach
                val keep = runCatching { sender.send(device.token, message) }.getOrDefault(true)
                if (!keep) notifications.unregisterDevice(device.token)
            }
        }
    }

    /** Server-side copy, in the recipient's reading language: the push arrives before the app can translate anything. */
    internal fun body(type: String, language: String, postTitle: String?, groupName: String?): String {
        val ru = language == "ru"
        // The title only when the build asks for it (feature.pushPostTitles).
        // A push goes through Apple or Google, who see its text, and lands on a
        // lock screen anybody holding the phone can read — so what it says
        // about the post is a choice the person deploying this makes, not one
        // the code should make for them.
        val quoted = postTitle?.takeIf { Features.PUSH_POST_TITLES }?.let { " “$it”" } ?: ""
        return when (type) {
            NotificationType.LIKE -> if (ru) "Кому-то понравился ваш пост$quoted" else "Somebody liked your post$quoted"
            NotificationType.COMMENT -> if (ru) "Новый комментарий к вашему посту$quoted" else "New comment on your post$quoted"
            NotificationType.GROUP_ADDED -> if (ru) "Вас добавили в группу ${groupName ?: ""}".trim() else "You were added to ${groupName ?: "a group"}"
            NotificationType.GROUP_JOINED -> if (ru) "Кто-то присоединился к группе ${groupName ?: ""}".trim() else "Somebody joined ${groupName ?: "your group"}"
            else -> if (ru) "Что-то новое в ${AppInfo.NAME}" else "Something new in ${AppInfo.NAME}"
        }
    }
}
