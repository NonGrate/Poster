package com.example.poster.auth

import com.example.poster.config.AppInfo
import com.example.poster.mail.Mailer
import com.example.poster.model.Language
import kotlinx.datetime.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** A sign-in link is a session in the making; a day would be a standing invitation. */
private val MAGIC_LINK_LIFETIME: Duration = 15.minutes

/**
 * The messages this app sends, and the guard on how often — see [allowed].
 *
 * Both endpoints behind these take an address from anybody, so without a limit
 * they are a way to send mail to strangers over somebody else's domain — and
 * the reputation spent is Poster's, not the sender's.
 *
 * The limit is per address rather than per caller: an address is the thing
 * being mailed, and the thing worth protecting from being mailed repeatedly.
 * It is held in memory, which is right for one instance and wrong for several
 * — the staging deployment runs one, deliberately, because it has one SQLite
 * file. If that ever changes this belongs in the database.
 */
class AccountMail(
    private val mailer: Mailer,
    private val tokens: AccountTokens,
    private val baseUrl: String = System.getenv("POSTER_BASE_URL") ?: AppInfo.WEB_ORIGIN,
    private val clock: Clock = Clock.System,
    private val minimumInterval: Duration = MINIMUM_INTERVAL,
) {
    private val lastSentTo = mutableMapOf<Pair<String, TokenPurpose>, Long>()
    private val lock = Any()

    /**
     * True when the message was sent. False means asked too soon, and the
     * caller must answer the same either way — see the routes for why.
     */
    suspend fun sendVerification(
        userId: String,
        email: String,
        name: String,
        language: String = Language.DEFAULT,
    ): Boolean {
        if (!allowed(email, TokenPurpose.VERIFY_EMAIL)) return false
        val token = tokens.issue(userId, TokenPurpose.VERIFY_EMAIL)
        val copy = copyFor(language)
        return mailer.send(
            to = email,
            subject = copy.verifySubject,
            body = copy.verifyBody(name, "$baseUrl/verify?token=$token"),
        )
    }

    suspend fun sendPasswordReset(
        userId: String,
        email: String,
        name: String,
        language: String = Language.DEFAULT,
    ): Boolean {
        if (!allowed(email, TokenPurpose.RESET_PASSWORD)) return false
        val token = tokens.issue(userId, TokenPurpose.RESET_PASSWORD)
        val copy = copyFor(language)
        return mailer.send(
            to = email,
            subject = copy.resetSubject,
            body = copy.resetBody(name, "$baseUrl/reset?token=$token"),
        )
    }

    /**
     * Per address *and* purpose.
     *
     * Keyed on the address alone, registering would block the reset that
     * follows it — somebody who mistypes a password and immediately asks for
     * help would be told nothing and left waiting, which is precisely the
     * person this stage exists for. The two kinds of message are independent
     * because they answer independent needs.
     */
    /**
     * A sign-in link (feature.magicLink): short-lived, because it is a session,
     * not a chore to get round to. Spending it also confirms the address.
     */
    suspend fun sendMagicLink(
        userId: String,
        email: String,
        name: String,
        language: String = Language.DEFAULT,
    ): Boolean {
        if (!allowed(email, TokenPurpose.MAGIC_LINK)) return false
        val token = tokens.issue(userId, TokenPurpose.MAGIC_LINK, validFor = MAGIC_LINK_LIFETIME)
        val copy = copyFor(language)
        return mailer.send(
            to = email,
            subject = copy.magicSubject,
            body = copy.magicBody(name, "$baseUrl/magic?token=$token"),
        )
    }

    private fun allowed(email: String, purpose: TokenPurpose): Boolean = synchronized(lock) {
        val key = email.lowercase() to purpose
        val now = clock.now().epochSeconds
        val last = lastSentTo[key]
        if (last != null && now - last < minimumInterval.inWholeSeconds) {
            false
        } else {
            lastSentTo[key] = now
            true
        }
    }

    companion object {
        /**
         * Long enough that a button pressed twice sends once, short enough that
         * somebody who genuinely lost the email is not left waiting.
         */
        val MINIMUM_INTERVAL: Duration = 2.minutes
    }
}

/**
 * The same two messages, in the language the person reads.
 *
 * The app is entirely in Russian for somebody who chose Russian, so an English
 * confirmation email is the first thing that breaks the illusion that this was
 * built for them — and it arrives at the one moment they cannot yet do anything
 * else. Anything other than the two languages this app speaks gets English,
 * which is what [Language.DEFAULT] means everywhere else.
 */
private fun copyFor(language: String): MailCopy =
    if (language == Language.RUSSIAN) RussianMail else EnglishMail

private class MailCopy(
    val verifySubject: String,
    val verifyBody: (name: String, link: String) -> String,
    val resetSubject: String,
    val resetBody: (name: String, link: String) -> String,
    val magicSubject: String,
    val magicBody: (name: String, link: String) -> String,
)

private val EnglishMail = MailCopy(
    verifySubject = "Confirm your email for ${AppInfo.NAME}",
    verifyBody = { name, link ->
        """
            Hello $name,

            Confirm this address to finish setting up ${AppInfo.NAME}:

            $link

            The link works once and expires in a day. If you did not create
            an account, nothing has been made in your name and you can
            ignore this.
        """.trimIndent()
    },
    resetSubject = "Reset your ${AppInfo.NAME} password",
    resetBody = { name, link ->
        """
            Hello $name,

            Somebody asked to reset the password for this address. If it was
            you:

            $link

            The link works once and expires in a day. If it was not you,
            your password has not changed and you need do nothing.
        """.trimIndent()
    },
    magicSubject = "Sign in to ${AppInfo.NAME}",
    magicBody = { name, link ->
        """
            Hello $name,
            Here is your link to sign in to ${AppInfo.NAME}:
            $link
            It works once and expires in fifteen minutes. If you did not ask
            for it, ignore this email; nobody can sign in without it.
        """.trimIndent()
    },
)

private val RussianMail = MailCopy(
    verifySubject = "Подтвердите почту для ${AppInfo.NAME}",
    verifyBody = { name, link ->
        """
            Здравствуйте, $name!

            Подтвердите этот адрес, чтобы закончить настройку ${AppInfo.NAME}:

            $link

            Ссылка сработает один раз и действует сутки. Если вы не создавали
            аккаунт, на ваше имя ничего не создано — это письмо можно просто
            удалить.
        """.trimIndent()
    },
    resetSubject = "Восстановление пароля в ${AppInfo.NAME}",
    resetBody = { name, link ->
        """
            Здравствуйте, $name!

            Кто-то попросил сбросить пароль для этого адреса. Если это были вы:

            $link

            Ссылка сработает один раз и действует сутки. Если это были не вы,
            пароль не изменился и делать ничего не нужно.
        """.trimIndent()
    },
    magicSubject = "Вход в ${AppInfo.NAME}",
    magicBody = { name, link ->
        """
            Здравствуйте, $name!
            Ваша ссылка для входа в ${AppInfo.NAME}:
            $link
            Она сработает один раз и действует пятнадцать минут. Если вы её не
            запрашивали, просто удалите письмо — без ссылки войти нельзя.
        """.trimIndent()
    },
)
