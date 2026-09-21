package com.example.poster.tags

import com.example.poster.model.TagRepository
import com.example.poster.authenticatedUserId
import io.ktor.http.HttpStatusCode
import com.example.poster.model.PostsRepository
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

/** `/tags` — the curated list, inside the bearer-authenticated block. */
internal fun Route.tagRoutes(
    tagRepository: TagRepository,
    /** Null leaves /forPost open, which is only right when there is nothing to hide. */
    postsRepository: PostsRepository? = null,
) {
    route("/tags") {
        get {
            val tags = tagRepository.allTags()
            call.respond(tags)
        }
        get("/byName/{query}") {
            val query = call.parameters["query"]
            if (query == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            val tags = tagRepository.tagsByName(query)
            call.respond(tags)
        }
        get("/forPost/{postId}") {
            val postId = call.parameters["postId"]
            if (postId == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            // The same visibility rule the post itself answers to. Without it
            // this told any signed-in caller what a private post is about —
            // which is what the write routes below were removed for.
            if (postsRepository?.visiblePostById(call.authenticatedUserId(), postId) == null &&
                postsRepository != null
            ) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            val tags = tagRepository.getTagsForPost(postId)
            call.respond(tags)
        }
        // Adding and removing a post's tags used to be here, taking a
        // post id from anybody: a post's tags could be changed, or
        // stripped, by someone who did not write it — and with filtering,
        // retagging one changes who finds it. Tags travel with the post
        // through POST /posts, which checks the author, and nothing in
        // the app ever called these.
    }
}
