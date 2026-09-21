package com.example.poster.accounts

import com.example.poster.auth.AuthService
import com.example.poster.authenticatedUserId
import com.example.poster.config.Features
import com.example.poster.domain.validation.AccountRules
import com.example.poster.domain.validation.ImageRules
import com.example.poster.model.*
import com.example.poster.uploads.UploadStore
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.JsonConvertException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * `/accounts` — your own account and nobody else's, inside the
 * bearer-authenticated block: fetching it, editing what a person may edit
 * about themselves, and deleting it with everything it wrote.
 */
internal fun Route.accountRoutes(
    accountRepository: AccountRepository,
    postsRepository: PostsRepository,
    authService: AuthService,
    uploadStore: UploadStore?,
) {
    route("/accounts") {
        // Listing every account — names, emails, roles — was reachable by
        // anybody signed in, and nothing in the app asked for it.
        get("/byId/{userId}") {
            val guid = call.parameters["userId"]
            if (guid == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            // Your own, and only your own — this returns an email, a role
            // and a status. The app restores its own session with it and
            // never asks about anybody else: it compares author ids rather
            // than looking people up.
            if (guid != call.authenticatedUserId()) {
                call.respond(HttpStatusCode.Forbidden)
                return@get
            }
            val user = accountRepository.userById(guid)
            if (user == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            call.respond(user)
        }
        // Looking an account up by email was here, guarded to your own
        // address — which made it a way of asking the server for something
        // you already knew. Nothing called it.
        post {
            try {
                val user = call.receive<User>()
                val currentUser = accountRepository.userById(call.authenticatedUserId())
                if (currentUser == null || user.guid != currentUser.guid) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@post
                }
                // The same limits registration enforces: editing a profile is
                // the other way an unbounded name or a broken email reaches
                // the store, and this route trusted the body as-is.
                val name = user.name.trim()
                val surname = user.surname.trim()
                val email = user.email.trim().lowercase()
                if (!AccountRules.nameValid(name) ||
                    !AccountRules.surnameValid(surname) ||
                    !AccountRules.emailValid(email)
                ) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError("Name, surname and a valid email are required"),
                    )
                    return@post
                }
                // Start from the stored account and apply only what a
                // person may change about themselves.
                //
                // This used to take the body as the new account and put the
                // password back, which meant `role` and `status` arrived
                // from the client: one POST with "role":"admin" made you an
                // admin, and a banned person could lift their own ban. An
                // allowlist cannot be got wrong the same way — a field
                // added to User later is not editable until it is named here.
                val languages = user.languages.filter(Language::isKnown).distinct()
                    .ifEmpty { currentUser.languages }
                // An avatar is an upload id this server stored (feature.authors + images).
                val photo = user.photo?.takeIf { Features.AUTHORS && Features.IMAGES }
                if (photo != null && (!ImageRules.isValidId(photo) || uploadStore?.file(photo) == null)) {
                    call.respond(HttpStatusCode.BadRequest, ApiError("Unknown picture"))
                    return@post
                }
                val previousPhoto = currentUser.photo
                accountRepository.addOrUpdateUser(
                    currentUser.copy(
                        name = name,
                        surname = surname,
                        email = email,
                        photo = photo,
                        languages = languages,
                        // The language your posts start in has to be one
                        // you actually read.
                        defaultLanguage = user.defaultLanguage.takeIf { it in languages }
                            ?: languages.first(),
                        // Their choice to be named on a post's liked-by list.
                        showName = user.showName,
                    ),
                )
                if (previousPhoto != null && previousPhoto != photo) uploadStore?.delete(previousPhoto)
                call.respond(HttpStatusCode.NoContent)
            } catch (ex: IllegalStateException) {
                call.respond(HttpStatusCode.BadRequest)
            } catch (ex: JsonConvertException) {
                call.respond(HttpStatusCode.BadRequest)
            }
        }
        delete("/{userId}") {
            val guid = call.parameters["userId"]
            if (guid == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@delete
            }
            if (guid != call.authenticatedUserId()) {
                call.respond(HttpStatusCode.Forbidden)
                return@delete
            }
            // Sessions first, so a request already in flight cannot write
            // something back after the rows are gone.
            authService.revokeAll(guid)
            val images = postsRepository.postsByAuthor(guid).mapNotNull { it.image }
            if (accountRepository.deleteAccountAndContent(guid)) {
                images.forEach { uploadStore?.delete(it) }
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }
}
