package com.example.poster.comments

import com.example.poster.authenticatedUserId
import com.example.poster.displayAuthor
import com.example.poster.config.Features
import com.example.poster.domain.validation.CommentRules
import com.example.poster.model.AccountRepository
import com.example.poster.model.ApiError
import com.example.poster.model.CommentRequest
import com.example.poster.model.PostsRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * `/posts/{postId}/comments` — inside the bearer-authenticated `/posts` route.
 * A comment is visible to whoever may see the post ([PostsRepository.visiblePostById]
 * decides, as it does for the post itself), may be written by anybody who can
 * see it with a confirmed address, and removed by its author or the post's.
 */
fun Route.commentRoutes(
    comments: CommentsRepository,
    posts: PostsRepository,
    accounts: AccountRepository,
    /** Called with the post and the commenter after a comment is stored; the notifier listens. */
    onCommented: (post: com.example.poster.model.Post, actor: String) -> Unit = { _, _ -> },
) {
    route("/{postId}/comments") {
        get {
            val viewer = call.authenticatedUserId()
            val postId = call.parameters["postId"].orEmpty()
            if (posts.visiblePostById(viewer, postId) == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            call.respond(comments.forPost(postId).map { it.withAuthor(accounts) })
        }

        post {
            val viewer = call.authenticatedUserId()
            val postId = call.parameters["postId"].orEmpty()
            val post = posts.visiblePostById(viewer, postId)
            if (post == null) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }
            if (Features.EMAIL_VERIFICATION_REQUIRED && accounts.userById(viewer)?.verifiedAt == null) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    ApiError("Confirm your email address before commenting", ApiError.EMAIL_NOT_VERIFIED),
                )
                return@post
            }
            val text = runCatching { call.receive<CommentRequest>() }.getOrNull()?.text?.trim()
            if (text == null || !CommentRules.textValid(text)) {
                call.respond(HttpStatusCode.BadRequest, ApiError("A comment is 1 to ${CommentRules.TEXT_LIMIT} characters"))
                return@post
            }
            val added = comments.add(postId, viewer, text)
            onCommented(post, viewer)
            call.respond(added.withAuthor(accounts))
        }

        delete("/{commentId}") {
            val viewer = call.authenticatedUserId()
            val postId = call.parameters["postId"].orEmpty()
            val comment = comments.byId(call.parameters["commentId"].orEmpty())
            if (comment == null || comment.postGuid != postId) {
                call.respond(HttpStatusCode.NotFound)
                return@delete
            }
            val post = posts.postById(postId)
            // The post's author keeps their own thread: they may take down what
            // was said under their words, not only what they said themselves.
            if (comment.author != viewer && post?.author != viewer) {
                call.respond(HttpStatusCode.Forbidden)
                return@delete
            }
            comments.remove(comment.guid)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private fun com.example.poster.model.Comment.withAuthor(accounts: AccountRepository): com.example.poster.model.Comment {
    val (name, photo) = accounts.displayAuthor(author) ?: return this
    return copy(authorName = name, authorPhoto = photo)
}

