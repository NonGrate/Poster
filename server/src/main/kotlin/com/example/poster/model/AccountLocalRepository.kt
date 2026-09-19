package com.example.poster.model

import com.example.poster.db.DatabaseManager
import com.example.poster.db.DatabaseDriverFactory
import com.example.poster.PostDatabase

class AccountLocalRepository(
    private val database: PostDatabase = DatabaseManager(DatabaseDriverFactory()).getDatabase(),
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
            favoriteQueries.deleteFavoritesOfUser(guid)

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
            database.userPostFavoriteQueries.deleteFavoritesOfUser(from)

            database.userGroupQueries.reassignMemberships(into = into, from = from)
            database.userGroupQueries.deleteMembershipsOfUser(from)

            database.groupQueries.reassignGroupOwner(into = into, from = from)

            database.groupInviteQueries.reassignInviteCreatedBy(into = into, from = from)
            database.groupInviteQueries.reassignInviteUsedBy(into = into, from = from)

            // Reports are unique per (post, reporter): same OR IGNORE + clear.
            database.postReportQueries.reassignReporter(into = into, from = from)
            database.postReportQueries.deleteReportsByReporter(from)

            database.moderationAuditQueries.reassignAuditActor(into = into, from = from)

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
