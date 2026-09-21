package com.example.poster

import com.example.poster.config.AppInfo
import com.example.poster.config.Features
import io.ktor.server.html.respondHtml
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.head
import kotlinx.html.li
import kotlinx.html.meta
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.style
import kotlinx.html.title
import kotlinx.html.ul
import kotlinx.html.unsafe

/**
 * Where somebody writes to about their data.
 *
 * From the environment, because a policy naming an address nobody reads is
 * worse than no policy: the whole point of the page is that a request can
 * actually reach a person.
 */
val CONTACT_EMAIL: String
    get() = System.getenv("POSTER_CONTACT_EMAIL")?.takeIf { it.isNotBlank() }
        ?: "privacy@${AppInfo.WEB_ORIGIN.substringAfter("://")}"

/**
 * What this app stores, who else sees it, and how to have it deleted.
 *
 * Required by Google Play, but that is not why it reads the way it does. It is
 * written from the schema rather than from a template: everything listed here
 * is a column that exists, and nothing that would be conventional to claim —
 * analytics, advertising identifiers, third-party trackers — is claimed,
 * because none of it is there.
 *
 * Posts are the reason to take it seriously. People write about their lives in
 * an app like this, and a page that was vague about who can read that would be
 * dishonest about the only thing worth being careful with.
 */
fun Route.privacyPage() {
    get("/privacy") {
        val russian = call.prefersRussian()
        call.respondHtml { privacy(russian) }
    }
}

private fun HTML.privacy(russian: Boolean) {
    val copy = if (russian) PrivacyRussian else PrivacyEnglish
    head {
        title { +copy.title }
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        siteVerification()
        style { unsafe { +SITE_STYLE } }
        script { unsafe { +THEME_SCRIPT } }
    }
    body {
        siteChrome(russian, "/privacy")
        h1 { +copy.heading }
        p("quiet") { +copy.updated }
        p("tagline") { +copy.summary }

        copy.sections.forEach { section ->
            h2 { +section.heading }
            section.paragraphs.forEach { p { +it } }
            if (section.points.isNotEmpty()) {
                ul { section.points.forEach { li { +it } } }
            }
        }

        h2 { +copy.contactHeading }
        p {
            +copy.contactBody
            +" "
            a(href = "mailto:$CONTACT_EMAIL") { +CONTACT_EMAIL }
        }
        p("quiet") { a(href = if (russian) "/?lang=ru" else "/?lang=en") { +copy.back } }
    }
}

private class Section(
    val heading: String,
    val paragraphs: List<String> = emptyList(),
    val points: List<String> = emptyList(),
)

private class PrivacyCopy(
    val title: String,
    val heading: String,
    val updated: String,
    val summary: String,
    val sections: List<Section>,
    val contactHeading: String,
    val contactBody: String,
    val back: String,
)

// Operator: bump this when the policy changes.
private const val UPDATED = "1 January 2026"

private val PrivacyEnglish = PrivacyCopy(
    title = "Privacy — ${AppInfo.NAME}",
    heading = "Privacy",
    updated = "Last updated $UPDATED",
    summary = "${AppInfo.NAME} keeps what it needs to work and nothing else. There is no advertising, " +
        "no analytics, no tracking, and nothing is sold or shared for marketing.",
    sections = listOf(
        Section(
            heading = "What is stored",
            paragraphs = listOf("When you use ${AppInfo.NAME}, the server keeps:"),
            points = listOf(
                "Your account: name, surname, email address, the languages you read, and a hashed password. " +
                    "The password itself is never stored and cannot be recovered from the hash.",
                "The posts you write: their text, when you wrote them, the tags you chose, who they are " +
                    "visible to, and — if you mark one resolved — what you said about how it went.",
                "Which posts you have liked, and which groups you belong to.",
                "Sign-in sessions, so you stay signed in. These are stored hashed and can be revoked.",
            ),
        ),
        Section(
            heading = "Who can see your posts",
            paragraphs = listOf(
                "You choose this for every post. A private post is visible only to you. A group " +
                    "post is visible to people in that group. A public post is visible to anybody " +
                    "using the app.",
                "You can delete a post at any time, and it stops being visible to everybody.",
            ),
        ),
        Section(
            heading = "What is never collected",
            points = listOf(
                "No advertising identifiers, and no advertising.",
                "No analytics or usage tracking.",
                if (Features.IMAGES) {
                    "No location, contacts, microphone or camera access. A photo you attach to a post is " +
                        "stored with the post, shown wherever the post is shown, and deleted with it; " +
                        "your photo library is read only for the picture you pick."
                } else {
                    "No location, contacts, photos, microphone or camera access."
                },
                "Nothing is sold, rented, or shared with anybody for marketing.",
            ),
        ),
        Section(
            heading = "Other services involved",
            paragraphs = listOf("A few things cannot be done alone:"),
            points = listOf(
                "Email — confirmation and password-reset messages are delivered by an email " +
                    "provider (Resend, unless the operator changed it), which receives your email " +
                    "address in order to send them.",
                "Signing in with Google — if you choose it, Google tells this app your email address, " +
                    "name and a Google account identifier. Signing in with an email and password " +
                    "involves Google not at all.",
                "Supporting the app — if you buy the developer a coffee, the purchase is handled by " +
                    "Google Play or the App Store and recorded by RevenueCat. None of them are told " +
                    "what you post about, and this app never sees your card details.",
                "Hosting — the server runs on infrastructure rented by whoever operates this app. " +
                    "(Operator: say where, e.g. \"on rented hardware in the EU\".)",
            ),
        ),
        Section(
            heading = "Crash reports",
            paragraphs = listOf(
                "When the app crashes it sends what went wrong: the error, where in the code it " +
                    "happened, and the phone's model and system version. Crash reports are not linked " +
                    "to your account and contain no posts. Only the most recent five hundred are kept.",
            ),
        ),
        Section(
            heading = "Keeping and deleting",
            paragraphs = listOf(
                "Your account and posts are kept until you delete them. Settings → Delete my account " +
                    "removes everything at once, and the deletion page on this site does the same " +
                    "without the app. Or write to the address below and it will be done for you.",
                "You can also ask what is stored about you, and it will be sent to you.",
            ),
        ),
        Section(
            heading = "Children",
            paragraphs = listOf(
                "${AppInfo.NAME} is not aimed at children, and no part of it is designed for them.",
            ),
        ),
        Section(
            heading = "Changes",
            paragraphs = listOf(
                "If this page changes in a way that matters, the date at the top changes with it.",
            ),
        ),
    ),
    contactHeading = "Getting in touch",
    contactBody = "For anything on this page — deletion, a copy of your data, or a question —",
    back = "← Back to ${AppInfo.NAME}",
)

