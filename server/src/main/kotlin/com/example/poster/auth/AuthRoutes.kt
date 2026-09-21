package com.example.poster.auth

import com.example.poster.config.Features
import com.example.poster.config.AppInfo
import com.example.poster.config.BrandPalette
import com.auth0.jwt.JWT
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import com.example.poster.model.AppleExchangeResponse
import com.example.poster.model.AppleExchangeRequest
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.http.ContentType
import io.ktor.http.encodeURLParameter
import io.ktor.server.request.receive
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.auth.authenticate
import com.example.poster.authenticatedUserId
import com.example.poster.model.AccountRepository
import com.example.poster.model.ApiError
import com.example.poster.model.LoginRequest
import com.example.poster.model.MergeRequest
import com.example.poster.model.SocialSignInRequest
import com.example.poster.model.LogoutRequest
import com.example.poster.model.RefreshTokenRequest
import com.example.poster.model.RegisterRequest
import com.example.poster.model.TokenRequest
import com.example.poster.model.EmailRequest
import com.example.poster.model.PasswordResetRequest

fun Application.configureBearerAuthentication(
    config: AuthConfig,
    tokenService: TokenService,
    accountRepository: AccountRepository,
) {
    install(Authentication) {
        jwt("auth-jwt") {
            realm = config.realm
            verifier(tokenService.verifier)
            validate { credential ->
                val userId = credential.payload.subject
                // The role is re-read rather than trusted from the token, so a
                // ban takes effect on the next request instead of when the
                // 15-minute access token happens to run out.
                if (
                    credential.payload.getClaim("type").asString() == "access" &&
                    !userId.isNullOrBlank() &&
                    accountRepository.userById(userId)?.isBanned == false
                ) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, ApiError("Authentication required"))
            }
        }
    }
}

