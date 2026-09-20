package com.example.poster.social

import com.example.poster.authenticatedUserId
import com.example.poster.config.Features
import com.example.poster.model.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * `/bookmarks` and `/follows` — the two "quiet" lists a reader keeps about
 * other people's things, inside the bearer-authenticated block. Each is gated
 * on its own flag, so one can ship without the other.
 */
internal fun Route.socialRoutes(
    bookmarksRepository: BookmarksLocalRepository,
    followsRepository: FollowsLocalRepository,
    postsRepository: PostsRepository,
    accountRepository: AccountRepository,
) {
    if (Features.BOOKMARKS) route("/bookmarks") {
        // The ids this reader saved; the app matches them against the feed.
        get { call.respond(bookmarksRepository.of(call.authenticatedUserId())) }
        post("/{postId}") {
            val me = call.authenticatedUserId()
            val postId = call.parameters["postId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
            // Saving what you cannot see reads as not-found, the same as fetching it.
            if (postsRepository.visiblePostById(me, postId) == null) return@post call.respond(HttpStatusCode.NotFound)
            bookmarksRepository.add(me, postId)
            call.respond(HttpStatusCode.NoContent)
        }
        delete("/{postId}") {
            val postId = call.parameters["postId"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            bookmarksRepository.remove(call.authenticatedUserId(), postId)
            call.respond(HttpStatusCode.NoContent)
        }
    }

    if (Features.FOLLOWS && Features.AUTHORS) route("/follows") {
        // The ids this reader follows; the app matches them against post authors.
        get { call.respond(followsRepository.following(call.authenticatedUserId())) }
        post("/{userId}") {
            val me = call.authenticatedUserId()
            val target = call.parameters["userId"]
            when {
                target == null || target == me -> call.respond(HttpStatusCode.BadRequest)
                accountRepository.userById(target) == null -> call.respond(HttpStatusCode.NotFound)
                else -> { followsRepository.follow(me, target); call.respond(HttpStatusCode.NoContent) }
            }
        }
        delete("/{userId}") {
            val target = call.parameters["userId"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            followsRepository.unfollow(call.authenticatedUserId(), target)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
