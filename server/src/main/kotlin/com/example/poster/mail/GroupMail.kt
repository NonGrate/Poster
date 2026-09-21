package com.example.poster.mail

import com.example.poster.config.AppInfo
import com.example.poster.model.Language
import kotlinx.datetime.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Inviting somebody to a group by email.
 *
 * This endpoint will send mail to an address that has no account — that is the
 * point of it, since an invitation is most useful to somebody not here yet, and
 * answering differently for a registered address would turn the app into a way
 * to ask whether a given person uses this app. That is not a question
 * anybody should be able to put to it.
 *
 * The cost of answering the same either way is that any account can cause mail
 * to be sent to an address of their choosing, over Poster's domain, and the
 * reputation spent is Poster's. Hence the two limits below. They are not
 * belt and braces: they guard different things. The per-address one stops one
 * person being mailed repeatedly; the per-sender one stops one account mailing
 * the world.
 *
 * Held in memory, like [AccountMail]'s, and right for one instance for the same
 * reason. If this ever runs on several, it belongs in the database.
 */
class GroupMail(
    private val mailer: Mailer,
    private val baseUrl: String = System.getenv("POSTER_BASE_URL") ?: AppInfo.WEB_ORIGIN,
    private val clock: Clock = Clock.System,
    private val minimumInterval: Duration = MINIMUM_INTERVAL,
    private val dailyLimitPerSender: Int = DAILY_LIMIT_PER_SENDER,
) {
    private val lastSentTo = mutableMapOf<String, Long>()
    private val sentBy = mutableMapOf<String, MutableList<Long>>()
    private val lock = Any()

    /**
     * True when the invitation went out.
     *
     * False means a limit stopped it, and the route says the same thing either
     * way — the caller learning "too many" tells them their earlier sends
     * landed, which is most of what the limit is hiding.
     */
    suspend fun sendInvitation(
        senderId: String,
        toEmail: String,
        inviterName: String,
        groupName: String,
        code: String,
        recipientHasAccount: Boolean,
        language: String = Language.DEFAULT,
    ): Boolean {
        if (!allowed(senderId, toEmail)) return false
        val copy = copyFor(language)
        return mailer.send(
            to = toEmail,
            subject = copy.inviteSubject(groupName),
            body = copy.inviteBody(
                inviterName,
                groupName,
                code,
                "$baseUrl/join/$code",
                recipientHasAccount,
            ),
        )
    }

    private fun allowed(senderId: String, email: String): Boolean = synchronized(lock) {
        val now = clock.now().epochSeconds
        val key = email.lowercase()

        val last = lastSentTo[key]
        if (last != null && now - last < minimumInterval.inWholeSeconds) return false

        val recent = sentBy.getOrPut(senderId) { mutableListOf() }
        recent.removeAll { now - it > A_DAY }
        if (recent.size >= dailyLimitPerSender) return false

        lastSentTo[key] = now
        recent += now
        true
    }

    companion object {
        /** Long enough that a button pressed twice sends once. */
        val MINIMUM_INTERVAL: Duration = 2.minutes

        /**
         * How many invitations one account may send in a day.
         *
         * Generous for somebody starting a club and small enough that an
         * account used as a mailer is stopped before it costs the domain
         * anything. A family-sized room needs a handful; a bigger group is
         * built over more than one afternoon.
         */
        const val DAILY_LIMIT_PER_SENDER = 20

        private const val A_DAY = 24L * 60 * 60
    }
}

private fun copyFor(language: String): InviteCopy =
    if (language == Language.RUSSIAN) RussianInvite else EnglishInvite

private class InviteCopy(
    val inviteSubject: (group: String) -> String,
    val inviteBody: (
        inviter: String,
        group: String,
        code: String,
        link: String,
        hasAccount: Boolean,
    ) -> String,
)

private val EnglishInvite = InviteCopy(
    inviteSubject = { group -> "$inviterlessSubject $group on ${AppInfo.NAME}" },
    inviteBody = { inviter, group, code, link, hasAccount ->
        val ending = if (hasAccount) {
            """
                Open this and it will take you to the app:

                $link

                Or enter this code under Settings → Manage groups:

                $code
            """.trimIndent()
        } else {
            """
                ${AppInfo.NAME} is a quiet place for a small group of people to
                share posts with each other. Somebody writes what is on their
                mind; the people who care about them see it and can say they
                liked it. It is free, and nothing in it is behind a paywall.

                Open this to get the app and join:

                $link

                Your invitation code is $code. It works once, and only for this
                address.
            """.trimIndent()
        }
        """
            $inviter has invited you to $group on ${AppInfo.NAME}.

            $ending

            If this means nothing to you, you can ignore it — nothing has been
            created in your name and nobody has been told you received this.
        """.trimIndent()
    },
)

private val RussianInvite = InviteCopy(
    inviteSubject = { group -> "Приглашение в «$group» в ${AppInfo.NAME}" },
    inviteBody = { inviter, group, code, link, hasAccount ->
        val ending = if (hasAccount) {
            """
                Откройте эту ссылку — она приведёт вас в приложение:

                $link

                Или введите код в разделе «Настройки» → «Мои группы»:

                $code
            """.trimIndent()
        } else {
            """
                ${AppInfo.NAME} — это спокойное место, где небольшая группа
                людей делится постами друг с другом. Человек пишет о том, что
                у него на уме, а те, кому он дорог, видят это и могут отметить,
                что им понравилось. Приложение бесплатное, и ничего в нём не
                закрыто платной подпиской.

                Откройте эту ссылку, чтобы установить приложение и
                присоединиться:

                $link

                Ваш код приглашения — $code. Он сработает один раз и только для
                этого адреса.
            """.trimIndent()
        }
        """
            $inviter приглашает вас в «$group» в ${AppInfo.NAME}.

            $ending

            Если это письмо вам ни о чём не говорит, его можно просто удалить —
            на ваше имя ничего не создано и никто не узнает, что вы его
            получили.
        """.trimIndent()
    },
)

/** The subject says the room, not the person: a name in a subject line reads as spam. */
private const val inviterlessSubject = "You have been invited to"
