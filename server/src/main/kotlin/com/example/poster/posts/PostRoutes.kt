package com.example.poster.posts

import com.example.poster.authenticatedUserId
import com.example.poster.comments.CommentsRepository
import com.example.poster.comments.commentRoutes
import com.example.poster.config.Features
import com.example.poster.domain.validation.ImageRules
import com.example.poster.domain.validation.PostRules
import com.example.poster.model.*
import com.example.poster.push.Notifier
import com.example.poster.uploads.UploadStore
import com.example.poster.withLikeCount
import com.example.poster.domain.validation.TagRules
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.JsonConvertException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.Clock
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * `/posts` — the feed and everything done to a single post, inside the
 * bearer-authenticated block. Comments hang off `/posts/{postId}/comments` and
 * are mounted here so they inherit the same authentication.
 *
 * [alert] is fired and forgotten; [adminBase] is where the link in it points.
 */
internal fun Route.postRoutes(
    postsRepository: PostsLocalRepository,
    accountRepository: AccountRepository,
    favoritesRepository: FavoritesRepository,
    commentsRepository: CommentsRepository,
    userGroupRepository: UserGroupRepository,
    reportsRepository: ReportsRepository,
    uploadStore: UploadStore?,
    notifier: Notifier?,
    adminBase: String,
    alert: (String) -> Unit,
) {
    route("/posts") {
        if (Features.COMMENTS) {
            commentRoutes(commentsRepository, postsRepository, accountRepository) { post, actor -> notifier?.commented(post, actor) }
        }

        get {
            // The feed obeys the languages this person reads. Their own
            // posts come back whatever language those are in — the query
            // exempts them, so nothing anybody wrote disappears on them.
            val viewer = call.authenticatedUserId()
            val languages = accountRepository.userById(viewer)?.languages ?: Language.ALL
            // Newest first, capped. A caller may ask for fewer; asking for
            // more than the cap gets the cap, because the point of a limit
            // nobody can raise is that it holds.
            val limit = call.request.queryParameters["limit"]
                ?.toIntOrNull()
                // Zero, negative or not a number is a caller with a bug
                // rather than somebody asking for nothing, so it reads as
                // no answer at all.
                ?.takeIf { it > 0 }
                ?: DEFAULT_FEED_LIMIT
            // Where the last page ended. Both halves or neither: a date
            // without its guid cannot resume from a minute holding two
            // posts, which is exactly when resuming matters.
            val beforeDate = call.request.queryParameters["beforeDate"]
            val beforeGuid = call.request.queryParameters["beforeGuid"]
            val before = if (!beforeDate.isNullOrBlank() && !beforeGuid.isNullOrBlank()) {
                FeedCursor(beforeDate, beforeGuid)
            } else {
                null
            }
            // Narrowing to the tags the reader has chosen, comma separated.
            // Blank reads as no filter rather than as a tag nothing carries.
            //
            // `tag` is still accepted, singular, because a client older
            // than multi-select sends it and its feed should keep filtering
            // rather than quietly showing everything.
            val tags = (call.request.queryParameters["tags"]
                ?: call.request.queryParameters["tag"])
                .orEmpty()
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                // The picker allows five; the query costs a scan per filter,
                // so a hand-written request asking for a thousand is work
                // nobody asked this server to do.
                .take(TagRules.MAX_IN_FILTER)
            // Narrowing to the rooms the reader has chosen, comma
            // separated, same shape as `tags`. The two are read together:
            // a group and a tag ask "from this room" and "about this",
            // and both have to hold.
            //
            // Unknown or foreign ids are not rejected. The query already
            // limits a reader to the rooms they belong to, so naming one
            // they do not returns nothing — and an error here would tell a
            // caller whether a group id exists, which is not theirs to
            // learn.
            val groups = call.request.queryParameters["groups"]
                .orEmpty()
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            // Capped: the search is an unindexed scan of every visible row,
            // and the cost rises with the needle as well as the haystack.
            // Nobody types two hundred characters into a search box.
            val query = call.request.queryParameters["q"].orEmpty().take(MAX_SEARCH_LENGTH)
            // Only people the reader follows. Signed out there is nobody to follow.
            val following = Features.FOLLOWS && Features.AUTHORS &&
                call.request.queryParameters["following"] == "true"
            val saved = Features.BOOKMARKS && call.request.queryParameters["saved"] == "true"
            val posts = postsRepository
                .visiblePosts(viewer, languages, limit, before, tags, groups, query, following, saved)
                .map { it.withLikeCount(favoritesRepository, commentsRepository, accountRepository) }
            call.respond(posts)
        }
        // Your own posts, all of them. The feed is a page and yours can
        // fall outside it, which left My Posts looking like the app had
        // thrown one away.
        get("/mine") {
            val viewer = call.authenticatedUserId()
            call.respond(postsRepository.postsByAuthor(viewer).map { it.withLikeCount(favoritesRepository, commentsRepository, accountRepository) })
        }
        get("/byId/{taskId}") {
            val guid = call.parameters["taskId"]
            if (guid == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            val viewer = call.authenticatedUserId()
            // Fetching by id must obey the same rules as the feed, or a
            // private post leaks to anyone who learns its id. Not-visible
            // reads as not-found: whether it exists is itself private.
            val post = postsRepository.visiblePostById(viewer, guid)
            if (post == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            call.respond(post.withLikeCount(favoritesRepository, commentsRepository, accountRepository))
        }
        post {
            try {
                val post = call.receive<Post>()
                val currentUserId = call.authenticatedUserId()
                // Writing needs a confirmed address; reading does not.
                //
                // Enforced here rather than by the app checking a flag: a
                // modified client can ignore a field, and can ignore a
                // status code just as easily, so the only thing that holds
                // is the server declining to write. The code is there so
                // the app knows to show the "confirm your email" screen
                // rather than a generic failure.
                if (Features.EMAIL_VERIFICATION_REQUIRED &&
                    accountRepository.userById(currentUserId)?.verifiedAt == null
                ) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ApiError(
                            message = "Confirm your email address before sharing a post",
                            code = ApiError.EMAIL_NOT_VERIFIED,
                        ),
                    )
                    return@post
                }
                val existingPost = postsRepository.postById(post.guid)
                if (post.author != currentUserId || (existingPost != null && existingPost.author != currentUserId)) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@post
                }
                // Visibility decides who ever sees this, so it is checked
                // rather than stored as sent: an unknown value matches no
                // clause in the feed's WHERE and the post goes nowhere.
                if (post.visibility !in POST_VISIBILITIES) {
                    call.respond(HttpStatusCode.BadRequest, ApiError("Unknown visibility"))
                    return@post
                }
                // With the feature off there is one kind of post.
                val visibility =
                    if (Features.POST_VISIBILITY) post.visibility else PostVisibility.PUBLIC
                // A group post goes into a room the writer is in. Without
                // this, naming any group id drops a post in front of every
                // one of its members — the feed serves it on membership
                // alone and never asks whether the author belongs there.
                if (visibility == PostVisibility.GROUP) {
                    val room = post.group
                    if (room == null) {
                        call.respond(HttpStatusCode.BadRequest, ApiError("A group post needs a group"))
                        return@post
                    }
                    if (!userGroupRepository.isMember(currentUserId, room)) {
                        call.respond(HttpStatusCode.Forbidden, ApiError("You are not in that group"))
                        return@post
                    }
                }
                // The form counts to the same number, but a form is not a
                // boundary: anything can post here, and a post nobody can
                // read past is the thing being prevented.
                if (PostRules.messageTooLong(post.message) ||
                    PostRules.titleTooLong(post.title)
                ) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError("A post is at most ${PostRules.MESSAGE_LIMIT} characters"),
                    )
                    return@post
                }
                // An image must be one this server stored; a made-up id
                // would be a post pointing at nothing, or at somebody else's
                // image. With images off the field is simply dropped.
                // `likes` and `comments` are counted from their own tables
                // on the way out, so what the body says about them is
                // dropped rather than written: otherwise a writer sets
                // their own like count. An existing row keeps its number.
                val saved = post.copy(
                    visibility = visibility,
                    image = if (uploadStore == null) null else post.image,
                    likes = existingPost?.likes ?: 0,
                    comments = 0,
                    // The feed orders by date, so the date a writer sends is a
                    // position in everybody else's feed. A date in the year
                    // 9999 pins a post to the top of page one for good, and a
                    // handful of them fill it — so anything ahead of now is
                    // brought back to now. Earlier dates are left alone on
                    // purpose: the fixtures and scripts/seed-demo-data.sh
                    // backdate posts to give a feed some spread, and a post
                    // sent to the back of the queue harms nobody.
                    date = post.date.coerceAtMost(Clock.System.now().toLocalDateTime(TimeZone.UTC)),
                )
                val image = saved.image
                if (image != null && (!ImageRules.isValidId(image) || uploadStore?.file(image) == null)) {
                    call.respond(HttpStatusCode.BadRequest, ApiError("Unknown image"))
                    return@post
                }
                postsRepository.addOrUpdatePost(saved)
                // Replacing or removing the picture leaves the old file with
                // no post; take it now rather than waiting for the sweep.
                val previousImage = existingPost?.image
                if (previousImage != null && previousImage != saved.image) uploadStore?.delete(previousImage)
                call.respond(HttpStatusCode.NoContent)
            } catch (ex: IllegalStateException) {
                call.respond(HttpStatusCode.BadRequest)
            } catch (ex: JsonConvertException) {
                call.respond(HttpStatusCode.BadRequest)
            }
        }
        // Completion is the author's to give: they are the only one who knows
        // how it went. Moderators can hide a post, not conclude it.
        if (Features.POST_COMPLETION) post("/{postId}/complete") {
            val guid = call.parameters["postId"]
            if (guid == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }
            val post = postsRepository.postById(guid)
            if (post == null) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }
            if (post.author != call.authenticatedUserId()) {
                call.respond(HttpStatusCode.Forbidden)
                return@post
            }
            val body = runCatching { call.receive<CompletePostRequest>() }.getOrNull()
            postsRepository.completePost(guid, body?.message?.trim()?.takeIf { it.isNotBlank() })
            call.respond(HttpStatusCode.NoContent)
        }

        if (Features.POST_COMPLETION) post("/{postId}/reopen") {
            val guid = call.parameters["postId"]
            if (guid == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }
            val post = postsRepository.postById(guid)
            if (post == null) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }
            if (post.author != call.authenticatedUserId()) {
                call.respond(HttpStatusCode.Forbidden)
                return@post
            }
            postsRepository.reopenPost(guid)
            call.respond(HttpStatusCode.NoContent)
        }

        /**
         * A share link for a post: mint (or reuse) its opaque token.
         *
         * Any signed-in person may share a PUBLIC post — it is already
         * public, and sharing is the point. Group and private posts are
         * refused: they are not for strangers, and the web page renders public
         * only, so a token must never exist for one. The token is unrelated to
         * the guid, so the URL it goes into cannot be turned back into an id.
         */
        if (Features.SHARING) post("/{postId}/share") {
            val guid = call.parameters["postId"]
            if (guid == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }
            val post = postsRepository.postById(guid)
            if (post == null) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }
            if (post.visibility != PostVisibility.PUBLIC) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiError("Only public posts can be shared"),
                )
                return@post
            }
            // Your own only. "Public" in the app means everyone signed in; a
            // share token means everyone at all, for good, with no way to take
            // it back — so it is the author's to mint and nobody else's.
            if (post.author != call.authenticatedUserId()) {
                call.respond(HttpStatusCode.Forbidden, ApiError("Only the author can share a post"))
                return@post
            }
            val token = postsRepository.ensureShareToken(guid) {
                newShareToken { candidate -> postsRepository.postByShareToken(candidate) != null }
            }
            call.respond(mapOf("token" to token))
        }

        /**
         * "This should not be here."
         *
         * Recorded, not acted on. In a small community app the usual reason a
         * post looks wrong is that somebody misread it, and hiding
         * something because one reader pressed a button would be a way to
         * silence the person it was written about.
         *
         * Answers the same whether this is the first report or the tenth,
         * and whether the post exists at all beyond the check below —
         * a caller learning which post ids are real is a caller mapping
         * other people's private posts.
         */
        if (Features.REPORTS) post("/{postId}/report") {
            val guid = call.parameters["postId"]
            val reporter = call.authenticatedUserId()
            if (guid == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }
            val reason = runCatching { call.receive<ReportRequest>().reason }.getOrNull()
            // Visible to the reporter, not merely existing: this used to take
            // any guid, so anybody holding a private post's id could push its
            // title into the operator's chat.
            val post = postsRepository.visiblePostById(reporter, guid)
            if (post != null && post.author != reporter) {
                val trimmedReason = reason?.take(500)?.takeIf { it.isNotBlank() }
                val isNew = reportsRepository.record(post.guid, reporter, trimmedReason)
                // Only the first report of a post alerts. The row is unique per
                // (post, reporter) already, but the message was sent every time,
                // so re-reporting in a loop buried every other alert.
                //
                // Ids and a link, no addresses and no post text: this goes to a
                // third party (Telegram), and the admin panel behind the link
                // shows the rest to somebody who has signed in for it.
                if (isNew) alert(buildString {
                    append("🚩 Report · post ${post.guid}\n")
                    append("reason: ${trimmedReason ?: "(none given)"}")
                    append("\n$adminBase/reports")
                })
            }
            call.respond(HttpStatusCode.NoContent)
        }

        delete("/{postId}") {
            val guid = call.parameters["postId"]
            if (guid == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@delete
            }
            val existingPost = postsRepository.postById(guid)
            if (existingPost != null && existingPost.author != call.authenticatedUserId()) {
                call.respond(HttpStatusCode.Forbidden)
                return@delete
            }
            if (postsRepository.removePost(guid)) {
                existingPost?.image?.let { uploadStore?.delete(it) }
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }
}

/**
 * An opaque token for a public share URL. Longer and lower-case than an invite
 * code — it is not read aloud, only clicked — and, like the invite code,
 * SecureRandom and unrelated to any id, so a share URL cannot be reversed into a
 * post's guid. [taken] reports a collision so a fresh one is drawn.
 */
internal fun newShareToken(taken: (String) -> Boolean): String {
    val alphabet = "abcdefghijkmnpqrstuvwxyz23456789"
    val random = java.security.SecureRandom()
    repeat(10) {
        val token = (1..12).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
        if (!taken(token)) return token
    }
    error("could not find an unused share token")
}

/** The visibilities a post may be sent in; anything else is a client with a bug. */
private val POST_VISIBILITIES =
    setOf(PostVisibility.PUBLIC, PostVisibility.GROUP, PostVisibility.PRIVATE)

/** The longest search a feed request may carry. */
private const val MAX_SEARCH_LENGTH = 100
