package com.example.poster

import com.example.poster.config.AppInfo
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.html.respondHtml
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import com.example.poster.auth.openInTheApp
import com.example.poster.auth.simplePage
import com.example.poster.model.PostsRepository
import kotlinx.html.p
import kotlinx.html.img
import io.ktor.server.response.respondFile
import com.example.poster.uploads.UploadStore

/**
 * The public web page a shared post link opens: poster.example.com/p/{token}.
 *
 * Read-only, and only ever a PUBLIC post. The token lookup filters to public
 * (Post.sq getPostByShareToken), so a group or private post can never
 * surface here even if a token somehow pointed at one. The page shows the title
 * and message and nothing that identifies the row — no guid, no author id — so a
 * share URL cannot be turned into anything that mutates the post. Its only
 * action is opening the app.
 */
fun Route.postSharePage(posts: PostsRepository, uploads: UploadStore? = null) {
    // The one place an image is served without a bearer token: a public post
    // that its author chose to share by link. The share token gates it, as it
    // gates the words.
    get("/p/{token}/image") {
        val token = call.parameters["token"].orEmpty()
        val post = token.takeIf { it.isNotBlank() }?.let { posts.postByShareToken(it) }
        val file = post?.image?.takeIf { post.visibility == "public" }?.let { uploads?.file(it) }
        if (file == null) call.respond(HttpStatusCode.NotFound) else call.respondFile(file.toFile())
    }
    get("/p/{token}") {
        val token = call.parameters["token"].orEmpty()
        val russian = call.prefersRussian()
        val post = token.takeIf { it.isNotBlank() }?.let { posts.postByShareToken(it) }
        if (post == null) {
            call.respondHtml(HttpStatusCode.NotFound) {
                simplePage(if (russian) "Пост не найден" else "Post not found") {
                    p {
                        +if (russian) {
                            "Эта ссылка больше не работает, или пост больше не публичный."
                        } else {
                            "This link no longer works, or the post is no longer public."
                        }
                    }
                }
            }
            return@get
        }
        call.respondHtml {
            simplePage(post.title) {
                p { +post.message }
                if (uploads != null && post.image != null) {
                    img(src = "/p/$token/image", alt = "") {
                        attributes["loading"] = "lazy"
                        attributes["style"] = "max-width:100%;border-radius:12px;display:block;margin:16px 0"
                    }
                }
                openInTheApp(
                    deepLink = "${AppInfo.SCHEME}://post/$token",
                    label = if (russian) "Открыть в ${AppInfo.NAME}" else "Open in ${AppInfo.NAME}",
                    hint = if (russian) {
                        "Откройте его в приложении ${AppInfo.NAME}, чтобы поставить лайк."
                    } else {
                        "Open it in the ${AppInfo.NAME} app to like it."
                    },
                )
                p("hint") {
                    +if (russian) {
                        "Публичный пост из ${AppInfo.NAME}."
                    } else {
                        "A public post shared from ${AppInfo.NAME}."
                    }
                }
            }
        }
    }
}
