package com.example.poster

import com.example.poster.config.AppInfo
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
 * The agreement between somebody using this app and whoever runs it.
 *
 * Written short and in the same voice as the rest of the site, because terms
 * nobody reads protect nobody. Everything here is either something the software
 * actually does — deletion really deletes, a private post really is private —
 * or a plain statement of what is not promised. Nothing is claimed that the
 * code does not do.
 *
 * It deliberately says little about liability and jurisdiction. This is a free
 * app run by one person for a small group; a page of boilerplate disclaiming
 * everything would be longer than the app's own description and no more
 * enforceable. If it ever takes money for something, this page needs a lawyer
 * rather than another paragraph.
 */
fun Route.termsPage() {
    get("/terms") {
        val russian = call.prefersRussian()
        call.respondHtml { terms(russian) }
    }
}

private fun HTML.terms(russian: Boolean) {
    val copy = if (russian) TermsRussian else TermsEnglish
    head {
        title { +copy.title }
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        siteVerification()
        style { unsafe { +SITE_STYLE } }
        script { unsafe { +THEME_SCRIPT } }
    }
    body {
        siteChrome(russian, "/terms")
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

private class TermsCopy(
    val title: String,
    val heading: String,
    val updated: String,
    val summary: String,
    val sections: List<TermsSection>,
    val contactHeading: String,
    val contactBody: String,
    val back: String,
)

private class TermsSection(
    val heading: String,
    val paragraphs: List<String> = emptyList(),
    val points: List<String> = emptyList(),
)

private val TermsEnglish = TermsCopy(
    title = "Terms of service — ${AppInfo.NAME}",
    heading = "Terms of service",
    updated = "Last updated 1 January 2026.",
    summary = "${AppInfo.NAME} is a small, free app for sharing posts with everyone, or with the " +
        "groups you belong to. Using it means agreeing to what is below. It is short on purpose.",
    sections = listOf(
        TermsSection(
            heading = "Who may use it",
            paragraphs = listOf(
                "You need to be at least 13. We do not verify age, so this is something we " +
                    "ask of you rather than something we check.",
                "One person, one account. Keep your password to yourself — anybody who has it " +
                    "can read the posts your groups can read.",
            ),
        ),
        TermsSection(
            heading = "What you write",
            paragraphs = listOf(
                "What you write stays yours. You give us only what is needed to run the app: " +
                    "storing your posts and showing them to the people you chose. We do not " +
                    "sell them, train on them, or hand them to anybody for marketing.",
                "You choose who sees each post — everyone, one group, or nobody but you. " +
                    "Once other people can see something, they can read it and remember it. " +
                    "Deleting removes it from the app; it cannot reach into anybody's memory.",
            ),
        ),
        TermsSection(
            heading = "What is not allowed",
            paragraphs = listOf("An account used for any of the following is removed:"),
            points = listOf(
                "Anything that sexualises a child, or any attempt to reach one. See our child " +
                    "safety standards.",
                "Threats, harassment, or targeting somebody because of who they are.",
                "Posting somebody else's private details, or writing about a named person in a " +
                    "way meant to harm them.",
                "Spam, advertising, or using the app to sell something.",
                "Trying to break, overload, or get into parts of the service that are not yours.",
            ),
        ),
        TermsSection(
            heading = "Reporting and moderation",
            paragraphs = listOf(
                "Every post has a Report control. Reports are read by a person rather than " +
                    "acted on automatically: in a small group the usual reason a post " +
                    "looks wrong is that somebody misread it, and one button press must not be " +
                    "able to silence anybody.",
                "Where something does break these terms, it is hidden and the account " +
                    "responsible may be banned. We do not promise to review everything, or to " +
                    "review anything within a particular time.",
            ),
        ),
        TermsSection(
            heading = "Leaving",
            paragraphs = listOf(
                "You can delete your account at any time, from Settings in the app or from " +
                    "the deletion page on this site. It removes your account, your posts, " +
                    "the marks other people left on them, and the marks you left on other " +
                    "people's. It cannot be undone.",
                "Groups you started stay, because other people are in them and their " +
                    "posts are in them. Your name stops being attached to them.",
            ),
        ),
        TermsSection(
            heading = "What we do not promise",
            paragraphs = listOf(
                "The app is free and provided as it is. We do not promise it will always be " +
                    "available, that nothing will ever be lost, or that it is fit for any " +
                    "particular purpose. Keep anything you would be upset to lose somewhere " +
                    "else as well.",
                "It is not a substitute for medical, legal, or professional help of any kind. " +
                    "If somebody is in danger, contact the emergency services where you are.",
                "We may change these terms. If a change matters, it will be said in the app " +
                    "rather than only here. Continuing to use ${AppInfo.NAME} after that means " +
                    "accepting the new version.",
                "We may suspend or end an account that breaks these terms, and may stop " +
                    "running the service. If the service is ending, we will say so with enough " +
                    "notice for people to get their posts out.",
            ),
        ),
    ),
    contactHeading = "Contact",
    contactBody = "Questions about any of this go to",
    back = "Back to the front page",
)

private val TermsRussian = TermsCopy(
    title = "Условия использования — ${AppInfo.NAME}",
    heading = "Условия использования",
    updated = "Обновлено 1 января 2026 года.",
    summary = "${AppInfo.NAME} — небольшое бесплатное приложение, чтобы делиться постами со всеми " +
        "или с группами, в которых вы состоите. Пользуясь им, вы соглашаетесь с тем, что " +
        "написано ниже. Написано коротко намеренно.",
    sections = listOf(
        TermsSection(
            heading = "Кто может пользоваться",
            paragraphs = listOf(
                "Вам должно быть не меньше 13 лет. Мы не проверяем возраст — это просьба к " +
                    "вам, а не проверка с нашей стороны.",
                "Один человек — один аккаунт. Держите пароль при себе: тот, у кого он есть, " +
                    "прочтёт всё, что доступно вашим группам.",
            ),
        ),
        TermsSection(
            heading = "То, что вы пишете",
            paragraphs = listOf(
                "Написанное остаётся вашим. Вы даёте нам ровно столько, сколько нужно, чтобы " +
                    "приложение работало: хранить ваши посты и показывать их тем, кого вы " +
                    "выбрали. Мы не продаём их, не обучаем на них модели и не передаём никому " +
                    "для рекламы.",
                "Вы выбираете, кто видит каждый пост: все, одна группа или только вы. Как " +
                    "только другие люди могут её увидеть — они могут её прочесть и запомнить. " +
                    "Удаление убирает её из приложения; из чужой памяти оно ничего не уберёт.",
            ),
        ),
        TermsSection(
            heading = "Что запрещено",
            paragraphs = listOf("Аккаунт, использованный для чего-либо из перечисленного, удаляется:"),
            points = listOf(
                "Всё, что сексуализирует ребёнка, и любые попытки выйти с ребёнком на связь. " +
                    "См. наши стандарты защиты детей.",
                "Угрозы, травля, преследование человека за то, кто он есть.",
                "Публикация чужих личных данных или тексты о конкретном человеке, " +
                    "написанные ему во вред.",
                "Спам, реклама, использование приложения для продаж.",
                "Попытки сломать или перегрузить сервис либо получить доступ к чужому.",
            ),
        ),
        TermsSection(
            heading = "Жалобы и модерация",
            paragraphs = listOf(
                "У каждого поста есть пункт «Пожаловаться». Жалобы читает человек, а не " +
                    "автоматика: в небольшой группе чаще всего пост выглядит неправильно " +
                    "просто потому, что её не так поняли, и одно нажатие кнопки не должно " +
                    "уметь заставить человека замолчать.",
                "Если что-то действительно нарушает эти условия, оно скрывается, а аккаунт " +
                    "может быть заблокирован. Мы не обещаем просматривать всё или " +
                    "просматривать что-либо в определённый срок.",
            ),
        ),
        TermsSection(
            heading = "Уход",
            paragraphs = listOf(
                "Вы можете удалить аккаунт в любой момент — в настройках приложения или на " +
                    "странице удаления на этом сайте. Удаляются аккаунт, ваши посты, отметки " +
                    "других людей на них и ваши отметки на чужих. Отменить это нельзя.",
                "Группы, которые вы создали, остаются: в них есть другие люди и их посты. " +
                    "Ваше имя перестаёт быть с ними связано.",
            ),
        ),
        TermsSection(
            heading = "Чего мы не обещаем",
            paragraphs = listOf(
                "Приложение бесплатное и предоставляется как есть. Мы не обещаем, что оно " +
                    "всегда будет доступно, что ничего никогда не потеряется и что оно " +
                    "подходит для какой-то определённой цели. То, что вам жаль было бы " +
                    "потерять, храните ещё где-нибудь.",
                "Оно не заменяет медицинскую, юридическую или любую другую профессиональную " +
                    "помощь. Если человек в опасности, обратитесь в экстренные службы там, " +
                    "где вы находитесь.",
                "Мы можем менять эти условия. О важных изменениях мы скажем в приложении, а " +
                    "не только здесь. Продолжая пользоваться ${AppInfo.NAME} после этого, вы " +
                    "принимаете новую версию.",
                "Мы можем приостановить или закрыть аккаунт, нарушающий эти условия, и можем " +
                    "прекратить работу сервиса. Если сервис закрывается, мы предупредим " +
                    "заранее, чтобы люди успели забрать свои посты.",
            ),
        ),
    ),
    contactHeading = "Контакт",
    contactBody = "Вопросы по всему этому — на",
    back = "На главную",
)
