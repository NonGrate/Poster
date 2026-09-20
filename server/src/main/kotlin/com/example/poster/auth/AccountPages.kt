package com.example.poster.auth

import com.example.poster.config.AppInfo
import com.example.poster.config.Features
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import com.example.poster.model.AccountRepository
import com.example.poster.model.LoginRequest
import io.ktor.http.HttpHeaders
import com.example.poster.prefersRussian
import kotlinx.html.*

/**
 * The pages the links in the emails lead to.
 *
 * They are served rather than deep-linked into the app because a link in an
 * email is opened wherever the email is read — a laptop, somebody else's phone,
 * a webmail tab — and a page works in all of those. Deep links are worth adding
 * on top later, when Stage 12 has the signing fingerprint it is waiting for;
 * they are not a replacement, because the person who opened the email on a
 * desktop still needs somewhere to land.
 *
 * Nothing here is behind authentication: the token in the link is the whole
 * credential, which is why it is single use and short lived.
 */
fun Route.accountPages(
    authService: AuthService,
    accounts: AccountRepository,
    throttle: AttemptThrottle = AttemptThrottle(),
    /**
     * The Google verifier, when the server has one, and the client id its
     * tokens must be minted for.
     *
     * Both or neither: the button is only drawn when there is something able to
     * check what it produces. A button that always fails is worse than no
     * button, and this page is somebody's last resort.
     */
    googleVerifier: SocialVerifier? = null,
    googleClientId: String = "",
) {

    deleteAccountPages(authService, accounts, throttle, googleVerifier, googleClientId)

    /**
     * A page with a button rather than doing the work on sight.
     *
     * Mail providers and antivirus scanners follow links in messages before a
     * person does — Outlook's Safe Links is the common one. A GET that spends a
     * single-use token would be burned by the scanner, and the person clicking
     * afterwards would be told their link had already been used, which is both
     * baffling and true.
     */
    get("/verify") {
        val token = call.request.queryParameters["token"].orEmpty()
        val copy = pageCopy(call.prefersRussian())
        call.respondHtml {
            simplePage(copy.verifyHeading) {
                if (token.isBlank()) {
                    p { +copy.missingCode }
                } else {
                    p { +copy.verifyPrompt }
                    form(action = "/verify", method = FormMethod.post) {
                        hiddenInput(name = "token") { value = token }
                        submitInput { value = copy.verifyButton }
                    }
                    openInTheApp("${AppInfo.SCHEME}://verify?token=$token", copy.openInApp, copy.openInAppHint)
                }
            }
        }
    }

    post("/verify") {
        val token = call.receiveParameters()["token"].orEmpty()
        val verified = authService.verifyEmail(token)
        val copy = pageCopy(call.prefersRussian())
        call.respondHtml(if (verified) HttpStatusCode.OK else HttpStatusCode.BadRequest) {
            simplePage(if (verified) copy.confirmedHeading else copy.deadHeading) {
                if (verified) {
                    p { +copy.confirmedBody }
                    openInTheApp("${AppInfo.SCHEME}://verified", copy.openInApp, copy.openInAppHint)
                } else {
                    // Deliberately one message for expired, spent and invented:
                    // telling them apart tells somebody holding a stolen link
                    // which kind they hold.
                    p { +copy.deadUsed }
                    p { +copy.deadAskAgain }
                }
            }
        }
    }

    get("/reset") {
        val token = call.request.queryParameters["token"].orEmpty()
        val copy = pageCopy(call.prefersRussian())
        call.respondHtml {
            simplePage(copy.resetHeading) {
                if (token.isBlank()) {
                    p { +copy.missingCode }
                } else {
                    form(action = "/reset", method = FormMethod.post) {
                        hiddenInput(name = "token") { value = token }
                        label { +copy.resetLabel }
                        passwordInput(name = "password") { required = true; minLength = "8" }
                        submitInput { value = copy.resetButton }
                    }
                    p { +copy.resetRules }
                    openInTheApp("${AppInfo.SCHEME}://reset?token=$token", copy.openInApp, copy.openInAppHint)
                }
            }
        }
    }

    /**
     * Where the sign-in email points. The link is for the app: the page only
     * hands the token over (feature.magicLink). Nothing is spent by loading it,
     * so a mail client that prefetches links does not burn the sign-in.
     */
    if (Features.MAGIC_LINK) get("/magic") {
        val token = call.request.queryParameters["token"].orEmpty()
        val copy = pageCopy(call.prefersRussian())
        call.respondHtml {
            simplePage(copy.magicHeading) {
                if (token.isBlank()) {
                    p { +copy.missingCode }
                } else {
                    p { +copy.magicBody }
                    openInTheApp("${AppInfo.SCHEME}://magic?token=$token", copy.openInApp, copy.openInAppHint)
                }
            }
        }
    }

    post("/reset") {
        val params = call.receiveParameters()
        val outcome = authService.resetPassword(
            token = params["token"].orEmpty(),
            newPassword = params["password"].orEmpty(),
        )
        val status = if (outcome == PasswordResetOutcome.DONE) HttpStatusCode.OK else HttpStatusCode.BadRequest
        val copy = pageCopy(call.prefersRussian())
        call.respondHtml(status) {
            when (outcome) {
                PasswordResetOutcome.DONE -> simplePage(copy.changedHeading) {
                    p { +copy.changedBody }
                }
                PasswordResetOutcome.WEAK_PASSWORD -> simplePage(copy.tooShortHeading) {
                    p { +copy.tooShortRule }
                    p { +copy.tooShortRetry }
                }
                PasswordResetOutcome.BAD_TOKEN -> simplePage(copy.deadHeading) {
                    p { +copy.deadUsed }
                    p { +copy.deadAskAgain }
                }
            }
        }
    }
}