private val PrivacyRussian = PrivacyCopy(
    title = "Конфиденциальность — ${AppInfo.NAME}",
    heading = "Конфиденциальность",
    updated = "Обновлено 1 января 2026",
    summary = "${AppInfo.NAME} хранит только то, что нужно для работы. Никакой рекламы, аналитики и " +
        "слежения; ничего не продаётся и не передаётся для маркетинга.",
    sections = listOf(
        Section(
            heading = "Что хранится",
            paragraphs = listOf("Когда вы пользуетесь ${AppInfo.NAME}, сервер хранит:"),
            points = listOf(
                "Ваш аккаунт: имя, фамилию, адрес почты, языки, на которых вы читаете, и хеш пароля. " +
                    "Сам пароль не хранится и не может быть восстановлен из хеша.",
                "Ваши посты: текст, дату, выбранные теги, кому они видны и — если вы отметили " +
                    "пост решённым — то, что вы написали об этом.",
                "Какие посты вам понравились и в каких группах вы состоите.",
                "Сессии входа, чтобы вы оставались в аккаунте. Они хранятся в виде хешей и могут быть отозваны.",
            ),
        ),
        Section(
            heading = "Кто видит ваши посты",
            paragraphs = listOf(
                "Вы выбираете это для каждого поста. Личный пост виден только вам. Пост для " +
                    "группы виден людям из этой группы. Публичный виден всем, кто пользуется приложением.",
                "Пост можно удалить в любой момент, и он перестаёт быть виден всем.",
            ),
        ),
        Section(
            heading = "Что никогда не собирается",
            points = listOf(
                "Никаких рекламных идентификаторов и никакой рекламы.",
                "Никакой аналитики и отслеживания действий.",
                if (Features.IMAGES) {
                    "Ни местоположения, ни контактов, ни микрофона, ни камеры. Фотография, приложенная к " +
                        "посту, хранится вместе с ним, показывается там же, где пост, и удаляется вместе с " +
                        "ним; из галереи читается только выбранный снимок."
                } else {
                    "Ни местоположения, ни контактов, ни фотографий, ни микрофона, ни камеры."
                },
                "Ничего не продаётся и не передаётся кому-либо для маркетинга.",
            ),
        ),
        Section(
            heading = "Другие сервисы",
            paragraphs = listOf("Кое-что нельзя сделать в одиночку:"),
            points = listOf(
                "Почта — письма с подтверждением и восстановлением пароля отправляет почтовый " +
                    "сервис (Resend, если оператор его не сменил), который получает ваш адрес, чтобы их доставить.",
                "Вход через Google — если вы им пользуетесь, Google сообщает приложению ваш адрес " +
                    "почты, имя и идентификатор аккаунта Google. При входе по почте и паролю Google " +
                    "не участвует вовсе.",
                "Поддержка приложения — если вы покупаете разработчику кофе, покупку проводит " +
                    "Google Play или App Store, а RevenueCat её записывает. Никому из них не " +
                    "сообщается, о чём вы пишете, и приложение никогда не видит данные вашей карты.",
                "Хостинг — сервер работает на инфраструктуре, арендованной оператором приложения. " +
                    "(Оператору: укажите где, например «на арендованном оборудовании в ЕС».)",
            ),
        ),
        Section(
            heading = "Отчёты о сбоях",
            paragraphs = listOf(
                "Когда приложение падает, оно отправляет, что именно сломалось: ошибку, место в коде, " +
                    "модель телефона и версию системы. Отчёты не связаны с вашим аккаунтом и не " +
                    "содержат постов. Хранятся только последние пятьсот.",
            ),
        ),
        Section(
            heading = "Хранение и удаление",
            paragraphs = listOf(
                "Аккаунт и посты хранятся, пока вы их не удалите. «Настройки» → «Удалить аккаунт» " +
                    "убирает всё сразу, страница удаления на этом сайте делает то же без приложения. " +
                    "Или напишите на адрес ниже, и это сделают за вас.",
                "Вы также можете запросить, что о вас хранится, и это будет вам выслано.",
            ),
        ),
        Section(
            heading = "Дети",
            paragraphs = listOf(
                "${AppInfo.NAME} не предназначено для детей, и ничего в нём не рассчитано на них.",
            ),
        ),
        Section(
            heading = "Изменения",
            paragraphs = listOf(
                "Если эта страница изменится существенно, дата наверху изменится вместе с ней.",
            ),
        ),
    ),
    contactHeading = "Связаться",
    contactBody = "По всему, что написано на этой странице — удаление, копия ваших данных, вопрос —",
    back = "← Вернуться на ${AppInfo.NAME}",
)
