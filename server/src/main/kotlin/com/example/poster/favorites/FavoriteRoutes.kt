package com.example.poster.favorites

import com.example.poster.authenticatedUserId
import com.example.poster.comments.CommentsRepository
import com.example.poster.model.*
import com.example.poster.push.Notifier
import com.example.poster.withLikeCount
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * `/favorites` — liking a post, inside the bearer-authenticated block.
 *
 * Every route here is about the caller, so none of them takes a user id: the
 * paths used to carry one that had to equal the token's subject, which is a
 * parameter with exactly one legal value and a 403 for everybody who got it
 * wrong. [authenticatedUserId] is the same fact without the chance to disagree.
 */
internal fun Route.favoriteRoutes(
    favoritesRepository: FavoritesRepository,
    postsRepository: PostsRepository,
    commentsRepository: CommentsRepository,
    accountRepository: AccountRepository,
    notifier: Notifier?,
) {
    route("/favorites") {
        get("/me") {
            val posts = favoritesRepository.getUserFavoritePosts(call.authenticatedUserId())
            call.respond(posts.map { it.withLikeCount(favoritesRepository, commentsRepository, accountRepository) })
        }
        get("/check/{postId}") {
            val userId = call.authenticatedUserId()
            val postId = call.parameters["postId"]
            if (postId == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            // Asking about a post you cannot see reads as not-found,
            // the same as fetching it — see /bookmarks.
            if (postsRepository.visiblePostById(userId, postId) == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            val isFavorite = favoritesRepository.isPostFavorite(userId, postId)
            call.respond(isFavorite)
        }
        post("/{postId}") {
            val userId = call.authenticatedUserId()
            val postId = call.parameters["postId"]
            if (postId == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }
            if (postsRepository.visiblePostById(userId, postId) == null) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }
            favoritesRepository.addFavoritePost(userId, postId)
            postsRepository.postById(postId)?.let { notifier?.liked(it, userId) }
            call.respond(HttpStatusCode.NoContent)
        }
        delete("/{postId}") {
            val userId = call.authenticatedUserId()
            val postId = call.parameters["postId"]
            if (postId == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@delete
            }
            if (postsRepository.visiblePostById(userId, postId) == null) {
                call.respond(HttpStatusCode.NotFound)
                return@delete
            }
            if (favoritesRepository.removeFavoritePost(userId, postId)) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
        // Who liked a given post was readable by anybody, for
        // any post. That is a list of people beside a subject they may
        // not have chosen to be associated with, and no screen shows it —
        // the cards show a count, which is the next route down.
        get("/count/{postId}") {
            val postId = call.parameters["postId"]
            if (postId == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            if (postsRepository.visiblePostById(call.authenticatedUserId(), postId) == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            val count = favoritesRepository.countPostFavorites(postId)
            call.respond(count)
        }
        // The roster the detail screen shows: only the names of people who
        // opted in (User.showName), newest first, plus the total so the UI
        // can add "and N more people". Signed-in only (this block is under
        // authenticate), so it is not the open, anybody-can-read list the
        // count route's comment warns about.
        get("/post/{postId}/people") {
            val postId = call.parameters["postId"]
            if (postId == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            if (postsRepository.visiblePostById(call.authenticatedUserId(), postId) == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            call.respond(favoritesRepository.likers(postId))
        }
    }
}