/**
 * A way through to the app, for somebody reading this on the phone it is
 * installed on.
 *
 * A link rather than a redirect: a browser asked to open a scheme nothing
 * handles shows an error, and on a laptop nothing handles it by definition.
 * Offering it means the page still works everywhere and the app is there for
 * whoever can use it — which is the whole reason the page exists as well.
 */
/**
 * Deleting an account from the web, which Google Play requires of any app that
 * lets somebody make one: the way out has to be reachable without installing
 * the app, and without asking anybody for help.
 *
 * The password is the authorisation. Nothing here is behind a session, because
 * the person may well be on a laptop that has never signed in — which is the
 * whole point of the page existing.
 */
private fun Route.deleteAccountPages(
    authService: AuthService,
    accounts: AccountRepository,
    throttle: AttemptThrottle,
    googleVerifier: SocialVerifier?,
    googleClientId: String,
) {
    val googleOffered = googleVerifier?.enabled == true && googleClientId.isNotBlank()

    get("/delete-account") {
        val copy = pageCopy(call.prefersRussian())
        call.respondHtml { deleteAccountForm(copy, problem = null, googleClientId.takeIf { googleOffered }) }
    }

    post("/delete-account") {
        val params = call.receiveParameters()
        val copy = pageCopy(call.prefersRussian())
        val email = params["email"]?.trim().orEmpty()
        val password = params["password"].orEmpty()
        val credential = params["credential"].orEmpty()
        val offered = googleClientId.takeIf { googleOffered }

        // The tick is the "are you sure", so it is checked before any
        // credential rather than after: somebody who has not confirmed should
        // be told so without a password or a token being tried at all.
        if (params["confirm"] != "on") {
            call.respondHtml(HttpStatusCode.BadRequest) {
                deleteAccountForm(copy, problem = copy.deleteUnconfirmed, offered)
            }
            return@post
        }

        // Signed in with Google rather than a password.
        //
        // This page existed only for people who have one, and an account made
        // through a provider never does: signInWithProvider stores a hash of a
        // random UUID so the column is not empty, and nobody can type it. They
        // were told their details did not match, which was true and useless.
        if (credential.isNotBlank()) {
            val verifier = googleVerifier
            val identity = if (verifier == null) null else runCatching { verifier.verify(credential) }.getOrNull()
            val user = identity?.let { authService.accountForProvider(it) }
            if (user == null) {
                // Same message as a wrong password, for the same reason: this
                // page must not become a way of asking who has an account.
                call.respondHtml(HttpStatusCode.Unauthorized) {
                    deleteAccountForm(copy, problem = copy.deleteMismatch, offered)
                }
                return@post
            }
            accounts.deleteAccountAndContent(user.guid)
            call.respondHtml { simplePage(copy.deletedHeading) { p { +copy.deletedBody } } }
            return@post
        }

        throttle.retryAfter(email)?.let { wait ->
            call.response.headers.append(HttpHeaders.RetryAfter, wait.seconds.toString())
            call.respondHtml(HttpStatusCode.TooManyRequests) {
                deleteAccountForm(copy, problem = copy.deleteTooMany, offered)
            }
            return@post
        }

        val user = try {
            authService.login(LoginRequest(email = email, password = password)).user
        } catch (invalid: AuthException.InvalidCredentials) {
            throttle.recordFailure(email)
            // One message for a wrong address and a wrong password alike:
            // saying which was wrong turns this page into a way of asking
            // whether somebody has an account here.
            call.respondHtml(HttpStatusCode.Unauthorized) {
                deleteAccountForm(copy, problem = copy.deleteMismatch, offered)
            }
            return@post
        }

        throttle.clear(email)
        accounts.deleteAccountAndContent(user.guid)
        call.respondHtml { simplePage(copy.deletedHeading) { p { +copy.deletedBody } } }
    }
}

