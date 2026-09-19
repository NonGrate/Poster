package com.example.poster

import com.example.poster.config.AppInfo
import com.example.poster.config.Features
import io.ktor.server.application.call
import io.ktor.server.html.respondHtml
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.html.*

/**
 * The published standards against child sexual abuse and exploitation.
 *
 * Google Play requires every app in a social category to publish these
 * somewhere public, reachable worldwide, and not a document strangers can edit.
 * A page served by this application satisfies all three, and is the same page
 * in Russian for the people who read the app in Russian.
 *
 * Written from what the service actually is. Two facts do most of the work and
 * both are structural: a post is a title and a message, both text, with no way
 * to attach an image or a video anywhere in the app or the API; and there is no
 * private messaging between people, so the channel grooming usually needs does
 * not exist here. If you add images or messaging to your app, rewrite this page
 * — it stops being true the moment either exists.
 */
fun Route.childSafetyPage(contactEmail: String) {
    get("/child-safety") {
        val russian = call.prefersRussian()
        val copy = if (russian) RussianSafety else EnglishSafety
        call.respondHtml { safetyPage(copy, contactEmail, russian) }
    }
}

private fun HTML.safetyPage(copy: SafetyCopy, contactEmail: String, russian: Boolean) {
    head {
        title { +"${copy.heading} — ${AppInfo.NAME}" }
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        siteVerification()
        style { unsafe { +SITE_STYLE } }
        script { unsafe { +THEME_SCRIPT } }
    }
    body {
        // The site's own chrome rather than the bare page the email links use:
        // this one is linked from the front page and from the Play listing, so
        // it is part of the site and should look like it.
        siteChrome(russian, "/child-safety")
        h1 { +copy.heading }
        p("tagline") { +copy.position }

        h2 { +copy.whatThisIsHeading }
        p { +copy.whatThisIs }
        ul { copy.limits.forEach { li { +it } } }
        p { +copy.age }

        h2 { +copy.prohibitedHeading }
        p { +copy.prohibited }

        h2 { +copy.reportingHeading }
        p { +copy.reportInApp }
        p {
            +copy.reportByEmail
            +" "
            a(href = "mailto:$contactEmail") { +contactEmail }
            +"."
        }

        h2 { +copy.authoritiesHeading }
        p { +copy.authorities }
        ul {
            li {
                a(href = "https://report.cybertip.org/") { +"NCMEC CyberTipline" }
                +" — ${copy.cybertip}"
            }
            li {
                a(href = "https://www.inhope.org/EN/our-members") { +"INHOPE" }
                +" — ${copy.inhope}"
            }
            // Operator: point this at the hotline for the country you operate from
            // (INHOPE lists them); Stopline.cz is the Czech one, kept as an example.
            li {
                a(href = "https://www.stopline.cz/") { +"Stopline.cz" }
                +" — ${copy.stopline}"
            }
        }

        h2 { +copy.responseHeading }
        ul { copy.response.forEach { li { +it } } }

        h2 { +copy.contactHeading }
        p {
            +copy.contact
            +" "
            a(href = "mailto:$contactEmail") { +contactEmail }
            +"."
        }

        p("quiet") { +copy.updated }
        p("quiet") { a(href = if (russian) "/?lang=ru" else "/?lang=en") { +copy.back } }
    }
}

private class SafetyCopy(
    val heading: String,
    val position: String,
    val whatThisIsHeading: String,
    val whatThisIs: String,
    val limits: List<String>,
    val age: String,
    val prohibitedHeading: String,
    val prohibited: String,
    val reportingHeading: String,
    val reportInApp: String,
    val reportByEmail: String,
    val authoritiesHeading: String,
    val authorities: String,
    val cybertip: String,
    val inhope: String,
    val stopline: String,
    val responseHeading: String,
    val response: List<String>,
    val contactHeading: String,
    val contact: String,
    val updated: String,
    val back: String,
)

