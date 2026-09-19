package com.example.poster.auth

import com.example.poster.config.AppInfo
import io.ktor.server.html.respondHtml
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import com.example.poster.model.GroupRepository
import com.example.poster.model.UserGroupRepository
import com.example.poster.prefersRussian
import kotlinx.html.p
import kotlinx.html.strong

/**
 * Where an emailed invitation lands.
 *
 * The link in the mail is an https address so that it works from any mail
 * client on any device, including a desktop with no app on it. This page then
 * offers the `poster://` form, which is what actually reaches the app — the
 * same two-step the verification and reset pages use, and for the same reason.
 *
 * Nothing is spent here. Joining needs an account, and an account needs the
 * app; a scanner following the link before its recipient does must not consume
 * the invitation, which is exactly what the verify page learned.
 */
fun Route.groupPages(
    groups: GroupRepository,
    memberships: UserGroupRepository,
) {
    get("/join/{code}") {
        val code = call.parameters["code"].orEmpty().trim().uppercase()
        val russian = call.prefersRussian()
        val copy = if (russian) RussianJoin else EnglishJoin

        // The group's name, when the code is real and still good. Naming it
        // is the point of the page — somebody deciding whether to install an
        // app should know what they are being asked into — and a code is eight
        // characters from a cryptographic source, so this is not a way to read
        // the list of groups.
        val invite = memberships.inviteByCode(code)
        val name = invite
            ?.takeIf { it.live }
            ?.let { groups.groupById(it.groupId)?.name }

        call.respondHtml {
            simplePage(if (name == null) copy.deadHeading else copy.heading(name)) {
                if (name == null) {
                    // One message for spent, withdrawn and invented. Telling
                    // them apart tells somebody holding a code they were not
                    // given which kind they hold.
                    p { +copy.dead }
                } else {
                    p { +copy.body }
                    openInTheApp("${AppInfo.SCHEME}://join/$code", copy.openInApp, copy.openInAppHint)
                    p {
                        +copy.codeIs
                        strong { +code }
                    }
                    p("hint") { +copy.boundHint }
                }
            }
        }
    }
}

private class JoinCopy(
    val heading: (group: String) -> String,
    val body: String,
    val codeIs: String,
    val boundHint: String,
    val deadHeading: String,
    val dead: String,
    val openInApp: String,
    val openInAppHint: String,
)

private val EnglishJoin = JoinCopy(
    heading = { group -> "You have been invited to $group" },
    body = "${AppInfo.NAME} is a quiet place for a small group of people to share posts with " +
        "each other. Open this on the phone the app is on, and it will take you straight in.",
    codeIs = "Your invitation code is ",
    boundHint = "It works once, and only for the address this was sent to. " +
        "Enter it under Settings → Manage groups if the link does not open the app.",
    deadHeading = "This invitation is no longer good",
    dead = "It may have been used already, or withdrawn by whoever sent it. " +
        "Ask them for another one.",
    openInApp = "Open in the ${AppInfo.NAME} app",
    openInAppHint = "If nothing happens, the app is not installed on this device — " +
        "install it, then enter the code below.",
)

private val RussianJoin = JoinCopy(
    heading = { group -> "Вас приглашают в «$group»" },
    body = "${AppInfo.NAME} — спокойное место, где небольшая группа людей делится постами " +
        "друг с другом. Откройте эту страницу на телефоне с приложением, и она " +
        "приведёт вас прямо туда.",
    codeIs = "Ваш код приглашения — ",
    boundHint = "Он сработает один раз и только для адреса, на который пришло письмо. " +
        "Введите его в разделе «Настройки» → «Мои группы», если ссылка не открыла приложение.",
    deadHeading = "Это приглашение больше не действует",
    dead = "Возможно, им уже воспользовались или его отозвал тот, кто прислал. " +
        "Попросите новое.",
    openInApp = "Открыть в приложении ${AppInfo.NAME}",
    openInAppHint = "Если ничего не произошло, приложение не установлено на этом устройстве — " +
        "установите его и введите код ниже.",
)
