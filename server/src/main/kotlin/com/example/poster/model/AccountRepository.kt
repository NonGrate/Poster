package com.example.poster.model

interface AccountRepository {
    fun allUsers(): List<User>
    fun userById(guid: String): User?
    fun userByEmail(email: String): User?

    /** The addresses of banned accounts, for spotting one coming back. */
    fun bannedEmails(): List<String> = emptyList()
    /** The account whose avatar is this upload id, or null. */
    fun userByPhoto(photo: String): User? = null
    fun addOrUpdateUser(user: User)
    fun removeUser(guid: String): Boolean

    /**
     * Everything this person leaves behind, gone, in one transaction.
     *
     * Not [removeUser]: that deletes the row in User and nothing else, which
     * leaves their posts in the feed under an author nobody can look up. The
     * posts are the reason somebody deletes an account — they are where the
     * pets and the marriage and the children were written down.
     *
     * Written out rather than left to ON DELETE CASCADE, because SQLite has
     * foreign keys off unless a connection turns them on and none of ours does.
     */
    fun deleteAccountAndContent(guid: String): Boolean

    /**
     * The account a provider's own id for somebody belongs to, or null.
     *
     * Asked before the email is, because the email is the thing that moves:
     * see SocialIdentity.sq.
     */
    fun userByIdentity(provider: String, subject: String): User?

    /** Remembers that this provider identity is this account, from now on. */
    fun linkIdentity(provider: String, subject: String, userId: String, email: String?)

    /**
     * Fold [from] into [into] and delete [from], in one transaction.
     *
     * Every row keyed to [from] — posts, comments, favourites, bookmarks,
     * follows, memberships, owned groups, invites, reports, notifications,
     * devices, its social identities, audit entries — is
     * moved to [into] first, deduping where a pair is unique, so nothing is lost
     * and nothing is left pointing at a deleted id. This is the merge that
     * [deleteAccountAndContent] deliberately is not: it moves rather than
     * destroys, then removes only the now-empty [from]. False if either is
     * missing.
     */
    fun mergeAccountInto(from: String, into: String): Boolean
}