private val EnglishSafety = SafetyCopy(
    heading = "Child safety standards",
    position = "${AppInfo.NAME} does not tolerate child sexual abuse or exploitation in any form. " +
        "Material or conduct that sexualises a child, or that seeks to make contact with a " +
        "child for that purpose, is forbidden here, is removed when we find it, and is " +
        "reported to the authorities.",
    whatThisIsHeading = "What this service is",
    whatThisIs = "${AppInfo.NAME} is a small app for people who know each other to share short " +
        "posts. A post is a title and a message, and both are text. That shape limits " +
        "what can happen here, and the limits are worth stating plainly:",
    limits = listOf(
        if (Features.IMAGES) {
            "A post may carry one still image and nothing else: no video, no files, no messages. " +
                "An image is visible exactly where its post is, anybody who can see it can report " +
                "it with one tap, and a removed post takes its image with it."
        } else {
            "There is no way to attach or send an image, a video, or any file. The app offers no " +
                "such control and the server accepts no such upload, so imagery cannot pass " +
                "through this service at all."
        },
        (if (Features.COMMENTS) {
            "Comments under a post are text only and visible exactly where the post is. The " +
                "person who wrote a comment and the person who wrote the post can each remove it, " +
                "and a report on the post reaches its comments too. "
        } else "") +
            "There is no private messaging between people. A post is visible to everyone, to a " +
            "group, or to nobody but its author. There is no channel for one person to " +
            "contact another privately.",
        if (Features.AUTHORS) {
            "Every post and comment shows the name its writer gave the account, so what is said " +
                "here is said by somebody, to people who can see who."
        } else {
            "Nobody's name is shown beside a post. Accounts hold a name for their own records " +
                "and the app shows it only where a person chose to be named."
        },
    ),
    age = "${AppInfo.NAME} is not intended for children under 13. We do not currently verify age, " +
        "and we say so rather than implying a check we do not perform.",
    prohibitedHeading = "What is not allowed",
    prohibited = "Child sexual abuse material of any kind. Text that sexualises a child. Using " +
        "a post, a group, or any other part of this service to identify, " +
        "approach, or arrange contact with a child. Encouraging any of the above. An account " +
        "used for these is removed, not warned.",
    reportingHeading = "How to report",
    reportInApp = "Every post in the app carries a Report control in its menu. Anyone signed " +
        "in can use it on anything they can see, with or without a reason, and the report " +
        "reaches a moderator directly. Reporting is not visible to the person reported.",
    reportByEmail = "You do not need an account, or the app, to tell us. Write to",
    authoritiesHeading = "Reporting to authorities",
    authorities = "Where we find or are told of child sexual abuse material, we preserve what " +
        "is needed as evidence, remove it from view, and report it. We cooperate with law " +
        "enforcement requests that are properly made. Anybody may also report directly, and " +
        "does not need to go through us:",
    cybertip = "the international line operated by the US National Center for Missing & " +
        "Exploited Children.",
    inhope = "a network of national hotlines; it will route you to the one for your country.",
    // Operator: replace with the hotline for the country you operate from.
    stopline = "the national hotline for the country this service is operated from.",
    responseHeading = "What happens to a report",
    response = listOf(
        "A moderator reviews it. Reports are recorded rather than acted on automatically: a " +
            "single button press must not be able to silence somebody.",
        "Content that breaks these standards is hidden from everyone.",
        "The account responsible is banned, and its sessions are ended.",
        "Material that appears to be child sexual abuse material is reported to the " +
            "authorities named above.",
    ),
    contactHeading = "Contact",
    contact = "The point of contact for questions about these standards, our prevention " +
        "practices, or a specific report is",
    updated = "Last updated 1 January 2026.",
    back = "Back to the front page",
)

