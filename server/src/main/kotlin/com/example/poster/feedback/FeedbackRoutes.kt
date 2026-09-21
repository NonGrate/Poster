package com.example.poster.feedback

import com.example.poster.authenticatedUserId
import com.example.poster.domain.validation.FeedbackRules
import com.example.poster.model.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * `/feedback` — inside the bearer-authenticated block. Sending a note to the
 * operators, and reading back your own with any reply.
 *
 * [alert] is fired and forgotten; [adminBase] is where the link in it points.
 */
internal fun Route.feedbackRoutes(
    feedbackRepository: FeedbackRepository,
    accountRepository: AccountRepository,
    adminBase: String,
    alert: (String) -> Unit,
) {
    route("/feedback") {
        // Sending feedback. Trimmed and length-checked with the same rule
        // the form uses — the form is not a boundary, anything can post.
        post {
            val request = runCatching { call.receive<FeedbackRequest>() }.getOrNull()
            if (request == null) {
                call.respond(HttpStatusCode.BadRequest, ApiError("Malformed request"))
                return@post
            }
            val message = request.message.trim()
            if (!FeedbackRules.messageValid(message)) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiError("Feedback is 1 to ${FeedbackRules.MESSAGE_LIMIT} characters"),
                )
                return@post
            }
            val sender = call.authenticatedUserId()
            feedbackRepository.submit(sender, message)
            val who = accountRepository.userById(sender)?.email ?: sender
            val at = java.time.Instant.now().toString().take(16).replace('T', ' ')
            alert("💬 Feedback from $who · $at\n$message\n$adminBase/feedback")
            call.respond(HttpStatusCode.NoContent)
        }
        // The sender's own feedback, with any reply. Newest first.
        get {
            call.respond(feedbackRepository.forUser(call.authenticatedUserId()))
        }
    }
}