/**
 * [googleClientId] non-null draws the Google button; null leaves it out
 * entirely rather than drawing one that cannot work.
 */
private fun HTML.deleteAccountForm(copy: PageCopy, problem: String?, googleClientId: String?) {
    simplePage(copy.deleteHeading) {
        problem?.let { p("problem") { +it } }
        p { +copy.deleteWhatGoes }
        p { +copy.deleteWhatStays }
        form(action = "/delete-account", method = FormMethod.post) {
            id = "password-form"
            label { htmlFor = "email"; +copy.deleteEmailLabel }
            emailInput(name = "email") { id = "email"; required = true }
            label { htmlFor = "password"; +copy.deletePasswordLabel }
            passwordInput(name = "password") { id = "password"; required = true }
            p("confirm") {
                checkBoxInput(name = "confirm") { id = "confirm"; required = true }
                label { htmlFor = "confirm"; +copy.deleteConfirmLabel }
            }
            submitInput(classes = "danger") { value = copy.deleteButton }
        }

        // The way in for an account that has no password to type.
        h2 { +copy.deleteOtherWaysHeading }
        p { +copy.deleteOtherWaysBody }

        if (googleClientId != null) {
            // The token this produces is checked by the same verifier the API
            // uses, against the same client id, so the button proves exactly
            // what signing in proves.
            form(action = "/delete-account", method = FormMethod.post) {
                id = "social-form"
                hiddenInput(name = "credential") { id = "credential" }
                hiddenInput(name = "confirm") { id = "social-confirm" }
            }
            div {
                id = "g_id_onload"
                attributes["data-client_id"] = googleClientId
                attributes["data-callback"] = "onGoogleCredential"
                attributes["data-ux_mode"] = "popup"
            }
            div("g_id_signin") {
                attributes["data-type"] = "standard"
                attributes["data-text"] = "continue_with"
            }
            script { src = "https://accounts.google.com/gsi/client"; async = true }
            script {
                unsafe {
                    // The tick is carried across from the form above, because
                    // it is the same question and asking it twice on one page
                    // reads as a page that did not notice the first answer.
                    // The server checks it again regardless — this only spares
                    // somebody a refusal they could not see coming.
                    +"""
                    function onGoogleCredential(response) {
                      var ticked = document.getElementById('confirm').checked;
                      if (!ticked) { alert(${'"' + copy.deleteTickFirst + '"'}); return; }
                      document.getElementById('credential').value = response.credential;
                      document.getElementById('social-confirm').value = 'on';
                      document.getElementById('social-form').submit();
                    }
                    """
                }
            }
        }

        // Apple, when there is an Apple developer account to enable it with.
        // Drawn and disabled rather than left out: somebody who signed in with
        // Apple should see that the way exists and is not ready, instead of
        // concluding this page is not for them.
        p {
            button(classes = "social disabled") {
                disabled = true
                +copy.deleteAppleButton
            }
        }
        p("hint") { +copy.deleteAppleSoon }

        p("hint") { +copy.deleteResetHint }
    }
}

