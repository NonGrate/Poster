package com.example.poster

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import kotlinx.serialization.Serializable
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import com.example.poster.model.AccountLocalRepository
import com.example.poster.model.Group
import com.example.poster.model.GroupLocalRepository
import com.example.poster.model.TagLocalRepository
import com.example.poster.model.Tag
import com.example.poster.model.User
import com.example.poster.model.UserGroupLocalRepository
import com.example.poster.PostDatabase
import com.example.poster.auth.PasswordHasher

internal fun Route.debugFixtureRoutes(
    enabled: Boolean,
    confirmAddress: (email: String) -> Boolean = { false },
    createGroup: (id: String, name: String, inviteCode: String) -> Unit = { _, _, _ -> },
    // Seeds a post's "who liked" roster with demo friends (some named,
    // some not, backdated). Returns false when the post is not there.
    seedLiked: (postId: String) -> Boolean = { false },
    // Last, so the caller can pass it as a trailing lambda as it always has.
    applyFixtures: () -> Unit,
) {
    if (!enabled) return

    route("/debug/fixtures") {
        post("/integration") {
            applyFixtures()
            call.respond(HttpStatusCode.NoContent)
        }

        /**
         * Marks an address confirmed, for seeding.
         *
         * Writing a post needs a confirmed address, and a seed script has no
         * inbox to read. Without this the demo content simply never appears:
         * every post is refused and the screenshots come out of an empty app,
         * which is what had been happening.
         *
         * Behind the same flag as the rest of this file, which is off in
         * production — see where debugFixtureRoutes is called.
         */
        /**
         * Creates a group, for seeding.
         *
         * Making one is the admin panel's job — it was taken off the public API
         * because any signed-in person could overwrite a group's invite
         * code or delete it outright. A seed script has no admin session, so
         * without this the demo screenshots have no group to post into, and
         * the one they used to find was simply left over in the database.
         *
         * Behind the same flag as the rest of this file, which is off in
         * production — see where debugFixtureRoutes is called.
         */
        post("/group") {
            val body = runCatching { call.receive<GroupRequest>() }.getOrNull()
            if (body == null || body.id.isBlank() || body.name.isBlank()) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }
            createGroup(body.id, body.name, body.inviteCode)
            call.respond(HttpStatusCode.NoContent)
        }

        post("/confirm") {
            val email = runCatching { call.receive<ConfirmRequest>().email }.getOrNull()
            if (email.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }
            if (confirmAddress(email.trim().lowercase())) call.respond(HttpStatusCode.NoContent)
            else call.respond(HttpStatusCode.NotFound)
        }

        // Populates a post's liked-by roster so the detail screen has something
        // to photograph. Behind the same off-in-production flag as the rest.
        post("/liking") {
            val postId = runCatching { call.receive<LikedSeedRequest>().postId }.getOrNull()
            if (postId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }
            if (seedLiked(postId)) call.respond(HttpStatusCode.NoContent)
            else call.respond(HttpStatusCode.NotFound)
        }
    }
}

@Serializable
private data class ConfirmRequest(val email: String)

@Serializable
private data class LikedSeedRequest(val postId: String)

/**
 * Seeds a post's "who liked" roster with demo friends.
 *
 * Three chose to be named and the rest are counted, backdated so the detail
 * screen reads "added N days ago" rather than everyone today. Idempotent: the
 * friend accounts key on a fixed guid, and favourites ignore duplicates.
 */
internal fun applyLikedFixture(
    postId: String,
    accountRepository: AccountLocalRepository,
    database: PostDatabase,
    passwordHasher: PasswordHasher,
) {
    data class Friend(val name: String, val surname: String, val show: Boolean, val daysAgo: Long)
    val friends = listOf(
        Friend("Adam", "Ford", true, 5),
        Friend("Grace", "Okoye", true, 2),
        Friend("Daniel", "Petrov", true, 0),
        Friend("Ruth", "Bello", false, 1),
        Friend("Mark", "Adeyemi", false, 3),
        Friend("Sarah", "Kim", false, 4),
        Friend("John", "Osei", false, 6),
        Friend("Lydia", "Costa", false, 8),
    )
    val now = java.time.Instant.now()
    friends.forEachIndexed { index, friend ->
        val guid = "liking.friend.$index@example.com"
        accountRepository.addOrUpdateUser(
            User(
                guid = guid,
                name = friend.name,
                surname = friend.surname,
                email = guid,
                passwordHash = passwordHasher.hash("password123"),
                photo = null,
                verifiedAt = now.toString(),
                showName = friend.show,
            )
        )
        database.userPostFavoriteQueries.addFavoritePost(
            guid, postId, now.minusSeconds(friend.daysAgo * 86_400).toString(),
        )
    }
}

@Serializable
private data class GroupRequest(val id: String, val name: String, val inviteCode: String)

internal fun applyIntegrationFixtures(
    database: PostDatabase,
    accountRepository: AccountLocalRepository,
    tagRepository: TagLocalRepository,
    groupRepository: GroupLocalRepository,
    userGroupRepository: UserGroupLocalRepository,
    passwordHasher: PasswordHasher,
) {
    database.transaction {
        database.userPostFavoriteQueries.deleteAllFavorites()
        database.postTagQueries.deleteAllPostTags()
        database.postQueries.deleteAllPosts()
        database.tagQueries.deleteAllTags()
        database.userGroupQueries.deleteAllUserGroups()
        database.groupQueries.deleteAllGroups()
        database.refreshTokenQueries.deleteAllRefreshTokens()
        database.userQueries.deleteAllUsers()
    }

    accountRepository.addOrUpdateUser(
        User(
            guid = "test@example.com",
            name = "Test",
            surname = "User",
            email = "test@example.com",
            passwordHash = passwordHasher.hash("password123"),
            photo = null,
            // Seeded accounts are established ones: the app's own suites create
            // posts, and posting needs a confirmed address.
            verifiedAt = "2026-08-18T12:00:00Z",
        )
    )
    accountRepository.addOrUpdateUser(
        User(
            guid = "user2@example.com",
            name = "User",
            surname = "Two",
            email = "user2@example.com",
            passwordHash = passwordHasher.hash("password123"),
            photo = null,
            // Seeded accounts are established ones: the app's own suites create
            // posts, and posting needs a confirmed address.
            verifiedAt = "2026-08-18T12:00:00Z",
        )
    )

    val groupA = Group("group-a", "Group A", "GROUP_A_INVITE")
    val youthGroup = Group("book-club", "Book Club", "BOOK_CLUB_INVITE")
    groupRepository.addOrUpdateGroup(groupA)
    groupRepository.addOrUpdateGroup(youthGroup)
    userGroupRepository.addUserToGroup("test@example.com", groupA.id)
    // Joining spends a row in GroupInvite; the code on the group itself is only
    // what `groups/byInvite` resolves. Without these rows the suites' "join by
    // code" flows get "not valid any more" from a fresh database.
    val seededAt = java.time.Instant.now().toString()
    for (group in listOf(groupA, youthGroup)) {
        database.groupInviteQueries.deleteInvitesForGroup(group.id)
        userGroupRepository.createInvite(group.id, "user2@example.com", group.inviteCode, seededAt)
    }

    // The curated set, because that is what the app offers now — a fixture that
    // wiped it left the picker with nothing to pick.
    tagRepository.seedCuratedTags()
}
