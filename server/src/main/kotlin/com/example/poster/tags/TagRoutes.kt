package com.example.poster.tags

import com.example.poster.model.TagRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

/** `/tags` — the curated list, inside the bearer-authenticated block. */
internal fun Route.tagRoutes(tagRepository: TagRepository) {
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