internal fun BODY.openInTheApp(deepLink: String, label: String, hint: String) {
    p {
        a(href = deepLink) { +label }
    }
    p {
        style = "font-size: 15px; opacity: 0.7"
        +hint
    }
}

/**
 * Plain, legible, and no stylesheet to fetch.
 *
 * Somebody reaches these once, from an email, often on a phone with a poor
 * connection. Nothing here is worth a second request.
 */
internal fun HTML.simplePage(heading: String, content: BODY.() -> Unit) {
    head {
        title { +"$heading — ${AppInfo.NAME}" }
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        style {
            unsafe {
                +"""
                body { font: 17px -apple-system, system-ui, sans-serif; margin: 0 auto;
                       padding: 48px 24px; max-width: 32rem; color: #2b211d;
                       background: #fdf7f4; }
                h1 { font-size: 22px; }
                input[type=submit] { font-size: 17px; padding: 12px 20px; border: 0;
                       border-radius: 10px; background: #8c4a32; color: #fff; }
                input[type=password], input[type=email] { font-size: 17px; padding: 12px;
                       width: 100%; box-sizing: border-box; margin: 8px 0 16px;
                       border: 1px solid #d8c9c1; border-radius: 10px; }
                input[type=submit].danger { background: #9c4238; }
                p.problem { color: #9c4238; font-weight: 600; }
                p.confirm { display: flex; gap: 10px; align-items: flex-start; }
                p.confirm input { margin-top: 3px; }
                label { font-size: 15px; }
                h2 { font-size: 18px; margin-top: 32px; }
                ul { padding-left: 22px; }
                li { margin-bottom: 10px; }
                a { color: #8c4a32; }
                small { color: #6d5a51; }
                p.hint { font-size: 15px; opacity: 0.7; }
                button.social { font-size: 17px; padding: 12px 20px; border-radius: 10px;
                       border: 1px solid #d8c9c1; background: #fff; color: #2b211d; }
                button.social.disabled { opacity: 0.45; }
                h2 { font-size: 18px; margin-top: 32px; }
                @media (prefers-color-scheme: dark) {
                  body { background: #3a2e28; color: #f4ebe5; }
                  input[type=password], input[type=email] { background: #48392f;
                       color: #f4ebe5; border-color: #6f5c52; }
                  p.problem { color: #f0a196; }
                  a { color: #e8b09c; }
                  small { color: #c3ada2; }
                }
                """
            }
        }
    }
    body {
        h1 { +heading }
        content()
    }
}

/**
 * The same pages in the language the browser asks for.
 *
 * The email is written in the language of the account; the page it leads to is
 * written in the language of whatever the person opened it in. Those can
 * differ — a Russian account opened from an English laptop — and the browser is
 * the honest signal for a page, as it is for the rest of the site. Reading the
 * account here would mean resolving the token before the page is allowed to
 * spend it, which is exactly what these pages exist not to do.
 */
private fun pageCopy(russian: Boolean): PageCopy = if (russian) RussianPages else EnglishPages

