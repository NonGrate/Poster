package com.example.poster

import com.example.poster.comments.CommentsRepository
import com.example.poster.config.Features
import com.example.poster.model.AccountRepository
import com.example.poster.model.FavoritesRepository
import com.example.poster.model.Group
import com.example.poster.model.Post
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal

/**
 * The few things more than one route package needs. Everything that belongs to
 * a single block lives with that block; these do not, and a copy each is how
 * two screens end up disagreeing about the same post.
 */

/** The account behind the bearer token. Internal so the auth routes share it. */
internal fun ApplicationCall.authenticatedUserId(): String =
    checkNotNull(principal<JWTPrincipal>()?.payload?.subject)

/**
 * feature.authors: how somebody is named beside something they wrote, or null
 * when the flag is off or the account has gone. Shared so a post and a comment
 * name the same person the same way. A blank surname (an account made by a
 * sign-in link) shows as the name alone.
 */
internal fun AccountRepository.displayAuthor(id: String): Pair<String, String?>? {
    if (!Features.AUTHORS) return null
    val user = userById(id) ?: return null
    return "${user.name} ${user.surname}".trim() to user.photo
}

/** feature.authors: the writer's name and avatar go out with the post. */
internal fun Post.withAuthor(accounts: AccountRepository): Post {
    val (name, photo) = accounts.displayAuthor(author) ?: return this
    return copy(authorName = name, authorPhoto = photo)
}

/**
 * The stored `likes` column is not a count anybody should be shown — the
 * favorites table is the authority, and comments are counted from theirs — so
 * both are filled in on the way out. One copy, because two that drift apart
 * are two screens disagreeing about the same post.
 */
internal fun Post.withLikeCount(
    favorites: FavoritesRepository,
    comments: CommentsRepository,
    accounts: AccountRepository,
): Post = copy(
    likes = favorites.countPostFavorites(guid).toInt(),
    comments = if (Features.COMMENTS) comments.countFor(guid) else 0,
).withAuthor(accounts)

/**
 * An invite code nobody can guess and anybody can read out loud.
 *
 * No vowels, so it cannot spell anything; no 0/O or 1/I/L, because these get
 * read down a phone line and written on paper. Retried on collision rather than
 * trusted to be unique — the space is large but the table is small, and a
 * duplicate would silently put somebody in the wrong group.
 *
 * Here rather than with the group routes because the admin panel mints codes
 * too, and its own copy of this function is how the two drifted apart once.
 */
internal const val INVITE_ALPHABET = "BCDFGHJKMNPQRSTVWXYZ23456789"
internal const val INVITE_CODE_LENGTH = 8

internal fun newInviteCode(taken: (String) -> Group?): String {
    val alphabet = INVITE_ALPHABET
    // SecureRandom, not Random.default. This code is the whole of the security
    // on a group — the comment above says nobody can guess it, and that is
    // only true of a generator built to resist guessing. The admin panel's copy
    // of this function had it right; this one did not.
    val random = java.security.SecureRandom()
    repeat(10) {
        val code = (1..INVITE_CODE_LENGTH).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
        if (taken(code) == null) return code
    }
    error("could not find an unused invite code")
}
