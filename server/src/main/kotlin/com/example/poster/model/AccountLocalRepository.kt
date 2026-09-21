package com.example.poster.model

import com.example.poster.PostDatabase
import com.example.poster.config.Features

// No default database, for the reason given on PostsLocalRepository.
class AccountLocalRepository(
    private val database: PostDatabase,
) : AccountRepository {
    private val userQueries = database.userQueries
    private val identityQueries = database.socialIdentityQueries

    override fun allUsers(): List<User> {
        return userQueries.getAllUsers().executeAsList().map { it.toModel() }
    }

    override fun userById(guid: String): User? {
        val users = userQueries.getUserById(guid).executeAsList()
        return if (users.isNotEmpty()) {
            users.first().toModel()
        } else {
            null
        }
    }

    override fun userByPhoto(photo: String): User? = userQueries.getUserByPhoto(photo).executeAsOneOrNull()?.toModel()

    override fun userByEmail(email: String): User? {
        val users = userQueries.getUserByEmail(email).executeAsList()
        return if (users.isNotEmpty()) {
            users.first().toModel()
        } else {
            null
        }
    }

    override fun userByIdentity(provider: String, subject: String): User? =
        identityQueries.userForIdentity(provider, subject).executeAsList().firstOrNull()?.toModel()

    override fun linkIdentity(provider: String, subject: String, userId: String, email: String?) {
        identityQueries.linkIdentity(
            provider = provider,
            subject = subject,
            user_id = userId,
            email = email,
            linked_at = java.time.Instant.now().toString(),
        )
    }

    override fun addOrUpdateUser(user: User) {
        val existingUser = userById(user.guid)

        if (existingUser != null) {
            userQueries.updateUser(
                name = user.name,
                surname = user.surname,
                email = user.email,
                password_hash = user.passwordHash,
                photo = user.photo,
                role = user.role,
                status = user.status,
                languages = Language.store(user.languages),
                default_language = user.defaultLanguage,
                verified_at = user.verifiedAt,
                show_name = if (user.showName) 1L else 0L,
                guid = user.guid,
            )
        } else {
            userQueries.insertUser(
                guid = user.guid,
                name = user.name,
                surname = user.surname,
                email = user.email,
                password_hash = user.passwordHash,
                photo = user.photo,
                role = user.role,
                status = user.status,
                languages = Language.store(user.languages),
                default_language = user.defaultLanguage,
                verified_at = user.verifiedAt,
                show_name = if (user.showName) 1L else 0L,
            )
        }
    }

    override fun deleteAccountAndContent(guid: String): Boolean {
        if (userById(guid) == null) return false
        database.transaction {
            val postQueries = database.postQueries
            val favoriteQueries = database.userPostFavoriteQueries

            // Their posts, and everything hanging off each one: the tags, and
            // anybody else's mark on it. Those marks belong to other people but
            // point at something that is about to stop existing.
            val mine = postQueries.postGuidsByAuthor(guid).executeAsList()
            mine.forEach { postGuid ->
                database.postTagQueries.removeAllTagsFromPost(postGuid)
                favoriteQueries.deleteFavoritesOfPost(postGuid)
            }
            postQueries.deletePostsByAuthor(guid)

            // What they marked on other people's posts, which stays theirs
            // to lose rather than a record anybody else needs.
            favoriteQueries.deleteFavoritesForUser(guid)

            // The groups they made outlive them: other people are in
            // them, and their posts are in them. What goes is the claim of
            // ownership, which would otherwise point at nobody.
            database.groupQueries.clearOwner(guid)

            database.userGroupQueries.deleteMembershipsOfUser(guid)
            database.socialIdentityQueries.deleteIdentitiesOfUser(guid)
            database.refreshTokenQueries.deleteRefreshTokensForUser(guid)
            userQueries.deleteUser(guid)
        }
        return true
    }

    override fun mergeAccountInto(from: String, into: String): Boolean {
        if (userById(from) == null || userById(into) == null) return false
        database.transaction {
            // Re-point every reference from the merged account to the survivor
            // first — never route its data through the delete queries, which
            // remove rather than move. Foreign keys are off, so each table is
            // handled explicitly.
            database.postQueries.reassignPostAuthor(into = into, from = from)

            // Favourites and memberships have a (user, x) primary key: OR IGNORE
            // drops the rows the survivor already has, then the leftovers on the
            // merged id are cleared.
            database.userPostFavoriteQueries.reassignFavorites(into = into, from = from)
            database.userPostFavoriteQueries.deleteFavoritesForUser(from)

            database.userGroupQueries.reassignMemberships(into = into, from = from)
            database.userGroupQueries.deleteMembershipsOfUser(from)

            database.groupQueries.reassignGroupOwner(into = into, from = from)

            database.groupInviteQueries.reassignInviteCreatedBy(into = into, from = from)
            database.groupInviteQueries.reassignInviteUsedBy(into = into, from = from)

            // Reports are unique per (post, reporter): same OR IGNORE + clear.
            database.postReportQueries.reassignReporter(into = into, from = from)
            database.postReportQueries.deleteReportsByReporter(from)

            database.moderationAuditQueries.reassignAuditActor(into = into, from = from)

            // Everything below hangs off User with ON DELETE CASCADE, so the
            // deleteUser at the end takes it rather than leaving it behind:
            // without these the merge silently threw away the comments, saved
            // posts, follows, activity and registered devices of the account
            // being folded in. Each is gated the same way its routes are.
            if (Features.COMMENTS) {
                database.commentQueries.reassignCommentAuthor(into = into, from = from)
            }
            if (Features.FOLLOWS) {
                // (follower, followed) is the primary key, so OR IGNORE drops
                // what the survivor already has; the leftovers go, and so does
                // the self-follow left behind if one of the two followed the
                // other.
                database.followQueries.reassignFollower(into = into, from = from)
                database.followQueries.reassignFollowed(into = into, from = from)
                database.followQueries.deleteFollowsOfUser(from)
                database.followQueries.deleteSelfFollows()
            }
            if (Features.BOOKMARKS) {
                database.bookmarkQueries.reassignBookmarks(into = into, from = from)
                database.bookmarkQueries.deleteBookmarksOfUser(from)
            }
            if (Features.PUSH_NOTIFICATIONS) {
                database.notificationQueries.reassignNotifications(into = into, from = from)
                database.notificationQueries.reassignNotificationActor(into = into, from = from)
                database.deviceQueries.reassignDevices(into = into, from = from)
            }

            // The identities are the point: the merged account's Apple identity
            // now belongs to the survivor, so future Apple sign-ins land there.
            database.socialIdentityQueries.reassignIdentities(into = into, from = from)
            database.refreshTokenQueries.deleteRefreshTokensForUser(from)

            userQueries.deleteUser(from)
        }
        return true
    }

    override fun removeUser(guid: String): Boolean {
        val existingUser = userById(guid)
        if (existingUser != null) {
            userQueries.deleteUser(guid)
            return true
        }
        return false
    }
}