private class PageCopy(
    val verifyHeading: String,
    val verifyPrompt: String,
    val verifyButton: String,
    val confirmedHeading: String,
    val confirmedBody: String,
    val missingCode: String,
    val deadHeading: String,
    val deadUsed: String,
    val deadAskAgain: String,
    val resetHeading: String,
    val resetLabel: String,
    val resetButton: String,
    val resetRules: String,
    val changedHeading: String,
    val changedBody: String,
    val tooShortHeading: String,
    val tooShortRule: String,
    val tooShortRetry: String,
    val openInApp: String,
    val openInAppHint: String,
    val magicHeading: String,
    val magicBody: String,
    val deleteHeading: String,
    val deleteWhatGoes: String,
    val deleteWhatStays: String,
    val deleteEmailLabel: String,
    val deletePasswordLabel: String,
    val deleteConfirmLabel: String,
    val deleteButton: String,
    val deleteOtherWaysHeading: String,
    val deleteOtherWaysBody: String,
    val deleteTickFirst: String,
    val deleteAppleButton: String,
    val deleteAppleSoon: String,
    val deleteResetHint: String,
    val deleteMismatch: String,
    val deleteUnconfirmed: String,
    val deletedHeading: String,
    val deletedBody: String,
    val deleteTooMany: String,
)

private val EnglishPages = PageCopy(
    verifyHeading = "Confirm your email",
    verifyPrompt = "One tap and this address is confirmed.",
    verifyButton = "Confirm my email",
    confirmedHeading = "Confirmed",
    confirmedBody = "Your address is confirmed. You can go back to ${AppInfo.NAME} and share a post.",
    missingCode = "This link is missing its code. Try the one in the email again.",
    deadHeading = "That link is no longer valid",
    deadUsed = "This link has already been used, or it has expired.",
    deadAskAgain = "Open ${AppInfo.NAME} and ask for a new one.",
    resetHeading = "Choose a new password",
    resetLabel = "New password",
    resetButton = "Set my password",
    resetRules = "Between 8 and 128 characters. Everywhere you are signed in will be signed out.",
    changedHeading = "Password changed",
    changedBody = "Sign in to ${AppInfo.NAME} with your new password.",
    tooShortHeading = "That password is too short",
    tooShortRule = "It needs to be between 8 and 128 characters.",
    tooShortRetry = "Use the link in the email again to try once more.",
    openInApp = "Open in the ${AppInfo.NAME} app",
    openInAppHint = "If nothing happens, the app is not installed on this device — " +
        "the page above does the same thing.",
    magicHeading = "Sign in to ${AppInfo.NAME}",
    magicBody = "This link signs you in on the device that has the ${AppInfo.NAME} app. Open it there; it works once, for fifteen minutes.",
    deleteHeading = "Delete your account",
    deleteWhatGoes = "Deleting removes your account and everything in it: the posts you " +
        "wrote, the tags on them, the marks other people left on them, and the posts " +
        "you marked yourself. It cannot be undone, and there is no way to get any of it back.",
    deleteWhatStays = "Groups you started stay, because other people are in them and " +
        "their posts are in them. Your name stops being attached to them.",
    deleteEmailLabel = "Your email",
    deletePasswordLabel = "Your password",
    deleteConfirmLabel = "I understand this cannot be undone",
    deleteButton = "Delete my account",
    deleteOtherWaysHeading = "Signed in with Google or Apple?",
    deleteOtherWaysBody = "Then you have no password to type here. Tick the box above, " +
        "then use the button below.",
    deleteTickFirst = "Please tick the box above first.",
    deleteAppleButton = "Continue with Apple",
    deleteAppleSoon = "Apple sign-in is not available on this page yet.",
    deleteResetHint = "Having trouble either way? Use \u201cReset your password\u201d first. " +
        "It works by email, even for an account that has only ever signed in " +
        "with Google or Apple, and then the form above will accept you.",
    deleteMismatch = "Those details did not match an account.",
    deleteUnconfirmed = "Tick the box if you want the account deleted.",
    deletedHeading = "Your account is deleted",
    deletedBody = "It is gone, along with everything in it. Nothing further is needed, " +
        "and you can close this page.",
    deleteTooMany = "Too many attempts. Wait a few minutes and try again.",
)