private val RussianSafety = SafetyCopy(
    heading = "Стандарты защиты детей",
    position = "${AppInfo.NAME} не допускает сексуального насилия над детьми и их эксплуатации ни " +
        "в каком виде. Материалы или действия, которые сексуализируют ребёнка или направлены " +
        "на установление с ним контакта с такой целью, здесь запрещены, удаляются при " +
        "обнаружении и передаются в соответствующие органы.",
    whatThisIsHeading = "Что это за сервис",
    whatThisIs = "${AppInfo.NAME} — небольшое приложение для людей, которые знают друг друга, чтобы " +
        "делиться короткими постами. Пост состоит из заголовка и текста, и то и другое — текст. " +
        "Это устройство само по себе ограничивает происходящее, и об ограничениях стоит " +
        "сказать прямо:",
    limits = listOf(
        if (Features.IMAGES) {
            "К посту можно приложить одно изображение и ничего больше: ни видео, ни файлов, ни " +
                "сообщений. Изображение видно ровно там, где виден пост, любой, кто его видит, может " +
                "пожаловаться одним нажатием, а удалённый пост забирает изображение с собой."
        } else {
            "Здесь нельзя приложить или отправить изображение, видео или любой файл. В приложении " +
                "нет такой возможности, а сервер не принимает загрузок — изображения через этот " +
                "сервис пройти не могут вовсе."
        },
        (if (Features.COMMENTS) {
            "Комментарии под постом — только текст, и видны ровно там, где виден пост. Автор " +
                "комментария и автор поста могут его убрать, а жалоба на пост касается и его " +
                "комментариев. "
        } else "") +
            "Здесь нет личных сообщений между людьми. Пост виден всем, группе или никому, " +
            "кроме автора. Канала для приватного обращения одного человека к другому нет.",
        if (Features.AUTHORS) {
            "Рядом с каждым постом и комментарием стоит имя, которое автор указал в аккаунте: " +
                "здесь говорит конкретный человек, и видно кто."
        } else {
            "Имя человека рядом с постом не показывается. Аккаунт хранит имя для своих записей, " +
                "и приложение показывает его только там, где человек сам решил быть названным."
        },
    ),
    age = "${AppInfo.NAME} не предназначен для детей младше 13 лет. Мы не проверяем возраст и " +
        "говорим об этом прямо, вместо того чтобы подразумевать проверку, которой нет.",
    prohibitedHeading = "Что запрещено",
    prohibited = "Любые материалы о сексуальном насилии над детьми. Тексты, сексуализирующие " +
        "ребёнка. Использование поста, группы или любой другой части сервиса " +
        "для поиска ребёнка, обращения к нему или организации встречи. Поощрение всего " +
        "перечисленного. Аккаунт, использованный для этого, удаляется без предупреждения.",
    reportingHeading = "Как сообщить",
    reportInApp = "У каждого поста в приложении есть пункт «Пожаловаться» в меню. Любой " +
        "вошедший в аккаунт может воспользоваться им для всего, что видит, с указанием " +
        "причины или без неё, и жалоба попадает прямо к модератору. Тот, на кого пожаловались, " +
        "об этом не узнаёт.",
    reportByEmail = "Чтобы сообщить нам, аккаунт и приложение не нужны. Напишите на",
    authoritiesHeading = "Передача в компетентные органы",
    authorities = "Обнаружив материалы о сексуальном насилии над детьми или получив сообщение " +
        "о них, мы сохраняем необходимое как доказательство, убираем их из доступа и передаём " +
        "информацию дальше. Мы содействуем правоохранительным органам по надлежаще " +
        "оформленным запросам. Сообщить можно и напрямую, не через нас:",
    cybertip = "международная линия Национального центра США по пропавшим и эксплуатируемым детям.",
    inhope = "сеть национальных горячих линий; она направит вас в линию вашей страны.",
    stopline = "национальная горячая линия страны, из которой работает сервис.",
    responseHeading = "Что происходит с жалобой",
    response = listOf(
        "Её рассматривает модератор. Жалобы фиксируются, а не исполняются автоматически: " +
            "одно нажатие кнопки не должно уметь заставить человека замолчать.",
        "Содержимое, нарушающее эти стандарты, скрывается от всех.",
        "Ответственный аккаунт блокируется, его сессии прекращаются.",
        "Материалы, похожие на материалы о сексуальном насилии над детьми, передаются в " +
            "названные выше органы.",
    ),
    contactHeading = "Контакт",
    contact = "По вопросам об этих стандартах, о наших мерах предотвращения или о конкретной " +
        "жалобе пишите на",
    updated = "Обновлено 1 января 2026 года.",
    back = "На главную",
)
