package com.example.poster.uploads

import com.example.poster.authenticatedUserId
import com.example.poster.config.Features
import com.example.poster.domain.validation.ImageRules
import com.example.poster.model.AccountLocalRepository
import com.example.poster.model.ApiError
import com.example.poster.model.PostsRepository
import com.example.poster.model.UploadResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import java.time.Duration
import java.time.Instant
import kotlin.io.path.getLastModifiedTime

/**
 * `POST /uploads` (multipart, one `file` part) → `{ "id": … }`, and
 * `GET /uploads/{id}`. Both sit inside the bearer-authenticated block: images
 * are never public here. The one public exposure is the share page's
 * `/p/{token}/image`, and only for a public post.
 *
 * Who may read an image follows the post it is on — the same
 * [PostsRepository.visiblePostById] rule the post itself is served under. An
 * image no post carries yet (the form uploads before it saves) is readable by
 * any signed-in account for [UploadStore.PENDING_GRACE]; the id is 128 random
 * bits, so "readable" means "by whoever the uploader shows it to", and after
 * the grace the sweep removes it.
 */
fun Route.uploadRoutes(
    store: UploadStore,
    posts: PostsRepository,
    accounts: AccountLocalRepository,
) {
    route("/uploads") {
        post {
            val userId = call.authenticatedUserId()
            if (Features.EMAIL_VERIFICATION_REQUIRED && accounts.userById(userId)?.verifiedAt == null) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    ApiError("Confirm your email address before adding an image", ApiError.EMAIL_NOT_VERIFIED),
                )
                return@post
            }
            var bytes: ByteArray? = null
            // One byte past the limit is enough to know it is over; reading
            // the whole of an oversized upload would be doing the sender's
            // bidding.
            val limit = ImageRules.MAX_BYTES.toLong() + 1
            call.receiveMultipart(formFieldLimit = limit).forEachPart { part ->
                if (part is PartData.FileItem && bytes == null) {
                    bytes = part.provider().readRemaining(limit).readByteArray()
                }
                part.dispose()
            }
            val data = bytes
            if (data == null || data.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, ApiError("Send the image as a multipart part named 'file'"))
                return@post
            }
            if (ImageRules.tooLarge(data)) {
                call.respond(
                    HttpStatusCode.PayloadTooLarge,
                    ApiError("An image is at most ${ImageRules.MAX_BYTES / (1024 * 1024)} MB"),
                )
                return@post
            }
            val extension = ImageRules.extensionOf(data)
            if (extension == null) {
                call.respond(HttpStatusCode.BadRequest, ApiError("Only JPEG, PNG or WebP images"))
                return@post
            }
            call.respond(UploadResponse(store.save(data, extension)))
        }

        get("/{id}") {
            val id = call.parameters["id"].orEmpty()
            val file = store.file(id)
            if (file == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            val owner = posts.postByImage(id)
            val allowed = if (owner == null && accounts.userByPhoto(id) != null) {
                // Somebody's avatar: shown beside their posts, so any signed-in reader may see it.
                true
            } else if (owner == null) {
                file.getLastModifiedTime().toInstant().isAfter(Instant.now().minus(UploadStore.PENDING_GRACE))
            } else {
                posts.visiblePostById(call.authenticatedUserId(), owner.guid) != null
            }
            if (!allowed) {
                // Not Forbidden: that would confirm there is something here.
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            call.response.header(HttpHeaders.CacheControl, "private, max-age=86400")
            call.respondFile(file.toFile())
        }
    }
}