private val RussianPages = PageCopy(
    verifyHeading = "Подтвердите почту",
    verifyPrompt = "Одно нажатие — и адрес подтверждён.",
    verifyButton = "Подтвердить почту",
    confirmedHeading = "Готово",
    confirmedBody = "Адрес подтверждён. Можно вернуться в ${AppInfo.NAME} и написать пост.",
    missingCode = "В этой ссылке не хватает кода. Попробуйте ссылку из письма ещё раз.",
    deadHeading = "Ссылка больше не действует",
    deadUsed = "Эта ссылка уже использована или истёк её срок.",
    deadAskAgain = "Откройте ${AppInfo.NAME} и запросите новую.",
    resetHeading = "Новый пароль",
    resetLabel = "Новый пароль",
    resetButton = "Сохранить пароль",
    resetRules = "От 8 до 128 символов. На всех устройствах, где вы вошли, сессии будут завершены.",
    changedHeading = "Пароль изменён",
    changedBody = "Войдите в ${AppInfo.NAME} с новым паролем.",
    tooShortHeading = "Пароль слишком короткий",
    tooShortRule = "Нужно от 8 до 128 символов.",
    tooShortRetry = "Откройте ссылку из письма ещё раз и попробуйте снова.",
    openInApp = "Открыть в приложении ${AppInfo.NAME}",
    openInAppHint = "Если ничего не произошло, приложение не установлено на этом устройстве — " +
        "страница выше делает то же самое.",
    magicHeading = "Вход в ${AppInfo.NAME}",
    magicBody = "Эта ссылка выполняет вход на устройстве, где установлено приложение ${AppInfo.NAME}. Откройте её там; она сработает один раз и действует пятнадцать минут.",
    deleteHeading = "Удаление аккаунта",
    deleteWhatGoes = "Удаление уберёт аккаунт и всё, что в нём: написанные вами посты, " +
        "их теги, отметки других людей на них и посты, которые отметили вы. " +
        "Отменить это нельзя, и вернуть что-либо будет невозможно.",
    deleteWhatStays = "Группы, которые вы создали, останутся — в них есть другие люди и " +
        "их посты. Ваше имя перестанет быть с ними связано.",
    deleteEmailLabel = "Ваша эл. почта",
    deletePasswordLabel = "Ваш пароль",
    deleteConfirmLabel = "Я понимаю, что это нельзя отменить",
    deleteButton = "Удалить мой аккаунт",
    deleteOtherWaysHeading = "Входили через Google или Apple?",
    deleteOtherWaysBody = "Тогда пароля для этой страницы у вас нет. Отметьте галочку выше " +
        "и нажмите кнопку ниже.",
    deleteTickFirst = "Сначала отметьте галочку выше.",
    deleteAppleButton = "Продолжить с Apple",
    deleteAppleSoon = "Вход через Apple на этой странице пока недоступен.",
    deleteResetHint = "Ничего не помогает? Сначала воспользуйтесь восстановлением пароля — " +
        "оно работает по почте, даже если вы всегда входили через Google или Apple, " +
        "и после этого форма выше вас примет.",
    deleteMismatch = "Эти данные не подошли ни к одному аккаунту.",
    deleteUnconfirmed = "Отметьте галочку, если хотите удалить аккаунт.",
    deletedHeading = "Аккаунт удалён",
    deletedBody = "Его больше нет вместе со всем, что в нём было. Больше ничего делать " +
        "не нужно, эту страницу можно закрыть.",
    deleteTooMany = "Слишком много попыток. Подождите несколько минут и попробуйте снова.",
)