fun Route.authRoutes(
    authService: AuthService,
    accountMail: AccountMail? = null,
    accounts: AccountRepository? = null,
    /**
     * The providers this deployment can accept tokens from.
     *
     * Empty is an ordinary state: a build with no client id configured simply
     * does not offer social sign-in, and the route says so rather than
     * pretending to try.
     */
    socialVerifiers: List<SocialVerifier> = emptyList(),
    /**
     * Shared with the /delete-account page on purpose: that page checks the
     * same password, so a separate allowance there would simply be the way
     * around this one.
     */
    throttle: AttemptThrottle = AttemptThrottle(),
    /**
     * Registering separately, and not [throttle]: a shared allowance would let
     * somebody lock a person out of signing in by registering at their address
     * until the login's allowance was spent.
     */
    registerThrottle: AttemptThrottle = AttemptThrottle(),
    /** Holds the Apple identity token between the callback and the app asking for it. */
    appleCodes: AppleCodes = AppleCodes(),
) {
    route("/auth") {
        post("/register") {
            val request = call.receive<RegisterRequest>()
            // Every registration sends a verification email to an address the
            // caller chose, so an unlimited one is a way to post mail to
            // somebody else over this domain. Counted per address, like the
            // login's guesses.
            registerThrottle.retryAfter(request.email)?.let { wait ->
                call.response.headers.append(HttpHeaders.RetryAfter, wait.seconds.toString())
                call.respond(HttpStatusCode.TooManyRequests, ApiError("Too many attempts. Try again later."))
                return@post
            }
            registerThrottle.recordFailure(request.email)
            respondAuth {
                val response = authService.register(request)
                // After the account exists, and never in a way that can undo it:
                // somebody who registered successfully is registered, whatever
                // the mail provider is doing.
                accountMail?.sendVerification(
                    userId = response.user.guid,
                    email = response.user.email,
                    name = response.user.name,
                    // What they registered in. An older client sends none and
                    // the account falls back to English, same as everything else.
                    language = response.user.defaultLanguage,
                )
                response
            }
        }

        /**
         * Following the link from the verification email.
         *
         * A used, expired or invented token is refused the same way: telling
         * them apart tells somebody holding a stolen link which kind they hold.
         */
        post("/verify") {
            val token = runCatching { call.receive<TokenRequest>().token }.getOrNull()
            val verified = token != null && authService.verifyEmail(token)
            if (verified) call.respond(HttpStatusCode.NoContent)
            else call.respond(HttpStatusCode.BadRequest, ApiError("This link is no longer valid"))
        }

        /**
         * Asking for the verification email again.
         *
         * This lived behind the password, which was wrong twice over: somebody
         * already signed in has a token and should not have to type a password
         * to ask for an email about their own address, and somebody who is not
         * signed in has the reset flow, which verifies as a side effect. It is
         * now the signed-in person asking for their own, which cannot be
         * pointed at anybody else's inbox at all.
         */
        authenticate("auth-jwt") {
            post("/verify/resend") {
                val user = accounts?.userById(call.authenticatedUserId())
                if (user != null && user.verifiedAt == null) {
                    accountMail?.sendVerification(user.guid, user.email, user.name, user.defaultLanguage)
                }
                // The same answer whether it was sent, already confirmed, or
                // asked for too soon: nothing here is worth reporting back.
                call.respond(HttpStatusCode.NoContent)
            }
        }

        /**
         * Starting a password reset.
         *
         * Answers the same whether or not the address is one of ours, and
         * whether or not it was rate limited. Anything else turns this into a
         * way of asking which of your family has an account.
         */
        post("/password/forgot") {
            val email = runCatching { call.receive<EmailRequest>().email }.getOrNull()
            if (email != null && accountMail != null) {
                authService.accountFor(email)?.let { user ->
                    accountMail.sendPasswordReset(user.guid, user.email, user.name, user.defaultLanguage)
                }
            }
            call.respond(HttpStatusCode.NoContent)
        }

        /**
         * feature.magicLink. Asking is always 204: whether an address has an
         * account is not something this route tells strangers. Sign-in only —
         * an unknown address is told nothing and gets nothing; register first.
         */
        if (Features.MAGIC_LINK) post("/magic/request") {
            val email = runCatching { call.receive<EmailRequest>().email }.getOrNull()?.trim()?.lowercase()
            if (!email.isNullOrBlank() && accountMail != null) {
                authService.accountForMagicLink(email)?.let { user ->
                    accountMail.sendMagicLink(user.guid, user.email, user.name, user.defaultLanguage)
                }
            }
            call.respond(HttpStatusCode.NoContent)
        }

        /** Spending the link: a session, and the address confirmed on the way. */
        if (Features.MAGIC_LINK) post("/magic") {
            val token = runCatching { call.receive<TokenRequest>().token }.getOrNull()
            val session = token?.let { authService.signInWithMagicLink(it) }
            if (session == null) {
                call.respond(HttpStatusCode.BadRequest, ApiError("This link is no longer valid"))
            } else {
                call.respond(session)
            }
        }

        /** Finishing one: the token proves the inbox, the body carries the new password. */
        post("/password/reset") {
            val request = runCatching { call.receive<PasswordResetRequest>() }.getOrNull()
            if (request == null) {
                call.respond(HttpStatusCode.BadRequest, ApiError("This link is no longer valid"))
                return@post
            }
            when (authService.resetPassword(request.token, request.password)) {
                PasswordResetOutcome.DONE -> call.respond(HttpStatusCode.NoContent)
                PasswordResetOutcome.WEAK_PASSWORD -> call.respond(
                    HttpStatusCode.BadRequest,
                    ApiError("Password must contain between 8 and 128 characters"),
                )
                PasswordResetOutcome.BAD_TOKEN -> call.respond(
                    HttpStatusCode.BadRequest,
                    ApiError("This link is no longer valid"),
                )
            }
        }
        /**
         * Signing in with a token somebody else issued.
         *
         * Every reason a token can be refused answers the same way — 401, no
         * detail. Telling a caller which check failed tells an attacker which
         * check to work on next, and none of it is actionable by the person
         * holding the phone.
         */
        post("/social") {
            val request = try {
                call.receive<SocialSignInRequest>()
            } catch (malformed: Exception) {
                call.respond(HttpStatusCode.BadRequest, ApiError("Malformed request"))
                return@post
            }
            call.application.log.info("social sign-in attempt: provider=${request.provider}")
            val verifier = socialVerifiers.firstOrNull {
                it.provider.equals(request.provider, ignoreCase = true) && it.enabled
            }
            if (verifier == null) {
                call.respond(
                    HttpStatusCode.NotImplemented,
                    ApiError("This app was not built with ${request.provider} sign-in"),
                )
                return@post
            }
            val account = try {
                verifier.verify(request.idToken, request.nonce)
            } catch (refused: SocialSignInException) {
                // The reason helps whoever runs this server and helps an
                // attacker just as much, so it is logged here but never
                // reaches the caller.
                call.application.log.warn(
                    "social sign-in refused (${request.provider}): ${refused.message} " +
                        "[${idTokenClaimsForLog(request.idToken)}]",
                )
                call.respond(HttpStatusCode.Unauthorized, ApiError("Could not sign you in"))
                return@post
            }
            respondAuth { authService.signInWithProvider(account) }
        }
        /**
         * Apple's redirect target for the Android/web flow.
         *
         * Android has no native Apple SDK, so the app opens Apple's sign-in in a
         * browser; Apple POSTs the result here (`response_mode=form_post`). This
         * endpoint does not verify or sign anything — it hands the identity token
         * straight back to the app through the `poster://` deep link, and the
         * app then calls /auth/social like any other sign-in, where the token is
         * verified (audience = the Service ID). The `state` the app generated
         * comes back untouched so the app can match this to its own request; a
         * token addressed to the wrong audience, or with a state the app never
         * issued, is refused there. Nothing here is logged: it carries a token.
         */
        post("/apple/callback") {
            val params = call.receiveParameters()
            val state = params["state"].orEmpty()
            val error = params["error"]
            // The identity token does not travel in this URL. A custom scheme
            // is first-come on Android, so anything put here is readable by
            // whichever app claimed it; what goes back is a one-time code the
            // app redeems with the verifier only it holds (AppleCodes).
            val challenge = AppleCodes.challengeIn(state)
            val deepLink = buildString {
                append("${AppInfo.SCHEME}://auth/apple?state=").append(state.encodeURLParameter())
                when {
                    error != null -> append("&error=").append(error.encodeURLParameter())
                    // A client from before the exchange existed. Answered with
                    // an error rather than the token it is expecting: sending
                    // the token to an older app would leave the hole open to
                    // anybody who asked for it in that shape.
                    //
                    // Written as a sentence because an older app puts whatever
                    // arrives here straight into what it shows the person, and
                    // that build is already out — its wording cannot be changed
                    // now, only what it is handed.
                    challenge == null -> append("&error=")
                        .append("update ${AppInfo.NAME} to sign in with Apple".encodeURLParameter())
                    else -> append("&code=").append(
                        appleCodes.mint(params["id_token"].orEmpty(), challenge).encodeURLParameter(),
                    )
                }
            }
            // A page that bounces to the app rather than a bare 302: a redirect
            // to a custom scheme after a cross-site POST is handled unevenly by
            // in-app browsers, while location.replace from a loaded page is not,
            // and the link is there for the rare case script is blocked.
            // The brand's ink, so re-theming this app does not leave one
            // page behind in the old colour.
            val ink = "#%06X".format(BrandPalette.Light.onBackground and 0xFFFFFF)
            call.respondText(ContentType.Text.Html) {
                """<!doctype html><html><head>
<meta name="viewport" content="width=device-width, initial-scale=1"><title>${AppInfo.NAME}</title></head>
<body style="font-family:-apple-system,system-ui,sans-serif;text-align:center;padding:3rem;color:$ink">
<p>Signing you in…</p>
<p><a href="$deepLink">Return to ${AppInfo.NAME}</a></p>
<script>window.location.replace("$deepLink");</script>
</body></html>"""
            }
        }

        /**
         * Trading the callback's one-time code for the identity token.
         *
         * Unauthenticated, because this happens before there is a session —
         * the token it returns is what the caller then signs in with. What
         * guards it is the verifier: the code alone, which is all an app that
         * merely intercepted the redirect has, redeems nothing. One attempt
         * per code, whether or not the verifier was right.
         */
        post("/apple/exchange") {
            val request = runCatching { call.receive<AppleExchangeRequest>() }.getOrNull()
            if (request == null || request.code.isBlank() || request.verifier.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ApiError("Malformed request"))
                return@post
            }
            val idToken = appleCodes.redeem(request.code, request.verifier)
            if (idToken.isNullOrBlank()) {
                call.respond(HttpStatusCode.Unauthorized, ApiError("That sign-in could not be completed"))
                return@post
            }
            call.respond(AppleExchangeResponse(idToken))
        }
        /**
         * "I already have an account."
         *
         * Signed in as the account somebody wants to keep, holding a token
         * from the provider they just used. Both halves are proved: the JWT
         * for one, the provider's signature for the other. Nothing moves —
         * this only writes down that the two are the same person, which is why
         * it is safe in a way that merging two populated accounts is not.
         */
        authenticate("auth-jwt") {
            post("/social/link") {
                val request = try {
                    call.receive<SocialSignInRequest>()
                } catch (malformed: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiError("Malformed request"))
                    return@post
                }
                val verifier = socialVerifiers.firstOrNull {
                    it.provider.equals(request.provider, ignoreCase = true) && it.enabled
                }
                if (verifier == null) {
                    call.respond(
                        HttpStatusCode.NotImplemented,
                        ApiError("This app was not built with ${request.provider} sign-in"),
                    )
                    return@post
                }
                val account = try {
                    verifier.verify(request.idToken, request.nonce)
                } catch (refused: SocialSignInException) {
                    call.application.log.warn(
                        "social link refused (${request.provider}): ${refused.message} " +
                            "[${idTokenClaimsForLog(request.idToken)}]",
                    )
                    call.respond(HttpStatusCode.Unauthorized, ApiError("Could not link that account"))
                    return@post
                }
                when (authService.linkIdentityTo(call.authenticatedUserId(), account)) {
                    LinkOutcome.LINKED -> call.respond(HttpStatusCode.NoContent)
                    // Deliberately not "that belongs to account X": whoever is
                    // asking has just proved they hold the provider account, so
                    // they can be told it is spoken for, and nothing more.
                    LinkOutcome.ALREADY_SOMEBODY_ELSES -> call.respond(
                        HttpStatusCode.Conflict,
                        ApiError("That sign-in is already linked to another account"),
                    )
                    LinkOutcome.NO_SUCH_ACCOUNT -> call.respond(HttpStatusCode.Unauthorized, ApiError("Not signed in"))
                }
            }
            /**
             * Merge the current account into an existing one.
             *
             * Signed in as the account being given up (typically a fresh
             * Apple/private-relay one), the caller proves they own the other
             * account with its password or a provider token. Everything moves to
             * the survivor, the current account is deleted, and the reply is a
             * fresh session for the survivor. This is the deliberate sibling of
             * /social/link — it moves two populated accounts together.
             */
            post("/social/merge") {
                val request = try {
                    call.receive<MergeRequest>()
                } catch (malformed: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiError("Malformed request"))
                    return@post
                }
                // The email+password branch of a merge verifies a password, so
                // it is a place to guess one. Without this it was the only such
                // place with no limit, and a guess there is worth more than a
                // guess at /login: it hands back a session for the account that
                // was guessed. The same bucket as /login, because it is the
                // same secret being worked on.
                val guessedEmail = request.email
                    ?.takeIf { !request.password.isNullOrBlank() }
                    ?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
                if (guessedEmail != null) {
                    throttle.retryAfter(guessedEmail)?.let { wait ->
                        call.response.headers.append(HttpHeaders.RetryAfter, wait.seconds.toString())
                        call.respond(
                            HttpStatusCode.TooManyRequests,
                            ApiError("Too many attempts. Try again later."),
                        )
                        return@post
                    }
                }
                when (val outcome = authService.mergeCurrentInto(
                    call.authenticatedUserId(), request, socialVerifiers,
                )) {
                    is MergeOutcome.Merged -> {
                        guessedEmail?.let(throttle::clear)
                        call.respond(HttpStatusCode.OK, outcome.response)
                    }
                    MergeOutcome.NoTarget -> {
                        guessedEmail?.let(throttle::recordFailure)
                        call.respond(
                            HttpStatusCode.Unauthorized,
                            ApiError("Couldn't find that account — check the details and try again"),
                        )
                    }
                    MergeOutcome.SameAccount -> call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError("That is the account you are already signed in to"),
                    )
                    MergeOutcome.TargetBanned -> call.respond(
                        HttpStatusCode.Forbidden,
                        ApiError("That account is not available"),
                    )
                    MergeOutcome.ProviderUnavailable -> call.respond(
                        HttpStatusCode.NotImplemented,
                        ApiError("This app was not built with ${request.provider} sign-in"),
                    )
                }
            }
        }

        post("/login") {
            val request = call.receive<LoginRequest>()
            throttle.retryAfter(request.email)?.let { wait ->
                call.response.headers.append(HttpHeaders.RetryAfter, wait.seconds.toString())
                call.respond(HttpStatusCode.TooManyRequests, ApiError("Too many attempts. Try again later."))
                return@post
            }
            try {
                val authenticated = authService.login(request)
                throttle.clear(request.email)
                call.respond(authenticated)
            } catch (invalid: AuthException.InvalidCredentials) {
                throttle.recordFailure(request.email)
                // The same answer as before it was throttled: which attempt was
                // the last allowed one is not the caller's business.
                call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid email or password"))
            }
        }
        post("/refresh") {
            respondAuth {
                authService.refresh(call.receive<RefreshTokenRequest>().refreshToken)
            }
        }
        post("/logout") {
            val request = call.receive<LogoutRequest>()
            authService.logout(request.refreshToken)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

/**
 * The claims from a refused id token that are safe to write to a log.
 *
 * Only `aud`, `iss` and `exp`: the client id, the issuer and the expiry, which
 * are what a refusal turns on and none of which is personal data. Deliberately
 * NOT the raw token, `email`, `sub` or `name` — those are PII and never belong
 * in a log. The token is decoded without verifying it: this is only reading back
 * what was claimed, to say why it was rejected, not trusting any of it.
 */
private fun idTokenClaimsForLog(idToken: String): String = try {
    val jwt = JWT.decode(idToken)
    "aud=${jwt.audience}, iss=${jwt.issuer}, exp=${jwt.expiresAt}"
} catch (unparseable: Exception) {
    "unparseable token"
}

private suspend fun io.ktor.server.routing.RoutingContext.respondAuth(block: suspend () -> Any) {
    try {
        call.respond(block())
    } catch (exception: AuthException.EmailAlreadyRegistered) {
        call.respond(HttpStatusCode.Conflict, ApiError(exception.message.orEmpty()))
    } catch (exception: AuthException.InvalidRegistration) {
        call.respond(HttpStatusCode.BadRequest, ApiError(exception.message.orEmpty()))
    } catch (exception: AuthException.InvalidCredentials) {
        call.respond(HttpStatusCode.Unauthorized, ApiError(exception.message.orEmpty()))
    } catch (exception: AuthException.InvalidRefreshToken) {
        call.respond(HttpStatusCode.Unauthorized, ApiError(exception.message.orEmpty()))
    }
}
